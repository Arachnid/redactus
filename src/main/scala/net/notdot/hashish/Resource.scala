package net.notdot.hashish

import java.security.MessageDigest
import scala.collection.mutable.WrappedArray
import rice.p2p.past._
import rice.p2p.commonapi.Id

object Resource {
	def apply(headers:Map[String,String], body:Array[Byte]):Resource = {
		headers.get("Content-Type") match {
			case Some("text/x-hashish-manifest") => new Manifest(body)
			case _ => new Content(headers, body)
		}
	}
}

abstract class Resource extends PastContent {
	@transient
	var id:Id = null
	
	def getId():Id = {
		if(this.id == null) {
			this.id = calculateId
		}
		this.id
	}
	
	protected def calculateId():Id

	def checkInsert(id:Id, existingContent:PastContent):PastContent = {
		if(id != getId)
			throw new PastException("Resource: can't insert, content hash incorrect")
		if(existingContent != null) {
			existingContent
		} else {
			this
		}
	}
	
	def getHandle(local:Past):PastContentHandle = {
		new ContentHashPastContentHandle(local.getLocalNodeHandle, getId)
	}
		
	def isMutable():Boolean = true
}

class Content(val headers:Map[String,String], val body:Array[Byte]) extends Resource {
	override def calculateId() = {
		val sortedHeaders = headers.toSeq.sortWith((a,b) => a._1 < b._1 || (a._1 == b._1 && a._2 < b._2))
		val headerString = sortedHeaders.map(kv => kv._1 + ":" + kv._2 + "\n").reduceLeft(_ + _)
		val message = (headerString + "\n").getBytes("UTF-8") ++ body
		val md = MessageDigest.getInstance("SHA")
		md.update(message)
		rice.pastry.Id.build(md.digest)
	}	
}

object Manifest {
	protected def parse(body:String) = {
		body.split("\n").map(x=>x.split("""\s+""", 2)).map(x=>(x(1) -> rice.pastry.Id.build(x(0)))).toMap
	}
}

class Manifest(val paths:Map[String,Id]) extends Resource {
	override def calculateId() = {
		val headerString = "Content-Type: text/x-hashish-manifest\n"
		val bodyString = paths.map(x => x._1 + "\t" + x._2.toStringFull).reduceLeft(_ + "\n" + _)
		val message = (headerString + "\n" + bodyString).getBytes("UTF-8")
		val md = MessageDigest.getInstance("SHA")
		md.update(message)
		rice.pastry.Id.build(md.digest)
	}
	
	def this(body:Array[Byte]) = this(Manifest.parse(new String(body, "UTF-8")))
	
	def lookup(path:String) = paths.get(path) match {
		case Some(id) => Some((id, ""))
		case None => None
	}
}
