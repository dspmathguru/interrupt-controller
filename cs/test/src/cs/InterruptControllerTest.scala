// InterruptControllerTest.scala - Test for the Interrupt Controller
// By: rjjt
// Created: 7/23/25

package cs

import chisel3._
import chisel3.simulator._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.funspec.AnyFunSpec

class InterruptControllerTestHarness(n: Int = 4) extends Module {
  val ic = Module(new InterruptController(n))
  ic.clk := clock
  ic.rst := reset
  val irqs = IO(Input(Vec(n, Bool())))
  val sclk = IO(Input(Bool()))
  val mosi = IO(Input(Bool()))
  val cs_n = IO(Input(Bool()))
  val miso = IO(Output(Bool()))
  val exti = IO(Output(Bool()))
  ic.irqs := irqs
  ic.sclk := sclk
  ic.mosi := mosi
  ic.cs_n := cs_n
  miso := ic.miso
  exti := ic.exti
}

class InterruptControllerTest extends AnyFunSpec with ChiselSim {
  def spiSend(dut: InterruptControllerTestHarness, code: Int, index: Int, data: Int): Unit = {
    require(code >= 0 && code <= 15, "Code must be 0-15")
    require(index >= 0 && index <= 3, "Index must be 0-3 for n=4")
    val cmdByte = (code << 4) | index
    dut.cs_n.poke(false.B)
    dut.clock.step(1)
    // Send command byte
    for (bit <- 7 to 0 by -1) {
      dut.mosi.poke((((cmdByte >> bit) & 1) == 1).B)
      dut.sclk.poke(true.B)
      dut.clock.step(1)
      dut.sclk.poke(false.B)
      dut.clock.step(1)
    }
    // Send data byte
    for (bit <- 7 to 0 by -1) {
      dut.mosi.poke((((data >> bit) & 1) == 1).B)
      dut.sclk.poke(true.B)
      dut.clock.step(1)
      dut.sclk.poke(false.B)
      dut.clock.step(1)
    }
    dut.cs_n.poke(true.B)
    dut.clock.step(2) // Extra steps to ensure transaction completion
  }

  def spiRead(dut: InterruptControllerTestHarness, code: Int, index: Int): Int = {
    require(code >= 0 && code <= 15, "Code must be 0-15")
    require(index >= 0 && index <= 3, "Index must be 0-3 for n=4")
    val cmdByte = (code << 4) | index
    dut.cs_n.poke(false.B)
    dut.clock.step(1)
    // Send command byte
    for (bit <- 7 to 0 by -1) {
      dut.mosi.poke((((cmdByte >> bit) & 1) == 1).B)
      dut.sclk.poke(true.B)
      dut.clock.step(1)
      dut.sclk.poke(false.B)
      dut.clock.step(1)
    }
    // Send dummy data, collect MISO after fall
    var result = 0
    for (bit <- 7 to 0 by -1) {
      dut.mosi.poke(false.B)
      dut.sclk.poke(true.B)
      dut.clock.step(1)
      dut.sclk.poke(false.B)
      dut.clock.step(1)
      result = (result << 1) | dut.miso.peek().litValue.toInt
    }
    dut.cs_n.poke(true.B)
    dut.clock.step(2) // Extra steps to ensure transaction completion
    result
  }

