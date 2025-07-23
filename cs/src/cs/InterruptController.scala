// InterruptController.scala - Simple edge sensitive interrupt controller
// By: rjjt
// Created: 7/22/25

package cs

import chisel3._
import chisel3.util._

class InterruptController(n: Int = 4) extends RawModule {
  val clk = IO(Input(Clock()))
  val rst = IO(Input(Bool()))
  val irqs = IO(Input(Vec(n, Bool())))
  val sclk = IO(Input(Bool()))
  val mosi = IO(Input(Bool()))
  val cs_n = IO(Input(Bool()))
  val miso = IO(Output(Bool()))
  val exti = IO(Output(Bool()))

  val sclkPrev = withClockAndReset(clk, rst) { RegNext(sclk, false.B) }
  val sclkRise = sclk && !sclkPrev
  val sclkFall = !sclk && sclkPrev

  val spiShift = withClockAndReset(clk, rst) { RegInit(0.U(16.W)) }
  val bitCount = withClockAndReset(clk, rst) { RegInit(0.U(5.W)) }
  val misoReg = withClockAndReset(clk, rst) { RegInit(0.U(1.W)) }
  miso := misoReg

  val enable = withClockAndReset(clk, rst) { RegInit(VecInit(Seq.fill(n)(false.B))) }
  val edgeSel = withClockAndReset(clk, rst) { RegInit(VecInit(Seq.fill(n)(0.U(2.W)))) }
  val mode = withClockAndReset(clk, rst) { RegInit(false.B) }
  val pending = withClockAndReset(clk, rst) { RegInit(VecInit(Seq.fill(n)(false.B))) }
  val rawState = Wire(Vec(n, Bool()))
  rawState := irqs

  val irqsLast = withClockAndReset(clk, rst) { RegNext(irqs, VecInit(Seq.fill(n)(false.B))) }
  val rising = Wire(Vec(n, Bool()))
  val falling = Wire(Vec(n, Bool()))
  for (i <- 0 until n) {
    rising(i) := irqs(i) && !irqsLast(i)
    falling(i) := !irqs(i) && irqsLast(i)
    when (enable(i)) {
      when (edgeSel(i) === 1.U && rising(i) || edgeSel(i) === 2.U && falling(i) || edgeSel(i) === 3.U && (rising(i) || falling(i))) {
        pending(i) := true.B
      }
    }
  }

  val pendingEnabled = (pending zip enable).map { case (p, e) => p && e }
  val hasEnabled = enable.reduce(_ || _)
  val allEnabledPending = (enable zip pending).map { case (e, p) => ~e | p }.reduce(_ && _)
  exti := Mux(mode, hasEnabled && allEnabledPending, pendingEnabled.reduce(_ || _))

  val readData = withClockAndReset(clk, rst) { RegInit(0.U(8.W)) }

  val new_spiShift = (spiShift << 1) | mosi

  when (!cs_n) {
    when (sclkRise) {
      spiShift := new_spiShift
      bitCount := bitCount + 1.U
    }
    when (sclkFall && bitCount > 8.U) {
      misoReg := readData(7)
      readData := readData << 1
    }
    when (bitCount === 8.U && sclkFall) {
      val tempCmd = spiShift(7, 0)
      val code = tempCmd(7, 4)
      val index = tempCmd(1, 0)
      switch (code) {
        is (3.U) {
          when (index < n.U) { readData := pending(index) ## 0.U(7.W) }
        }
        is (4.U) {
          when (index < n.U) { readData := rawState(index) ## 0.U(7.W) }
        }
      }
    }
    when (bitCount === 15.U && sclkRise) {
      val new_cmd = new_spiShift(15, 8)
      val new_data = new_spiShift(7, 0)
      val code = new_cmd(7, 4)
      val index = new_cmd(1, 0)
      switch (code) {
        is (0.U) {
          when (index < n.U) { enable(index) := new_data(0) }
        }
        is (1.U) {
          when (index < n.U) { edgeSel(index) := new_data(1, 0) }
        }
        is (2.U) {
          mode := new_data(0)
        }
        is (5.U) {
          pending.foreach(_ := false.B)
        }
      }
    }
  } .otherwise {
    bitCount := 0.U
    readData := 0.U
  }
}
