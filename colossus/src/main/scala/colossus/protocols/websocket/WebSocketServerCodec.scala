package colossus
package protocols.websocket

import service.Codec.ServerCodec
import service.DecodedResult
import core._
import protocols.websocket.WebSocketParser._


class WebSocketServerCodec extends ServerCodec[BaseWebSocketMessage, BaseWebSocketMessage] {
  private var parser = HandshakeRequestParser() onceAndThen WebSocketFrameParser()
  def decode(data: DataBuffer): Option[DecodedResult[BaseWebSocketMessage]] = DecodedResult.static(parser.parse(data))
  def encode(response: Websocket#Output): DataBuffer = response.bytes()
  def reset() = {
    parser =  HandshakeRequestParser() onceAndThen WebSocketFrameParser()
  }
}

