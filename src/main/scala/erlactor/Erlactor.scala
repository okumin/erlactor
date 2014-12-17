package erlactor

import akka.actor.{Actor, ActorLogging}
import akka.util.Timeout
import com.ericsson.otp.erlang.OtpErlangExit
import erlactor.Erlactor.Ask
import erlactor.ErlangTerm.{ErlangRef, ErlangPid, ErlangTuple2}
import scala.concurrent.duration._
import scala.util.control.NonFatal

private[erlactor] class Erlactor(pid: ErlangPid) extends Actor with ActorLogging {
  import context.dispatcher
  private[this] val extension = ErlactorExtension(context.system)
  private[this] val erlangMailBox = extension.createErlangMailBox()
  private[this] val selfPid = {
    val s = erlangMailBox.self()
    ErlangPid(s.node(), s.id(), s.serial(), s.creation())
  }
  context.system.scheduler.schedule(
    Erlactor.DeathWatchInterval,
    Erlactor.DeathWatchInterval,
    self,
    Erlactor.DeathWatch)

  override def receive: Receive = {
    case term: ErlangTerm =>
      erlangMailBox.send(pid.toOtpErlangPid, term.toOtpErlangObject)
    case a @ Ask(term) =>
      val from = ErlangTuple2(selfPid, extension.makeRef())
      val request = ErlangTuple2(from, term)
      erlangMailBox.send(pid.toOtpErlangPid, request.toOtpErlangObject)
      val replyTo = sender()
      try {
        val response = erlangMailBox.receive(a.timeout.duration.toMillis)
        replyTo ! ErlangTerm.fromOtpErlangObject(response)
      } catch {
        case NonFatal(e) => replyTo ! akka.actor.Status.Failure(e)
      }
    case Erlactor.DeathWatch =>
      try {
        erlangMailBox.link(pid.toOtpErlangPid)
      } catch {
        case e: OtpErlangExit =>
          log.info(s"$pid is dead. ${e.getStackTrace}")
          context.stop(self)
      }
  }

  override def postStop(): Unit = {
    super.postStop()
    erlangMailBox.exit("kill")
  }
}

object Erlactor {
  private val DeathWatchInterval = 100.millis
  private case object DeathWatch

  case class Ask(term: ErlangTerm)(implicit val timeout: Timeout)
}
