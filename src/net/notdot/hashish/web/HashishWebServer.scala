package net.notdot.hashish.web

import scala.collection.JavaConversions._
import java.security.MessageDigest
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import org.apache.commons.io.IOUtils
import org.apache.commons.io.HexDump
import org.eclipse.jetty.continuation.ContinuationSupport
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler._
import rice.pastry.Id
import rice.p2p.past.PastException

import net.notdot.hashish._

object BaseContentHandler {
	val PathHashRE = """/([0-9a-fA-F]{40})""".r
	val HostnameHashRE = """([0-9a-fA-F]{40})\.sha1\..+""".r
}

abstract class BaseContentHandler(val client:AbstractHashishClient) extends AbstractHandler {
	def getContent(request:HttpServletRequest, response:HttpServletResponse, hexId:String) = {
		val id = Id.build(hexId)
		val continuation = ContinuationSupport.getContinuation(request)
		continuation.suspend
		client.getByHash(id, (result:Either[Exception,Resource]) => {
			result match {
				case Right(null) => get404(request, response, "Not Found")
				case Right(resource) => writeResponse(request, response, resource)
				case Left(ex) => get500(request, response, ex)
			}
			continuation.complete
		})
	}
	
	def writeResponse(request:HttpServletRequest, response:HttpServletResponse,
			resource:Resource) = {
		resource.headers foreach { kv => response.addHeader(kv._1, kv._2) }
		response.getOutputStream().write(resource.body)
	}
	
	def get404(request:HttpServletRequest, response:HttpServletResponse, reason:String) = {
		response.sendError(404, reason)
	}
	
	def get500(request:HttpServletRequest, response:HttpServletResponse, ex:Exception) = {
		response.sendError(500, ex.toString)
		printf(ex.toString + "\n")
		printf(ex.getStackTraceString + "\n")
	}
}

class PathHashContentHandler(client:AbstractHashishClient) extends BaseContentHandler(client) {
	def handle(target:String, baseRequest:Request, request:HttpServletRequest,
			response:HttpServletResponse) = {
		baseRequest.setHandled(true)
		request.getPathInfo match {
			case BaseContentHandler.PathHashRE(hexId) => getContent(request, response, hexId)
			case _ => baseRequest.setHandled(false)
		}
	}
}

class DomainHashContentHandler(client:AbstractHashishClient) extends BaseContentHandler(client) {
	def handle(target:String, baseRequest:Request, request:HttpServletRequest,
			response:HttpServletResponse) = {
		baseRequest.setHandled(true)
		request.getServerName match {
			case BaseContentHandler.HostnameHashRE(hexId) => getContent(request, response, hexId)
			case _ => baseRequest.setHandled(false)
		}
	}
}

class UploadHandler(client:AbstractHashishClient) extends AbstractHandler {
	def handle(target:String, baseRequest:Request, request:HttpServletRequest,
			response:HttpServletResponse) = {
		baseRequest.setHandled(true)
		
		val headers = (for {
			nameObj <- request.getHeaderNames()
			name = nameObj.asInstanceOf[String]
			value = request.getHeader(name)
		} yield name -> value).toMap[String,String]
		val body = IOUtils.toByteArray(request.getInputStream())
		val resource = new Resource(headers, body)

		val continuation = ContinuationSupport.getContinuation(request)
		continuation.suspend
		client.insert(resource, (result:Either[Exception,Int]) => {
			result match {
				case Right(0) => writeFailureResponse(request, response)
				case Right(successes) => writeSuccessResponse(request, response, resource, successes)
				case Left(ex) => write500(request, response, ex)
			}
			continuation.complete
		})
	}
	
	def writeSuccessResponse(request:HttpServletRequest, response:HttpServletResponse, resource:Resource, successes:Int) = {
		response.setStatus(201)
		response.setHeader("Content-Type", "text/html")
		response.getWriter().print("Resource with ID " + resource.getId().toStringFull() + " written to " + successes + " replicas")
	}
	
	def writeFailureResponse(request:HttpServletRequest, response:HttpServletResponse) = {
		response.setStatus(500)
		response.setHeader("Content-Type", "text/html")
		response.getWriter().print("Failed to write resource to any replicas.")
	}
	
	def write500(request:HttpServletRequest, response:HttpServletResponse, ex:Exception) = {
		response.sendError(500, ex.toString)
		printf(ex.toString + "\n")
		printf(ex.getStackTraceString + "\n")
	}
}

class HashishWebServer(val port:Int, val client:AbstractHashishClient) {
	val server = new Server(port)
	
	def start() {
		val uploadContext = new ContextHandler
		uploadContext.setContextPath("/_upload")
		uploadContext.setHandler(new UploadHandler(client))

		val pathHashContext = new ContextHandler
		pathHashContext.setContextPath("/_sha1")
		pathHashContext.setHandler(new PathHashContentHandler(client))

		val domainHashContext = new ContextHandler
		domainHashContext.setHandler(new DomainHashContentHandler(client))

		val contexts = new ContextHandlerCollection
		contexts.setHandlers(Array(uploadContext, pathHashContext, domainHashContext))
		
		server.setHandler(contexts)
		server.start()
		server.join()
	}
}
