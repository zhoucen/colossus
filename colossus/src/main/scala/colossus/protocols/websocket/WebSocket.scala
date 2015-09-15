package colossus
package protocols
package websocket

import java.nio.ByteOrder

import akka.util.{ByteStringBuilder, ByteString}
import colossus.core.DataBuffer


sealed trait BaseWebSocketMessage {
  def bytes(): DataBuffer
}

case class HandshakeRequest(url:String, headers: List[(String,String)]) extends BaseWebSocketMessage{
  def bytes() = DataBuffer({
    val reqString = ByteString(s"GET $url HTTP/1.1\r\n")
    if (headers.size > 0) {
      val headerString = ByteString(headers.map{case(k,v) => k + ": " + v}.mkString("\r\n"))
      reqString ++ headerString ++ ByteString("\r\n\r\n")
    } else {
      reqString ++ ByteString("\r\n")
    }
  })
}

case class HandshakeResponse(headers: List[(String,String)]) extends BaseWebSocketMessage{
  def bytes() = DataBuffer({
    val reqString = ByteString(s"HTTP/1.1 101 Switching Protocols\r\n")
    if (headers.size > 0) {
      val headerString = ByteString(headers.map{case(k,v) => k + ": " + v}.mkString("\r\n"))
      reqString ++ headerString ++ ByteString("\r\n\r\n")
    } else {
      reqString ++ ByteString("\r\n")
    }
  })
}

sealed trait WebSocketBaseFrame extends BaseWebSocketMessage {
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

