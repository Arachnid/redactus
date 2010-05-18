package net.notdot.hashish.web

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer
import java.security.MessageDigest
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import org.apache.commons.io.IOUtils
import org.apache.commons.io.HexDump
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.eclipse.jetty.continuation.Continuation
import org.eclipse.jetty.continuation.ContinuationSupport
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler._
import rice.p2p.commonapi.Id
import rice.p2p.past.PastException

import net.notdot.hashish._

object BaseContentHandler {
	val PathHashRE = """/([0-9a-fA-F]{40})(/.*|)""".r
	val HostnameHashRE = """([0-9a-fA-F]{40})\.sha1\..+""".r
}

abstract class BaseContentHandler(val client:AbstractHashishClient) extends AbstractHandler {
	def getContent(continuation:Continuation, response:HttpServletResponse, id:Id, path:String):Unit = {
		client.getByHash(id, (result:Either[Exception,Resource]) => {
			result match {
				case Right(null) => serve404(continuation, response, "Not Found")
				case Right(resource:Content) => serveContent(continuation, response, resource, path)
				case Right(resource:Manifest) => resource.lookup(path) match {
					case Some((newid, newpath)) => getContent(continuation, response, newid, newpath)
					case None => serve404(continuation, response, "Not Found")
				}
				case Left(ex) => serve500(continuation, response, ex)
			}
		})
	}
	
	def serveContent(continuation:Continuation, response:HttpServletResponse,
			resource:Content, path:String) = {
		resource.headers foreach { kv => response.addHeader(kv._1, kv._2) }
		response.getOutputStream().write(resource.body)
		continuation.complete
	}
	
	def serve404(continuation:Continuation, response:HttpServletResponse, reason:String) = {
		response.sendError(404, reason)
		continuation.complete
	}
	
	def serve500(continuation:Continuation, response:HttpServletResponse, ex:Exception) = {
		response.sendError(500, ex.toString)
		printf(ex.toString + "\n")
		printf(ex.getStackTraceString + "\n")
		continuation.complete
	}
}

class PathHashContentHandler(client:AbstractHashishClient) extends BaseContentHandler(client) {
	def handle(target:String, baseRequest:Request, request:HttpServletRequest,
			response:HttpServletResponse) = {
		request.getPathInfo match {
			case BaseContentHandler.PathHashRE(hexId, path) => {
				baseRequest.setHandled(true)
				val continuation = ContinuationSupport.getContinuation(request)
				continuation.suspend
				getContent(continuation, response, rice.pastry.Id.build(hexId), path)
			}
			case _ => Unit
		}
	}
}

class DomainHashContentHandler(client:AbstractHashishClient) extends BaseContentHandler(client) {
	def handle(target:String, baseRequest:Request, request:HttpServletRequest,
			response:HttpServletResponse) = {
		request.getServerName match {
			case BaseContentHandler.HostnameHashRE(hexId) => {
				baseRequest.setHandled(true)
				val continuation = ContinuationSupport.getContinuation(request)
				continuation.suspend
				getContent(continuation, response, rice.pastry.Id.build(hexId), request.getPathInfo)
			}
			case _ => Unit
		}
	}
}

class UploadHandler(client:AbstractHashishClient) extends AbstractHandler {
	def createResource(headers:Map[String,String], body:Array[Byte]):Resource = {
		headers.get("Content-Type") match {
			case Some("text/x-hashish-manifest") => new Manifest(body)
			case _ => new Content(headers, body)
		}
	}

	def extractBody(request:HttpServletRequest) = {
		val headers = (for {
			nameObj <- request.getHeaderNames()
			name = nameObj.asInstanceOf[String]
			value = request.getHeader(name)
		} yield name -> value).toMap[String,String]
		val body = IOUtils.toByteArray(request.getInputStream())
		Array(createResource(headers, body))
	}
	
	def extractMultipart(request:HttpServletRequest) = {
		val upload = new ServletFileUpload
		val iter = upload.getItemIterator(request)
		val ret = new ArrayBuffer[Resource]()
		while(iter.hasNext) {
			val item = iter.next
			val name = item.getFieldName
			if(!item.isFormField) {
				/*val headerObj = item.getHeaders()
				val headers = (for {
					nameObj <- headerObj.getHeaderNames
					name = nameObj.asInstanceOf[String]
					value = headerObj.getHeader(name)
				} yield name -> value).toMap[String,String]*/
				val headers = Map("Content-Type" -> item.getContentType)
				val body = IOUtils.toByteArray(item.openStream())
				ret += createResource(headers, body)
			}
		}
		ret.toArray[Resource]
	}
	
	def handle(target:String, baseRequest:Request, request:HttpServletRequest,
			response:HttpServletResponse) = {
		baseRequest.setHandled(true)
		
		val resources = if(ServletFileUpload.isMultipartContent(request))
			extractMultipart(request)
		else
			extractBody(request)

		val continuation = ContinuationSupport.getContinuation(request)
		continuation.suspend
		client.insertMany(resources, finishInsert(continuation, request, response, resources))
	}
	
	def finishInsert(continuation:Continuation, request:HttpServletRequest, response:HttpServletResponse, resources:Array[Resource])
			(results:Array[Either[Exception,Int]]) = {
		val statuses = for(result <- resources.zip(results)) yield result match {
			case (resource, Right(0)) => "{'status': 'failure', 'replicas': 0, 'id': '" + resource.getId.toStringFull + "'}"
			case (resource, Right(successes)) => "{'status': 'success', 'replicas': " + successes + ", 'id': '" + resource.getId.toStringFull + "'}"
			case (resource, Left(ex)) => "{'status': 'failure', 'replicas': 0, 'id': '" + resource.getId.toStringFull + "', 'error': '" + ex.toString + "'}"
		}
		response.setStatus(201)
		response.setHeader("Content-Type", "text/plain")
		response.getWriter().print("[" + statuses.reduceLeft(_ + ", " + _) + "]")
		continuation.complete
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
