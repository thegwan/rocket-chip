// See LICENSE for license details.

package uncore.tilelink2

import Chisel._
import scala.collection.mutable.ListBuffer
import scala.math.max
import chisel3.internal.sourceinfo.SourceInfo

case class IntSourceParameters(device: String, num: Int)

case class IntSinkPortParameters()
case class IntSourcePortParameters(sources: Seq[IntSourceParameters])
{
  val num = sources.map(_.num).sum
}
case class IntEdge(source: IntSourcePortParameters, sink: IntSinkPortParameters)

object IntImp extends NodeImp[IntSourcePortParameters, IntSinkPortParameters, IntEdge, IntEdge, Vec[Bool]]
{
  def edgeO(po: IntSourcePortParameters, pi: IntSinkPortParameters): IntEdge = IntEdge(po, pi)
  def edgeI(po: IntSourcePortParameters, pi: IntSinkPortParameters): IntEdge = IntEdge(po, pi)
  def bundleO(eo: Seq[IntEdge]): Vec[Vec[Bool]] = {
    if (eo.isEmpty) Vec(0, Vec(0, Bool())) else
    Vec(eo.size, Vec(eo.map(_.source.num).max, Bool()))
  }
  def bundleI(ei: Seq[IntEdge]): Vec[Vec[Bool]] = {
    require (!ei.isEmpty)
    Vec(ei.size, Vec(ei.map(_.source.num).max, Bool())).flip
  }

  def connect(bo: Vec[Bool], eo: IntEdge, bi: Vec[Bool], ei: IntEdge)(implicit sourceInfo: SourceInfo): Unit = {
    require (eo == ei)
    // Cannot use bulk connect, because the widths could differ
    (bo zip bi) foreach { case (o, i) => i := o }
  }
}

case class IntIdentityNode() extends IdentityNode(IntImp)
case class IntOutputNode() extends OutputNode(IntImp)
case class IntInputNode() extends InputNode(IntImp)

case class IntSourceNode(device: String, num: Int) extends SourceNode(IntImp)(
  IntSourcePortParameters(Seq(IntSourceParameters(device, num))),
  (if (num == 0) 0 else 1) to 1)
case class IntSinkNode() extends SinkNode(IntImp)(IntSinkPortParameters())

case class IntAdapterNode(
  sourceFn:       Seq[IntSourcePortParameters] => IntSourcePortParameters,
  sinkFn:         Seq[IntSinkPortParameters]   => IntSinkPortParameters,
  numSourcePorts: Range.Inclusive = 1 to 1,
  numSinkPorts:   Range.Inclusive = 1 to 1)
  extends InteriorNode(IntImp)(sourceFn, sinkFn, numSourcePorts, numSinkPorts)

class IntXbar extends LazyModule
{
  val intnode = IntAdapterNode(
    numSourcePorts = 1 to 1, // does it make sense to have more than one interrupt sink?
    numSinkPorts   = 1 to 128,
    sourceFn       = { seq => IntSourcePortParameters(seq.map(_.sources).flatten) },
    sinkFn         = { _ => IntSinkPortParameters() })

  lazy val module = new LazyModuleImp(this) {
    val io = new Bundle {
      val in  = intnode.bundleIn
      val out = intnode.bundleOut
    }

    val cat = (intnode.edgesIn zip io.in).map{ case (e, i) => i.take(e.source.num) }.flatten
    io.out.foreach { _ := cat }
  }
}