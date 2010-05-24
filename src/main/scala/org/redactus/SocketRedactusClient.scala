package org.redactus

import java.net.InetSocketAddress
import rice.pastry._
import rice.p2p.past._
import rice.environment.Environment
import rice.pastry.commonapi.PastryIdFactory
import rice.pastry.socket.SocketPastryNodeFactory
import rice.pastry.standard.RandomNodeIdFactory
import rice.persistence._

class SocketRedactusClient(properties:TypedProperties, env:Environment)
extends AbstractRedactusClient(env, new SocketPastryNodeFactory(
		new RandomNodeIdFactory(env), properties.getInt("listen_port"), env),
		properties)
