package erlactor

import akka.actor._
import akka.pattern.ask
import com.ericsson.otp.erlang.{OtpMbox, OtpNode}
import erlactor.ErlactorSelection.{RemoteProcessName, ResolveByName, ResolveByPid}
import erlactor.ErlangTerm.{ErlangRef, ErlangPid}
import scala.concurrent.Future

private[erlactor] class ErlactorExtensionImpl(system: ExtendedActorSystem) extends Extension {
  private[this] val node = new OtpNode("erlactor")
  private[this] val erlactorSelection = {
    system.actorOf(Props(classOf[ErlactorSelection], node.createMbox()), name = "erlactor-selection")
  }

  private[erlactor] def createErlangMailBox(): OtpMbox = synchronized {
    node.createMbox()
  }

  private[erlactor] def makeRef(): ErlangRef = synchronized {
    val ref = node.createRef()
    ErlangRef(ref.node(), ref.ids(), ref.creation())
  }

  def resolveByPid(pid: ErlangPid): Future[ActorRef] = {
    implicit val timeout = ErlactorSelection.ResolveTimeout
    (erlactorSelection ? ResolveByPid(pid)).mapTo[ActorRef]
  }

  def resolveByName(name: RemoteProcessName): Future[ActorRef] = {
    implicit val timeout = ErlactorSelection.ResolveTimeout
    (erlactorSelection ? ResolveByName(name)).mapTo[ActorRef]
  }
}

object ErlactorExtension extends ExtensionId[ErlactorExtensionImpl] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = ErlactorExtension

  override def createExtension(system: ExtendedActorSystem): ErlactorExtensionImpl = {
    new ErlactorExtensionImpl(system)
  }
}
