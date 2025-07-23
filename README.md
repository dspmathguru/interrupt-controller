# interrupt-controoler
An example of an interrupt controller using chisel and mill on an iceStick

You will need the following installed:
- chisel (follow chisels way to install scala)
- scala
- mill
- OSS yosys, icestorm, ... https://github.com/YosysHQ/oss-cad-suite-build/releases

Run:
- make
- make prog

The above will create the verilog from the Chisel/Scala files and then program the iceStick.
