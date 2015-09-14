package colossus
package protocols.websocket

import protocol.websocket.WebSocket
import service.Codec.ServerCodec
import service.DecodedResult
import core._
import protocols.websocket.WebSocketParser._


class WebSocketServerCodec extends ServerCodec[WebSocket, WebSocket] {
  private var parser = WebSocketRequestParser() onceAndThen WebSocketFrameParser()
  def decode(data: DataBuffer): Option[DecodedResult[WebSocket]] = parser.parse(data).map{DecodedResult.Static(_)}
  def encode(response: WebSocket): DataBuffer = response.bytes()
  def reset() = {
    parser =  WebSocketRequestParser() onceAndThen WebSocketFrameParser()
  }
}

