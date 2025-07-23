// InterruptControllerMain.scala - Simple edge sensitive interrupt controller
// By: rjjt
// Created: 7/22/25

package cs

import circt.stage.ChiselStage
import chisel3._

object InterruptControllerMain extends App {
  class InterruptControllerWrapper extends RawModule {
    val clk = IO(Input(Clock()))
    val rst = IO(Input(Bool()))
    val irqs = IO(Input(Vec(4, Bool())))
    val sclk = IO(Input(Bool()))
    val mosi = IO(Input(Bool()))
    val cs_n = IO(Input(Bool()))
    val miso = IO(Output(Bool()))
    val exti = IO(Output(Bool()))

    val ic = Module(new InterruptController(4))
    ic.clk := clk
    ic.rst := rst
    ic.irqs := irqs
    ic.sclk := sclk
    ic.mosi := mosi
    ic.cs_n := cs_n
    miso := ic.miso
    exti := ic.exti
  }
  ChiselStage.emitSystemVerilogFile(
    new InterruptControllerWrapper,
    Array("--target-dir", "build"),
    Array("-disable-mem-randomization", "-disable-reg-randomization",
          "--lowering-options", "disallowLocalVariables,disallowPackedArrays,noAlwaysComb")
  )
}
