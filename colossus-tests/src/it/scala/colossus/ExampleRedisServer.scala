package colossus

import java.net.InetSocketAddress

import akka.util.ByteString
import colossus.core.ServerRef
import colossus.protocols.http.Http
import colossus.protocols.http.HttpMethod.{Get => HttpGet, Post, Put}
import colossus.protocols.http.UrlParsing.{/, Root, on}
import colossus.protocols.redis._
import colossus.service.{ClientConfig, Callback, Service}



object RedisExample {

  def start(port: Int)(implicit io: IOSystem): ServerRef = {
    import io.actorSystem.dispatcher
    import colossus.protocols.redis.Commands._
    import scala.concurrent.duration._

    Service.serve[Http]("key-value-example", port){ context =>

      val redisClient = new RedisClient(ClientConfig(new InetSocketAddress("localhost", 6379), 1.second, "redis"), context.worker)

      context.handle{ connection =>
        connection.become {
          case req @ HttpGet on Root / "get" / key => {
            redisClient.send(Get(ByteString(key))).map{
              case BulkReply(x) => req.ok(x.utf8String)
              case _ => req.error(":(")
            }

          }
          case req @ Post on Root / "set" / key / value => {
            redisClient.send(Set(ByteString(key), ByteString(value))).map{
              case StatusReply(x) => req.ok(x)
              case _ => req.error(":(")
            }

          }

        }
      }
    }
  }
}
