import mill._
import mill.scalalib._

object cs extends ScalaModule {
  def scalaVersion = "2.13.14"

  def ivyDeps = Agg(
    ivy"org.chipsalliance::chisel:7.0.0-RC3"
  )

  def scalacPluginIvyDeps = Agg(
    ivy"org.chipsalliance:::chisel-plugin:7.0.0-RC3"
  )

  object test extends ScalaTests with TestModule.ScalaTest {
    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"org.scalatest::scalatest:3.2.19"
    )
  }
}
