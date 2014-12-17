package erlactor

import akka.util.ByteString
import com.ericsson.otp.erlang._

sealed trait ErlangTerm {
  private[erlactor] def toOtpErlangObject: OtpErlangObject
}

object ErlangTerm {
  case class ErlangAtom(value: String) extends ErlangTerm {
    override private[erlactor] def toOtpErlangObject: OtpErlangObject = {
      new OtpErlangAtom(value)
    }
  }
  val ErlangTrue = ErlangAtom("true")
  val ErlangFalse = ErlangAtom("false")

  case class ErlangBinary(value: Array[Byte]) extends ErlangTerm {
    override private[erlactor] def toOtpErlangObject: OtpErlangObject = {
      new OtpErlangBinary(value.toArray)
    }
  }

  case class ErlangFloat(value: Double) extends ErlangTerm {
    override private[erlactor] def toOtpErlangObject: OtpErlangObject = {
      new OtpErlangDouble(value)
    }
  }

  case class ErlangInteger(value: BigInt) extends ErlangTerm {
    override private[erlactor] def toOtpErlangObject: OtpErlangObject = {
      new OtpErlangLong(value.bigInteger)
    }
  }

  case class ErlangList[A <: ErlangTerm](value: List[A]) extends ErlangTerm {
    override private[erlactor] def toOtpErlangObject: OtpErlangObject = {
      new OtpErlangList(value.map(_.toOtpErlangObject).toArray)
    }
  }
  object ErlangList {
    def apply(value: String): ErlangList[ErlangInteger] = {
      ErlangList(value.toList.map(c => ErlangInteger(c.toInt)))
    }
  }

  case class ErlangPid(node: String, id: Int, serial: Int, creation: Int) extends ErlangTerm {
    override private[erlactor] def toOtpErlangObject: OtpErlangObject = toOtpErlangPid

    private[erlactor] def toOtpErlangPid: OtpErlangPid = {
      new OtpErlangPid(node, id, serial, creation)
    }
  }

  case class ErlangRef(node: String, ids: Array[Int], creation: Int) extends ErlangTerm {
    override private[erlactor] def toOtpErlangObject: OtpErlangObject = {
      new OtpErlangRef(node, ids, creation)
    }
  }

  sealed trait ErlangTuple extends ErlangTerm {
    protected def value: Array[ErlangTerm]

    override private[erlactor] def toOtpErlangObject: OtpErlangObject = {
      new OtpErlangTuple(value.map(_.toOtpErlangObject))
    }
  }
  case class ErlangTuple1[A <: ErlangTerm](v1: A) extends ErlangTuple {
    override protected def value: Array[ErlangTerm] = Array(v1)
  }
  case class ErlangTuple2[A <: ErlangTerm, B <: ErlangTerm](v1: A, v2: B) extends ErlangTuple {
    override protected def value: Array[ErlangTerm] = Array(v1, v2)
  }

  private[erlactor] def fromOtpErlangObject(obj: OtpErlangObject): ErlangTerm = {
    obj match {
      case o: OtpErlangAtom => ErlangAtom(o.atomValue())
      case o: OtpErlangBinary => ErlangBinary(o.binaryValue())
      case o: OtpErlangFloat => ErlangFloat(o.doubleValue())
      case o: OtpErlangDouble => ErlangFloat(o.doubleValue())
      case o: OtpErlangByte => ErlangInteger(BigInt(o.bigIntegerValue()))
      case o: OtpErlangChar => ErlangInteger(BigInt(o.bigIntegerValue()))
      case o: OtpErlangShort => ErlangInteger(BigInt(o.bigIntegerValue()))
      case o: OtpErlangUShort => ErlangInteger(BigInt(o.bigIntegerValue()))
      case o: OtpErlangInt => ErlangInteger(BigInt(o.bigIntegerValue()))
      case o: OtpErlangUInt => ErlangInteger(BigInt(o.bigIntegerValue()))
      case o: OtpErlangLong => ErlangInteger(BigInt(o.bigIntegerValue()))
      case o: OtpErlangList => ErlangList(o.elements().map(fromOtpErlangObject).toList)
      case o: OtpErlangString => ErlangList(o.stringValue())
      case o: OtpErlangPid => ErlangPid(o.node(), o.id(), o.serial(), o.creation())
      case o: OtpErlangRef => ErlangRef(o.node(), o.ids(), o.creation())
      case o: OtpErlangTuple =>
        o.elements().map(fromOtpErlangObject) match {
          case Array(v1) => ErlangTuple1(v1)
          case Array(v1, v2) => ErlangTuple2(v1, v2)
        }
    }
  }
}
