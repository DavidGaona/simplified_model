import akka.actor.{Actor, ActorRef, ActorSystem, Props}

case class AddNetworks(numberOfNetworks: Int, numberOfAgents: Int, density: Int, degreeDistribution: Double)
case object hola

object Main extends App {
  val system = ActorSystem("simplified")
  val monitor = system.actorOf(Props(new Monitor()), "Monitor")
  monitor ! AddNetworks(100, 1000, 5, 2.5)
}
