package colossus
package protocols.websocket

import akka.util.ByteString
import colossus.core.DataBuffer
import colossus.parsing.Combinators.Parser
import colossus.parsing.Combinators._

class WebSocketException(message: String) extends Exception(message)

object WebSocketParser {

  case class HttpMessage(method:String, path:String, version:String, headers:List[(String, String)], contentLength:Option[Int], transferEncoding:Option[String])

  object HttpMessageParser{

    class RBuilder {
      var method: String = ""
      var path: String = ""
      var version: String = ""
      var headers: List[(String, String)] = Nil
      var contentLength: Option[Int] = None
      var transferEncoding: Option[String] = None
      var body: Option[ByteString] = None

      def addHeader(rawName: String, rawValue: String) {
        val name = rawName.toLowerCase
        val value = rawValue.trim
        headers = (name, value) :: headers
        if (name == "content-length") {
          contentLength = Some(value.toInt)
        } else if (name == "transfer-encoding") {
          transferEncoding = Some(value)
        }
      }

      def build = {
        val r = new HttpMessage(method, path, version, headers, contentLength, transferEncoding)
        reset()
        r
      }

      def reset() {
        headers = Nil
        contentLength = None
        transferEncoding = None
        body = None
      }
    }

    var requestBuilder = new RBuilder
    var headerState = 0 //incremented when parsing \r\n\r\n


    trait MiniParser {
      def parse(c: Char)
      def end()
    }

    class FirstLineParser extends MiniParser {
      val STATE_METHOD  = 0
      val STATE_PATH    = 1
      val STATE_VERSION = 2
      var state = STATE_METHOD
      val builder = new StringBuilder
      def parse(c: Char) {
        if (c == ' ') {
          val res = builder.toString
          builder.setLength(0)
          state match {
            case STATE_METHOD => {
              requestBuilder.method = res
              state = STATE_PATH
            }
            case STATE_PATH   => {
              requestBuilder.path = res
              state = STATE_VERSION
            }
            case _ => {
              throw new WebSocketException("invalid content in header first line")
            }
          }
        } else {
          builder.append(c)
        }
      }
      def end() {
        val res = builder.toString
        builder.setLength(0)
        requestBuilder.version = res
        state = STATE_METHOD
      }
    }

    class HeaderParser extends MiniParser {
      val STATE_KEY   = 0
      val STATE_VALUE = 1
      var state = STATE_KEY
      val builder = new StringBuilder
      var builtKey = ""
      def parse(c: Char) {
        if (c == ':' && state == STATE_KEY) {
          builtKey = builder.toString
          builder.setLength(0)
          state = STATE_VALUE
        } else {
          builder.append(c)
        }
      }
      def end() {
        requestBuilder.addHeader(builtKey, builder.toString)
        builder.setLength(0)
        state = STATE_KEY
      }
    }

    val fparser = new FirstLineParser
    val hparser = new HeaderParser

    var currentParser: MiniParser = fparser

    def parse(d: DataBuffer): Option[HttpMessage] = {
      while (d.hasUnreadData) {
        val b = d.next.toChar
        if (b == '\r') {
          headerState += 1
        } else if (b == '\n') {
          headerState += 1
          if (headerState == 2) {
            //finished reading in a line
            currentParser.end()
            if (currentParser == fparser) {
              currentParser = hparser
            }
          } else if (headerState == 4) {
            currentParser = fparser
            headerState = 0
            return Some(requestBuilder.build)
          }
        } else {
          currentParser.parse(b)
          headerState = 0
        }
      }
      None
    }
  }


  object HandshakeRequestParser {
    def apply(): Parser[HandshakeRequest] = {
      new Parser[HandshakeRequest] {
        def parse(d: DataBuffer) = HttpMessageParser.parse(d) match {
          case Some(HttpMessage(_, path, _, headers, _, _)) => Some(new HandshakeRequest(path, headers))
          case None => None
        }
      }
    }
  }

  object HandshakeResponseParser {
    def apply(): Parser[HandshakeResponse] = {
      new Parser[HandshakeResponse] {
        def parse(d: DataBuffer) = HttpMessageParser.parse(d) match {
          case Some(HttpMessage(_,_,_,headers,_,_)) => Some(new HandshakeResponse(headers))
          case None => None
        }
      }
    }
  }

  object WebSocketFrameParser {
    def apply(): Parser[BaseWebSocketMessage] = {
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
