package net.notdot.hashish

import java.io.IOException
import scala.collection.JavaConversions
import rice.Continuation
import rice.pastry._
import rice.p2p.commonapi.Id
import rice.p2p.past._
import rice.environment.Environment
import rice.pastry.commonapi.PastryIdFactory
import rice.persistence._
import net.notdot.pastryutil._

abstract class AbstractHashishClient(val env:Environment,
		val nodeFactory:PastryNodeFactory, val properties:TypedProperties) {
	val node = nodeFactory.newNode
	val idFactory = new PastryIdFactory(env)
	val store:Storage = new PersistentStorage(idFactory,
			properties.getString("storage_dir"),
			properties.getInt("storage_size"), env)
	val past:Past = new PastImpl(
			node, new StorageManagerImpl(
					idFactory, store, new LRUCache(
							new MemoryStorage(idFactory), 
							properties.getInt("cache_size"), env)),
			properties.getInt("num_replicas"),
			properties.getString("past_instance_name"))
	
	def boot[T<:java.lang.Object](bootaddresses: Iterable[T]) = {
		node.boot(JavaConversions.asCollection[Object](bootaddresses))
		node.synchronized {
			while(!node.isReady && !node.joinFailed) {
				node.wait(500);
			}
			if(node.joinFailed)
				new Some(node.joinFailedReason)
			else
				None
		}
	}
	
	def getByHash(id:Id, callback:(Either[Exception,Resource])=>Unit) =
		past.lookup(id, callback)
	
	def insert(resource:Resource, callback:(Either[Exception,Int]=>Unit)) = {
		past.insert(resource, (result:Either[Exception,Array[java.lang.Boolean]]) => result match {
			case Left(ex) => callback(new Left(ex))
			case Right(ret) => callback(new Right(ret.count(_.booleanValue)))
		})
	}
	
	def insertMany(resources:Array[Resource], callback:(Array[Either[Exception,Int]]=>Unit)) = {
		var remaining = resources.length
		val results = new Array[Either[Exception,Int]](remaining)
		for(i <- 0 until resources.length) {
			insert(resources(i), (result)=>{
				results(i) = result
				remaining -= 1
				if(remaining == 0)
					callback(results)
			})
		}
	}
}
