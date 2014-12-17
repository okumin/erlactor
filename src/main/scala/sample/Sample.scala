package sample

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import erlactor.Erlactor.Ask
import erlactor.ErlactorSelection.RemoteProcessName
import erlactor.ErlangTerm.ErlangList
import erlactor.{ErlactorExtension, ErlangTerm}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Sample {
  val system = ActorSystem("erlactor")
  val yamabikoClient = system.actorOf(Props(classOf[YamabikoClient]), name = "yamabiko-client")

  implicit val timeout = Timeout(3.seconds)

  case class YamabikoRequest(name: RemoteProcessName, term: ErlangTerm)
  case class KillYamabiko(name: RemoteProcessName)

  class YamabikoClient extends Actor with ActorLogging {
    private[this] val extension = ErlactorExtension(system)

    override def receive: Receive = {
      case YamabikoRequest(name, term) =>
        val response = for {
          process <- extension.resolveByName(name)
          response <- (process ? Ask(term)).mapTo[ErlangTerm]
        } yield response
        response.onComplete {
          case Success(t) =>
            log.info(s"Yamabiko returned. $t")
          case Failure(e) =>
            log.error(e, "Yamabiko failed.")
        }
      case KillYamabiko(name) =>
        extension.resolveByName(name).foreach { process =>
          context.stop(process)
        }
    }
  }

  def main(args: Array[String]): Unit = {
    yamabikoClient ! YamabikoRequest(RemoteProcessName(node = "mofu", name = "server"), ErlangList("yahoo!"))
    yamabikoClient ! YamabikoRequest(RemoteProcessName(node = "mofu", name = "server"), ErlangTerm.ErlangTrue)
  }
}
