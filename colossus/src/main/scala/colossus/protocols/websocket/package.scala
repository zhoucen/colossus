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

  trait WebsocketProvider extends CodecProvider[Websocket] {
    def provideCodec = new WebSocketServerCodec

    override def provideHandler(config: ServiceConfig[Websocket#Input, Websocket#Output], worker: WorkerRef, initializer: CodecDSL.HandlerGenerator[Websocket])
                               (implicit ex: ExecutionContext, tagDecorator: TagDecorator[Websocket#Input, Websocket#Output] = TagDecorator.default[Websocket#Input, Websocket#Output]): DSLHandler[Websocket] = {
      new WebsocketServiceHandler[Websocket](config, worker, this, initializer)
    }

  }

  class WebsocketServiceHandler[D <: Websocket]
  (config: ServiceConfig[D#Input, D#Output], worker: WorkerRef, provider: CodecProvider[D], initializer: CodecDSL.HandlerGenerator[D])
  (implicit ex: ExecutionContext, tagDecorator: TagDecorator[Websocket#Input, Websocket#Output] = TagDecorator.default[Websocket#Input, Websocket#Output])
    extends BasicServiceHandler[D](config, worker, provider, initializer) {

    override implicit lazy val sender = boundWorker.get.worker

    // Members declared in colossus.core.ConnectionHandler
    override def connected(endpoint: colossus.core.WriteEndpoint) {
      super.connected(endpoint)
      //push(Status("Please enter your name")){_ => ()}
    }

    override def connectionClosed(cause: colossus.core.DisconnectCause){}
    override def connectionLost(cause: colossus.core.DisconnectError){}
    override def idleCheck(period: scala.concurrent.duration.Duration){}

    def onMessage(data: String): Unit
    def onClose(data: String): Unit
    def onPing(data: String): Unit
    def onPong(data: String): Unit
    def writeMessage(data: String): Unit = {
      push(WebSocketText(data)){_ => ()}
    }
    def close(): Unit = {
      push(WebSocketClose("")) { _ => {}}
    }


    override def processMessage(request: BaseWebSocketMessage) {
      request match {
        case request: HandshakeRequest =>
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
                val sha1 = md.digest((secWebSocketKey+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(UTF_8))
                val accept = new String(Base64.encodeBase64(sha1))
                ("Sec-WebSocket-Accept", accept) :: headers
              case _ => headers
            }
          }
          val response = new HandshakeResponse(headers)
          push(response){_ => ()}
        case websockText: WebSocketText => {
          onMessage(websockText.message)
        }
        case websockBinary: WebSocketBinary => {
          onMessage(websockBinary.message)
        }
        case websockClose: WebSocketClose => {
          onClose(websockClose.message)
        }
        case websockPing: WebSocketPing => {
          onPing(websockPing.message)
        }
        case websockPong: WebSocketPong => {
          onPong(websockPong.message)
        }
        case _ => throw new WebSocketException("unspecific frame")


      }

    }

    override def connectionTerminated(cause: DisconnectCause) {
      super.connectionTerminated(cause)
      onClose("")
    }
    override def processRequest(input: D#Input): Callback[D#Output] = super.processRequest(input).map{response =>
//      if (response.head.version == HttpVersion.`1.0`) {
//        gracefulDisconnect()
//      }
      response
    }

  }
}