  describe("InterruptController") {
    it("should handle interrupts and SPI configuration") {
      simulate(new InterruptControllerTestHarness(4)) { dut =>
        // Initialize
        dut.irqs.foreach(_.poke(false.B))
        dut.sclk.poke(false.B)
        dut.mosi.poke(false.B)
        dut.cs_n.poke(true.B)
        dut.clock.step(1)
        dut.reset.poke(true.B)
        dut.clock.step(1)
        dut.reset.poke(false.B)
        dut.clock.step(1)

        // Test 1: Configure IRQ0 rising edge, enable, OR mode
        spiSend(dut, 0, 0, 1) // Enable IRQ0
        spiSend(dut, 1, 0, 1) // IRQ0 rising edge (01)
        spiSend(dut, 2, 0, 0) // OR mode
        dut.clock.step(2)
        dut.exti.expect(false.B)

        // Test 2: Trigger IRQ0 rising edge
        dut.irqs(0).poke(true.B)
        dut.clock.step(2)
        dut.exti.expect(true.B)
        assert(spiRead(dut, 3, 0) == 0x80, "IRQ0 pending")
        assert(spiRead(dut, 4, 0) == 0x80, "IRQ0 raw state")

        // Test 3: Clear pending
        spiSend(dut, 5, 0, 0)
        dut.clock.step(2)
        assert(spiRead(dut, 3, 0) == 0, "IRQ0 pending cleared")
        dut.exti.expect(false.B)

        // Test 4: Configure IRQ1 falling edge, AND mode
        spiSend(dut, 0, 1, 1) // Enable IRQ1
        spiSend(dut, 1, 1, 2) // IRQ1 falling edge (10)
        spiSend(dut, 2, 0, 1) // AND mode
        dut.irqs(0).poke(false.B)
        dut.irqs(1).poke(true.B)
        dut.clock.step(2)
        dut.irqs(1).poke(false.B)
        dut.clock.step(2)
        assert(spiRead(dut, 3, 1) == 0x80, "IRQ1 pending")
        dut.exti.expect(false.B) // AND mode, not all enabled IRQs pending

        // Test 5: Trigger all enabled IRQs in AND mode
        dut.irqs(0).poke(true.B)
        dut.clock.step(2)
        dut.exti.expect(true.B) // Both IRQ0 and IRQ1 pending

        // Test 6: Disable IRQ0, check OR mode
        spiSend(dut, 0, 0, 0) // Disable IRQ0
        spiSend(dut, 2, 0, 0) // OR mode
        dut.clock.step(2)
        dut.exti.expect(true.B) // IRQ1 still pending
        spiSend(dut, 0, 1, 0) // Disable IRQ1
        dut.clock.step(2)
        dut.exti.expect(false.B)
      }
    }

    it("should detect both edges and ignore when disabled") {
      simulate(new InterruptControllerTestHarness(4)) { dut =>
        // Initialize
        dut.irqs.foreach(_.poke(false.B))
        dut.sclk.poke(false.B)
        dut.mosi.poke(false.B)
        dut.cs_n.poke(true.B)
        dut.clock.step(1)
        dut.reset.poke(true.B)
        dut.clock.step(1)
        dut.reset.poke(false.B)
        dut.clock.step(1)

        // Configure IRQ2 to both edges (11), enable it, OR mode
        spiSend(dut, 0, 2, 1) // Enable IRQ2
        spiSend(dut, 1, 2, 3) // IRQ2 both edges (11)
        spiSend(dut, 2, 0, 0) // OR mode
        dut.clock.step(2)
        dut.exti.expect(false.B)

        // Trigger rising edge on IRQ2
        dut.irqs(2).poke(true.B)
        dut.clock.step(2)
        dut.exti.expect(true.B)
        assert(spiRead(dut, 3, 2) == 0x80, "IRQ2 pending after rising")
        assert(spiRead(dut, 4, 2) == 0x80, "IRQ2 raw state high")

        // Clear pending
        spiSend(dut, 5, 0, 0)
        dut.clock.step(2)
        dut.exti.expect(false.B)

        // Trigger falling edge on IRQ2
        dut.irqs(2).poke(false.B)
        dut.clock.step(2)
        dut.exti.expect(true.B)
        assert(spiRead(dut, 3, 2) == 0x80, "IRQ2 pending after falling")
        assert(spiRead(dut, 4, 2) == 0, "IRQ2 raw state low")

        // Clear pending again to ensure clean state
        spiSend(dut, 5, 0, 0)
        dut.clock.step(2)
        dut.exti.expect(false.B)

        // Disable IRQ2
        spiSend(dut, 0, 2, 0) // Disable IRQ2
        dut.clock.step(2)

        // Trigger rising again on IRQ2
        dut.irqs(2).poke(true.B)
        dut.clock.step(2)
        assert(spiRead(dut, 3, 2) == 0, "No pending when disabled")
        dut.exti.expect(false.B)
      }
    }

    it("should handle AND mode with multiple interrupts") {
      simulate(new InterruptControllerTestHarness(4)) { dut =>
        // Initialize
        dut.irqs.foreach(_.poke(false.B))
        dut.sclk.poke(false.B)
        dut.mosi.poke(false.B)
        dut.cs_n.poke(true.B)
        dut.clock.step(1)
        dut.reset.poke(true.B)
        dut.clock.step(1)
        dut.reset.poke(false.B)
        dut.clock.step(1)

        // Configure IRQ0 and IRQ3 to rising, enable both, AND mode
        spiSend(dut, 0, 0, 1) // Enable IRQ0
        spiSend(dut, 1, 0, 1) // Rising
        spiSend(dut, 0, 3, 1) // Enable IRQ3
        spiSend(dut, 1, 3, 1) // Rising
        spiSend(dut, 2, 0, 1) // AND mode
        dut.clock.step(2)

        // Trigger IRQ0 only
        dut.irqs(0).poke(true.B)
        dut.clock.step(2)
        dut.exti.expect(false.B) // Not all pending

        // Trigger IRQ3
        dut.irqs(3).poke(true.B)
        dut.clock.step(2)
        dut.exti.expect(true.B) // All pending

        // Clear pending
        spiSend(dut, 5, 0, 0)
        dut.clock.step(2)
        dut.exti.expect(false.B)
      }
    }

    it("should poll raw state without triggering pending") {
      simulate(new InterruptControllerTestHarness(4)) { dut =>
        // Initialize
        dut.irqs.foreach(_.poke(false.B))
        dut.sclk.poke(false.B)
        dut.mosi.poke(false.B)
        dut.cs_n.poke(true.B)
        dut.clock.step(1)
        dut.reset.poke(true.B)
        dut.clock.step(1)
        dut.reset.poke(false.B)
        dut.clock.step(1)

        // Disable all interrupts
        for (i <- 0 until 4) {
          spiSend(dut, 0, i, 0)
        }
        dut.clock.step(2)

        // Set IRQ1 high
        dut.irqs(1).poke(true.B)
        dut.clock.step(2)
        assert(spiRead(dut, 4, 1) == 0x80, "Raw state high")
        assert(spiRead(dut, 3, 1) == 0, "No pending since disabled")

        // Set IRQ1 low
        dut.irqs(1).poke(false.B)
        dut.clock.step(2)
        assert(spiRead(dut, 4, 1) == 0, "Raw state low")
      }
    }
  }
}

