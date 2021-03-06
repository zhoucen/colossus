package colossus
package protocols.http

import akka.util.ByteString

import core.DataBuffer
import parsing._
import DataSize._
import HttpParse._
import Combinators._

object HttpRequestParser {

  val DefaultMaxSize: DataSize = 1.MB

  def apply(size: DataSize = DefaultMaxSize) = maxSize(size, httpRequest)

  protected def httpRequest: Parser[HttpRequest] = httpHead |> {case HeadResult(head, contentLength, transferEncoding) => 
    transferEncoding match { 
      case None | Some("identity") => contentLength match {
        case Some(0) | None => const(HttpRequest(head, None))
        case Some(n) => bytes(n) >> {body => HttpRequest(head, Some(body))}
      }
      case Some(other)  => chunkedBody >> {body => HttpRequest(head, Some(body))}
    } 
  }

  protected def httpHead = new HttpHeadParser
  
}

case class HeadResult(head: HttpHead, contentLength: Option[Int], transferEncoding: Option[String] )

/**
 * This parser is optimized to reduce the number of operations per character
 * read
 */
class HttpHeadParser extends Parser[HeadResult]{

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
      if (name == HttpHeaders.ContentLength) {
        contentLength = Some(value.toInt)
      } else if (name == HttpHeaders.TransferEncoding) {
        transferEncoding = Some(value)
      }
    }

    def build: HeadResult = {
      val r = HeadResult(HttpHead(HttpMethod(method), path, HttpVersion(version), headers), contentLength, transferEncoding)
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
            throw new ParseException("invalid content in header first line")
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


  def parse(d: DataBuffer): Option[HeadResult] = {
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
          //two consecutive \r\n indicates the end of the request head
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


