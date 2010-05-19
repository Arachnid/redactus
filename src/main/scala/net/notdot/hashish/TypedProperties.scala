package net.notdot.hashish

import java.util.Properties

class TypedProperties(defaults:Properties) extends Properties(defaults) {
	def this() = this(null)
	def getInt(name:String) = Integer.parseInt(getProperty(name))
	def getString(name:String) = getProperty(name)
}