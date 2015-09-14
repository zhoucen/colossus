package colossus
package protocol
package websocket

import akka.util.{ByteStringBuilder, ByteString}
import colossus.core.DataBuffer
import colossus.protocols.http.{HttpRequest, HttpResponse}
import java.nio.ByteOrder

sealed trait WebSocket {
  def bytes(): DataBuffer
}

case class WebSocketRequest(request: HttpRequest) extends WebSocket {
  def bytes() = DataBuffer(request.bytes)
}

case class WebSocketResponse(response: HttpResponse) extends WebSocket {
  def bytes() = response.encode()
}

sealed trait WebSocketBaseFrame extends WebSocket {
  implicit val byteOrder = ByteOrder.BIG_ENDIAN
  def onFrame(opcode: Byte, data: String) = {
    val finalFrame: Byte =  0x80.toByte
    val builder = new ByteStringBuilder
    // first byte
    builder.putByte((finalFrame | opcode).toByte)
    data.getBytes().length match {
      case len: Int if (len < 126) =>
        builder.putByte(len.toByte)
      case len: Int if (len < (2 << 16 - 1)) =>
        builder.putByte(126.toByte)
        builder.putShort(len.toShort)
      case len: Int if (len < (2 << 64 - 1)) =>
        builder.putByte(127.toByte)
        builder.putLong(len.toLong)
    }

    builder.putBytes(data.getBytes)
    DataBuffer(builder.result())
  }
}

case class WebSocketText(message: String) extends WebSocketBaseFrame {
  def bytes() = onFrame(0x1.toByte, message)
}

case class WebSocketBinary(message: String) extends WebSocketBaseFrame {
  def bytes() = onFrame(0x2.toByte, message)
}

case class WebSocketClose(message: String) extends WebSocketBaseFrame {
  def bytes() = onFrame(0x8.toByte, message)
}

case class WebSocketPing(message: String) extends WebSocketBaseFrame {
  def bytes() = onFrame(0x9.toByte, message)
}

case class WebSocketPong(message: String) extends WebSocketBaseFrame {
  def bytes() = onFrame(0xA.toByte, message)
}

case class WebSocketContinuation(message: String) extends WebSocketBaseFrame {
  def bytes() = DataBuffer(ByteString(message))
}

