PROJ = InterruptController
BUILD_DIR = build
MILL = mill
YOSYS = yosys
NEXTPNR = nextpnr-ice40
ICEPACK = icepack
ICEPROG = iceprog
PCF = constraints/interrupt_controller.pcf
DEVICE = hx1k
PACKAGE = tq144

VCD_TOP = $(BUILD_DIR)/chiselsim/InterruptControllerTest/InterruptController
VCD_FILE = workdir-verilator/trace.vcd

VCD_SRC1 = $(VCD_TOP)/should-detect-both-edges-and-ignore-when-disabled/$(VCD_FILE)
VCD_DEST1 = $(BUILD_DIR)/chiselsim/interrupt_controller_test1.vcd

VCD_SRC2 = $(VCD_TOP)/should-handle-AND-mode-with-multiple-interrupts/$(VCD_FILE)
VCD_DEST2 = $(BUILD_DIR)/chiselsim/interrupt_controller_test2.vcd

VCD_SRC3 = $(VCD_TOP)/should-handle-interrupts-and-SPI-configuration/$(VCD_FILE)
VCD_DEST3 = $(BUILD_DIR)/chiselsim/interrupt_controller_test3.vcd

VCD_SRC4 = $(VCD_TOP)/should-poll-raw-state-without-triggering-pending/$(VCD_FILE)
VCD_DEST4 = $(BUILD_DIR)/chiselsim/interrupt_controller_test4.vcd

all: $(BUILD_DIR)/$(PROJ).bin

$(BUILD_DIR)/$(PROJ).sv:
	$(MILL) cs.runMain cs.InterruptControllerMain

$(BUILD_DIR)/$(PROJ)_patched.sv: $(BUILD_DIR)/$(PROJ).sv
	sed 's/automatic//g' $< > $@

$(BUILD_DIR)/$(PROJ).json: $(BUILD_DIR)/$(PROJ)_patched.sv
	$(YOSYS) -p "read_verilog -sv $<; synth_ice40 -top InterruptController -json $@" $<

$(BUILD_DIR)/$(PROJ).asc: $(BUILD_DIR)/$(PROJ).json
	$(NEXTPNR) --$(DEVICE) --package $(PACKAGE) --pcf $(PCF) --json $< --asc $@

$(BUILD_DIR)/$(PROJ).bin: $(BUILD_DIR)/$(PROJ).asc
	$(ICEPACK) $< $@

prog: $(BUILD_DIR)/$(PROJ).bin
	$(ICEPROG) $<

test:
	$(MILL) cs.test

$(VCD_DEST1): $(VCD_SRC1)
	@mkdir -p $(BUILD_DIR)/chiselsim
	@cp -f $(VCD_SRC1) $(VCD_DEST1)

$(VCD_DEST2): $(VCD_SRC2)
	@mkdir -p $(BUILD_DIR)/chiselsim
	@cp -f $(VCD_SRC2) $(VCD_DEST2)

$(VCD_DEST3): $(VCD_SRC3)
	@mkdir -p $(BUILD_DIR)/chiselsim
	@cp -f $(VCD_SRC3) $(VCD_DEST3)

$(VCD_DEST4): $(VCD_SRC4)
	@mkdir -p $(BUILD_DIR)/chiselsim
	@cp -f $(VCD_SRC4) $(VCD_DEST4)

$(VCD_SRC1) $(VCD_SRC2) $(VCD_SRC3) $(VCD_SRC4):
	$(MILL) cs.test -DemitVcd=1

view1: $(VCD_DEST1)
	gtkwave $<

view2: $(VCD_DEST2)
	gtkwave $<

view3: $(VCD_DEST3)
	gtkwave $<

view4: $(VCD_DEST4)
	gtkwave $<

view: view1

clean-mill:
	$(MILL) clean

clean: clean-mill
	rm -rf $(BUILD_DIR)/*

.PHONY: all prog test view view1 view2 view3 view4 clean clean-mill
