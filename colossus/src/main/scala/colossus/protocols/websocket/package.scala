package colossus
package protocols

import java.nio.charset.StandardCharsets._
import java.security.MessageDigest

import akka.util.ByteString
import colossus.protocols.http.{HttpCodes, HttpVersion, HttpResponse, HttpResponseHeader}
import colossus.service.{Callback, CodecProvider, CodecDSL}
import colossus.core.{DisconnectCause, WorkerRef}
import org.apache.commons.codec.binary.Base64
import service._

import scala.concurrent.ExecutionContext

/**
 * Created by zhoucen on 15/9/15.
 */
package object websocket {

  trait Websocket extends CodecDSL{
    type Input = BaseWebSocketMessage
    type Output = BaseWebSocketMessage
  }

  class WebsocketServiceHandler[D <: Websocket]
  (config: ServiceConfig[D#Input, D#Output], worker: WorkerRef, provider: CodecProvider[D], initializer: CodecDSL.HandlerGenerator[D])
  (implicit ex: ExecutionContext, tagDecorator: TagDecorator[Websocket#Input, Websocket#Output] = TagDecorator.default[Websocket#Input, Websocket#Output])
    extends BasicServiceHandler[D](config, worker, provider, initializer) {

    def handlehandshake : PartialFunction[D#Input, Callback[D#Output]] = {
      case request: HandshakeRequest => {
        val headers = request.headers.foldLeft(List[(String, String)]()) { (headers, keyVal) =>
          val low = (keyVal._1.toLowerCase, keyVal._2)
          keyVal match {
            case ("connection", "Upgrade") =>
              ("Connection", "Upgrade") :: headers
            case ("upgrade", "websocket") =>
              ("Upgrade", "websocket") :: headers
            case ("host", host) =>
              headers
            case ("origin", origin) =>
              headers
            case ("sec-websocket-version", secWebSocketVersion) =>
              headers
            case ("sec-websocket-key", secWebSocketKey) =>
              val md = MessageDigest.getInstance("SHA1")
              val sha1 = md.digest((secWebSocketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(UTF_8))
              val accept = new String(Base64.encodeBase64(sha1))
              ("Sec-WebSocket-Accept", accept) :: headers
            case _ => headers
          }
        }
        Callback.successful(new HandshakeResponse(headers))
      }
    }

    override def processRequest(i: D#Input): Callback[D#Output] = (handlehandshake orElse fullHandler)(i)

  }

  implicit object WebsocketProvider extends CodecProvider[Websocket] {
    def provideCodec = new WebSocketServerCodec

    override def provideHandler(config: ServiceConfig[Websocket#Input, Websocket#Output], worker: WorkerRef, initializer: CodecDSL.HandlerGenerator[Websocket])
                               (implicit ex: ExecutionContext, tagDecorator: TagDecorator[Websocket#Input, Websocket#Output] = TagDecorator.default[Websocket#Input, Websocket#Output]): DSLHandler[Websocket] = {
      new WebsocketServiceHandler[Websocket](config, worker, this, initializer)
    }

    def errorResponse(request: BaseWebSocketMessage, reason: Throwable) = WebSocketText(s"Error: $reason")

  }
}
