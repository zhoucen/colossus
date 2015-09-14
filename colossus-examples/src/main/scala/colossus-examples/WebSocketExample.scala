package colossus.examples

import akka.actor.{Actor, Props, ActorRef}
import colossus.IOSystem
import colossus.core._
import colossus.protocols.websocket.WebSocketHandler


case class Message(msg: String, id: Long)


class DemoHandler(broadcaster: ActorRef) extends WebSocketHandler with ServerConnectionHandler {

  def onMessage(data: String): Unit = {

    data match {
      case data =>
        broadcaster ! Message(data, id.get)
    }
  }

  def onClose(data: String): Unit = {

  }
  def onPing(data: String): Unit = {
    println("ping")
  }
  def onPong(data: String): Unit = {
    println("pong")
  }

  def receivedMessage(message: Any,sender: akka.actor.ActorRef): Unit ={
    writeMessage("server: "+message.toString)
  }

  def shutdownRequest(){
    gracefulDisconnect()
  }
}

class WebSocketChatDelegator(server: ServerRef, worker: WorkerRef, broadcaster: ActorRef) extends Delegator(server, worker) {
  def acceptNewConnection = Some(new DemoHandler(broadcaster))
}

class Master extends Actor {


  def receive = {
    case Message(msg, id) => {
      sender() ! WorkerCommand.Message(id, msg)
    }

  }
}

object WebSocketExample {

  def start(port: Int)(implicit io: IOSystem): ServerRef = {
    val broadcaster = io.actorSystem.actorOf(Props[Master])
    val echoConfig = ServerConfig(
      name = "websocket",
      settings = ServerSettings(
        port = port
      ),
      delegatorFactory = (server, worker) => new WebSocketChatDelegator(server, worker, broadcaster)
    )
    Server(echoConfig)
  }
}
