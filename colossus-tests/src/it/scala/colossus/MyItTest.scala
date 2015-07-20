package colossus

import colossus.core.ServerRef
import colossus.protocols.http.HttpCodes
import colossus.testkit.HttpServiceSpec
import org.scalatest.{ShouldMatchers, WordSpec}
import scala.concurrent.duration._

import scala.concurrent.duration.FiniteDuration

class MyItTest extends WordSpec with ShouldMatchers{

  "MY test" should {
    "work" in {
      1 shouldBe 1
    }
  }

}


class MyRedisServerItTest extends HttpServiceSpec {
  override def service: ServerRef = RedisExample.start(8888)

  override def requestTimeout: FiniteDuration = 1.seconds

  "My awesome IT tset" should {
    "fucking work like a champ" in {
      val res = testPost("/set/mykey/nick", "", HttpCodes.OK)
      testGet("/get/mykey", "nick")
    }
  }
}