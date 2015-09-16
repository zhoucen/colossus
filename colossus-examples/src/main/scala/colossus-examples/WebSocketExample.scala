package colossus.examples

import colossus.IOSystem
import colossus.core.{ServerRef}
import colossus.service._
import colossus.protocols.websocket._
import Callback.Implicits._

case class Push(msg:String)

object WebSocketExample {

  def start(port: Int)(implicit io: IOSystem): ServerRef = {

    Service.serve[Websocket]("websocket-test", port) { context =>
      context.handle { connection =>
        connection.become {
          case WebSocketText(msg) => {
//            self ! Push("zhoucen")
            WebSocketText("server: "+msg)
          }
          case Push(msg) => WebSocketText("server: "+msg)
        }
      }
    }
  }
}

