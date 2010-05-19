package net.notdot.hashish

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Properties
import rice.environment.Environment
import net.notdot.hashish.web.HashishWebServer

object HashishApp {
	def main(args: Array[String]):Unit = {
		val baseProps = new TypedProperties
		baseProps.load(this.getClass().getClassLoader().getResourceAsStream("META-INF/hashish.properties"))

		val propFile = new File("./hashish.properties")
		val properties = if(propFile.exists)
			new TypedProperties(baseProps)
		else
			baseProps
		
		val env = new Environment
		val client = new SocketHashishClient(properties, env)
		
		val bootAddresses = (properties.getString("boot_addresses")
			.split(",").map(_.split(":"))
			.map(x=>new InetSocketAddress(InetAddress.getByName(x(0)), Integer.parseInt(x(1)))))
		printf("Booting...\n")
		client.boot(bootAddresses) match {
			case Some(e) => printf(e.toString)
			case None => printf("Booted!")
		}
		
		val server = new HashishWebServer(properties.getInt("webserver_port"), client)
		server.start()
	}
}