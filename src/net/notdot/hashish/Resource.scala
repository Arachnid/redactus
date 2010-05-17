package net.notdot.hashish

import java.security.MessageDigest
import scala.collection.mutable.WrappedArray
import rice.p2p.past._
import rice.p2p.commonapi.Id

object Resource {
	def calculateId(headers:Map[String, String], body:Array[Byte]) = {
		val sortedHeaders = headers.toSeq.sortWith((a,b) => a._1 < b._1 || (a._1 == b._1 && a._2 < b._2))
		val headerString = sortedHeaders.map(kv => kv._1 + ":" + kv._2 + "\n").reduceLeft(_ + _)
		val message = (headerString + "\n").getBytes("UTF-8") ++ body
		val md = MessageDigest.getInstance("SHA")
		md.update(message)
		rice.pastry.Id.build(md.digest)
	}
}

class Resource(val headers:Map[String,String], val body:Array[Byte]) extends PastContent {
	@transient
	var id:Id = null
	
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
	
	def getId():Id = {
		if(this.id == null) {
			this.id = Resource.calculateId(headers, body)
		}
		this.id
	}
	
	def isMutable():Boolean = true
}
