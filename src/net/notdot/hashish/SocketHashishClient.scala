package net.notdot.hashish

import java.net.InetSocketAddress
import rice.pastry._
import rice.p2p.past._
import rice.environment.Environment
import rice.pastry.commonapi.PastryIdFactory
import rice.pastry.socket.SocketPastryNodeFactory
import rice.pastry.standard.RandomNodeIdFactory
import rice.persistence._

class SocketHashishClient(properties:TypedProperties, env:Environment)
extends AbstractHashishClient(env, new SocketPastryNodeFactory(
		new RandomNodeIdFactory(env), properties.getInt("listen_port"), env),
		properties)
