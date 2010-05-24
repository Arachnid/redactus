package org.redactus

import org.specs._
import rice.pastry.Id

class RedactusSpecTest extends SpecificationWithJUnit {
	"Resources" should {
		"create Content" in {
			val headers = Map("Content-Type" -> "text/plain")
			val body = "Hello, world!".getBytes
			val content = Resource(headers, body)

			content must haveClass[Content]
			content.asInstanceOf[Content].body mustEqual body
			content.asInstanceOf[Content].headers must haveKey("Content-Type")
		}
		
		"create and query Manifest" in {
			val headers = Map("Content-Type" -> "text/x-hashish-manifest")
			val hash1 = "0123456789012345678901234567890123456789"
			val hash2 = "1234567890123456789012345678901234567890"
			val hash3 = "2345678901234567890123456789012345678901"
			val body = (hash1 + "\t/\n" + hash2 + " /foo\n").getBytes
			val manifest = Resource(headers, body)
			
			manifest must haveClass[Manifest]

			var result = manifest.asInstanceOf[Manifest].lookup("/")
			result must beSome[(rice.p2p.commonapi.Id, String)]
			result.get._1 mustEqual Id.build(hash1)
			result.get._2 mustEqual ""

			result = manifest.asInstanceOf[Manifest].lookup("/foo")
			result must beSome[(rice.p2p.commonapi.Id, String)]
			result.get._1 mustEqual Id.build(hash2)
			result.get._2 mustEqual ""

			result = manifest.asInstanceOf[Manifest].lookup("/bar")
			result must beNone
		}
	}
}
