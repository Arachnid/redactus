import java.net._
import java.io.IOException
import scala.collection.JavaConversions
import rice.environment.Environment
import rice.pastry._
import rice.pastry.socket.SocketPastryNodeFactory
import rice.pastry.standard.RandomNodeIdFactory

class DistTutorial(bindport: Int, env: Environment) {
	val nidFactory: NodeIdFactory = new RandomNodeIdFactory(env)
	val factory: PastryNodeFactory = new SocketPastryNodeFactory(nidFactory, bindport, env)
	val node: PastryNode = factory newNode
	  	
	def boot(bootaddresses: InetSocketAddress*) : Boolean = {
		node.boot(JavaConversions.asCollection[Object](bootaddresses))
		node.synchronized {
			while(!node.isReady && !node.joinFailed) {
				printf("Booting...\n")
				node.wait(500)
			}
			return !node.joinFailed
		}
	}
}

object DistTutorial {
	def main(args: Array[String]): Unit = {
		val env = new Environment
		env.getParameters.setString("nat_search_policy", "never")

		val bindport = Integer.parseInt(args(0))
		val tut = new DistTutorial(bindport, env)
		if(args.length > 1) {
			val bootaddr = InetAddress.getByName(args(1))
			val bootport = Integer.parseInt(args(2))
			val bootaddress = new InetSocketAddress(bootaddr, bootport)
			if(!tut.boot(bootaddress)) {
				printf("Failed to boot.\n")
				exit
			}
		} else {
			tut.boot()
		}
		printf("Booted!\n")
	}
}
