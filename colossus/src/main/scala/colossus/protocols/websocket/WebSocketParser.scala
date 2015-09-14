package colossus
package protocols.websocket

import colossus.parsing.Combinators.Parser
import colossus.protocol.websocket._
import colossus.protocols.http.{HttpResponseParser, HttpRequestParser}
import colossus.parsing.Combinators._

class WebSocketException(message: String) extends Exception(message)

object WebSocketParser {

  object WebSocketRequestParser {
    def apply(): Parser[WebSocketRequest] = HttpRequestParser() >> { httpRequest => WebSocketRequest(httpRequest) }
  }

  object WebSocketResponseParser {
    def apply(): Parser[WebSocketResponse] =  HttpResponseParser.static() >> { httpResponse => WebSocketResponse(httpResponse.value) }
  }

  object WebSocketFrameParser {
    def apply(): Parser[WebSocket] = {
      val FIN = 0x80
      val RSV1 = 0x40
      val RSV2 = 0x20
      val RSV3 = 0x10
      val RSV_MASK = RSV1 | RSV2 | RSV3
      val OPCODE_MASK = 0x0f
      val fragmented_message_buffer = new StringBuilder
      var fragmented_message_opcode: Option[Int] = None
      byte ~ byte |> { case first ~ second => {
        val final_frame = first & FIN
        val reserved_bits = first & RSV_MASK
        if (reserved_bits != 0) throw new WebSocketException("client is using as-yet-undefined extensions")
        val frame_opcode = first & OPCODE_MASK
        val frame_opcode_is_control = frame_opcode & (0x8 | 0x9 | 0xA)
        val masked_frame = second & 0x80
        val dataLength = second & 0x7f
        if (frame_opcode_is_control != 0 && dataLength >= 126) throw new WebSocketException("control frames must have payload < 126 ")
        val p = dataLength match {
          case payloadlen if payloadlen < 126 =>
            if (masked_frame != 0) bytes(4) ~ bytes(payloadlen) >> { case frameMask ~ data => new String((0 until data.length).map(i => (data(i) ^ frameMask(i % 4)).toByte).toArray,  "UTF-8")}
            else bytes(payloadlen) >> {data => new String(data.toArray, "UTF-8")}
          case 126 =>
            if (masked_frame != 0) bytes(2) |> { case payloadlen => bytes(4) ~ bytes(payloadlen.foldLeft(0) { (sum, b) => sum * 256 + b.toInt}) >> { case frameMask ~ data => new String((0 until data.length).map(i => (data(i) ^ frameMask(i % 4)).toByte).toArray,  "UTF-8")}}
            else bytes(2) |> { case payloadlen => bytes(payloadlen.foldLeft(0) { (sum, b) => sum * 256 + b.toInt}) >> {data => new String(data.toArray, "UTF-8")}}
          case 127 =>
            if (masked_frame != 0) bytes(8) |> { case payloadlen => bytes(4) ~ bytes(payloadlen.foldLeft(0) { (sum, b) => sum * 256 + b.toInt}) >> { case frameMask ~ data => new String((0 until data.length).map(i => (data(i) ^ frameMask(i % 4)).toByte).toArray,  "UTF-8")}}
            else bytes(2) |> { case payloadlen => bytes(payloadlen.foldLeft(0) { (sum, b) => sum * 256 + b.toInt}) >> {data => new String(data.toArray, "UTF-8")}}
        }
        p >> { data =>
          // control frames
          if (frame_opcode_is_control == 0) {
            if (final_frame == 0) throw new WebSocketException("control frames must not be fragmented")
          }
          // continuation frames
          else if (frame_opcode == 0) {
            if (fragmented_message_buffer.length == 0)
              throw new WebSocketException("nothing to continue")
            else
              fragmented_message_buffer.append(data)
          }
          else {
            if (fragmented_message_buffer.length != 0) throw new WebSocketException("can't start new message until the old one is finished")
            if (final_frame != 0) {
              fragmented_message_buffer.append(data)
              fragmented_message_opcode = Some(frame_opcode)
            }
          }
          if (final_frame == 0)
            WebSocketContinuation("")
          else {
            val message = fragmented_message_buffer.toString()
            fragmented_message_buffer.clear()
            fragmented_message_opcode = None
            frame_opcode match {
              case 0x1 => WebSocketText(message)
              case 0x2 => WebSocketBinary(message)
              case 0x8 => WebSocketClose(message)
              case 0x9 => WebSocketPing(message)
              case 0xA => WebSocketPong(message)
              case _ => throw new WebSocketException("unspecific data")
            }
          }
        }
      }
      }
    }
  }

}
