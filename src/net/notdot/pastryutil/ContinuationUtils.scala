package net.notdot

import java.lang.ClassCastException
import rice.Continuation
import rice.p2p.past.PastContent
import net.notdot.hashish.Resource

package object pastryutil {
	implicit def functionToContinuation[T<:AnyRef,R<:T](callback:Either[Exception,R]=>Unit) = {
		new Continuation[T, Exception]() {
			def receiveResult(result:T) = {
				try {
					callback(new Right(result.asInstanceOf[R]))
				} catch {
					case ex:Exception => callback(new Left(ex))
				}
			}
			def receiveException(ex:Exception) = callback(new Left(ex))
		}
	}
}