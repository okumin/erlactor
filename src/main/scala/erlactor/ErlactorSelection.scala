package erlactor

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.util.Timeout
import com.ericsson.otp.erlang.OtpMbox
import erlactor.ErlactorSelection.{ProcessRef, RemoteProcessName, ResolveByName, ResolveByPid}
import erlactor.ErlangTerm.{ErlangAtom, ErlangPid, ErlangTuple2}
import scala.concurrent.duration._
import scala.util.control.NonFatal

private[erlactor] class ErlactorSelection(mailBox: OtpMbox) extends Actor with ActorLogging {
  private[this] val extension = ErlactorExtension(context.system)
  private[this] val identityMessage: ErlangTerm = {
    val pid = mailBox.self()
    val from = ErlangTuple2(
      ErlangPid(pid.node(), pid.id(), pid.serial(), pid.creation()),
      extension.makeRef())
    ErlangTuple2(from, ErlangAtom("identity"))
  }
  private[this] var memo1: Map[ActorRef, ProcessRef] = Map.empty
  private[this] var memo2: Map[ErlangPid, ActorRef] = Map.empty
  private[this] var memo3: Map[RemoteProcessName, ActorRef] = Map.empty
  private[this] def updateMemo(processRef: ProcessRef, actor: ActorRef): ActorRef = {
    val ProcessRef(pid, nameOpt) = processRef
    memo1 = memo1.updated(actor, processRef)
    memo2 = memo2.updated(pid, actor)
    nameOpt.foreach { name =>
      memo3 = memo3.updated(name, actor)
    }
    actor
  }
  private[this] def resolve(remoteProcessName: Option[RemoteProcessName]): Unit = {
    try {
      log.info("Waiting the response...")
      val response = mailBox.receive(ErlactorSelection.ResolveTimeout.duration.toMillis)
      ErlangTerm.fromOtpErlangObject(response) match {
        case ErlangTuple2(_, pid: ErlangPid) =>
          val processRef = ProcessRef(pid, remoteProcessName)
          val ref = memo2.get(pid) match {
            case Some(r) =>
              updateMemo(processRef, r)
            case None =>
              mailBox.link(pid.toOtpErlangPid)
              val r = context.actorOf(
                Props(classOf[Erlactor], pid),
                name = s"erlactor-${pid.node}-${pid.id}-${pid.serial}-${pid.creation}")
              context.watch(r)
              updateMemo(processRef, r)
          }
          log.info(s"Get Erlactor $ref")
          sender() ! ref
        case term => throw new RuntimeException(s"An unexpected term was received. term = $term")
      }
    } catch {
      case NonFatal(e) => sender() ! akka.actor.Status.Failure(e)
    }
  }

  override def receive: Receive = {
    case ResolveByPid(pid) =>
      memo2.get(pid) match {
        case Some(ref) =>
          sender() ! ref
        case None =>
          mailBox.send(pid.toOtpErlangPid, identityMessage.toOtpErlangObject)
          resolve(None)
      }
    case ResolveByName(name) =>
      memo3.get(name) match {
        case Some(ref) =>
          sender() ! ref
        case None =>
          mailBox.send(name.name, name.node, identityMessage.toOtpErlangObject)
          resolve(Some(name))
      }
    case Terminated(ref) =>
      memo1.get(ref) match {
        case None =>
        case Some(ProcessRef(pid, nameOpt)) =>
          memo1 = memo1 - ref
          memo2 = memo2 - pid
          nameOpt.foreach { name =>
            memo3 = memo3 - name
          }
      }
  }
}

object ErlactorSelection {
  private[erlactor] val ResolveTimeout = Timeout(1.seconds)
  private[erlactor] case class ProcessRef(pid: ErlangPid, name: Option[RemoteProcessName])
  private[erlactor] case class ResolveByPid(pid: ErlangPid)
  private[erlactor] case class ResolveByName(name: RemoteProcessName)

  case class RemoteProcessName(node: String, name: String)
}
