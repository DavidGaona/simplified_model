import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Random, Success}


// Messages
case class RequestBelief(belief: Double)
case class SendBelief(belief: Double, senderAgent: ActorRef, opinion: Int)
case class AddToNeighborhood(agent: ActorRef)
case class SendAgentData(round: Int, name: String, belief: Double, isSpeaking: Boolean)
case object BeliefUpdated

// Actor
class Agent(agentDataSaver: ActorRef) extends Actor {
  import context.dispatcher

  var belief: Double = -1
  var prevBelief: Double = belief
  var tolRadius: Double = 0.25
  var neighbors: Vector[ActorRef] = Vector.empty
  var influences: Vector[Double] = Vector.empty
  var speaking: Boolean = true
  var prevSpeaking: Boolean = true
  var round = 0

  implicit val timeout: Timeout = Timeout(600.seconds) // Set a timeout for the ask pattern

  def receive: Receive = {
    case AddToNeighborhood(neighbor) =>
      neighbors = neighbors :+ neighbor

    case RequestBelief(_) if !prevSpeaking =>
      sender() ! SendBelief(prevBelief, self, 0)

    case RequestBelief(belief) =>
      val opinion = if (tolRadius >= math.abs(belief - prevBelief)) 1 else 2
      sender() ! SendBelief(prevBelief, self, opinion)

    case UpdateBelief =>
      prevBelief = belief
      prevSpeaking = speaking
      if (round == 0) {
        generateInfluences()
        snapshotAgentState()
      }
      round += 1

      fetchBeliefsFromNeighbors { beliefs =>
        var inFavor = 0
        var against = 0
        var influenceSum = 0.0
        belief = 0

        beliefs.foreach {
          case SendBelief(neighborBelief, neighbor, opinion) if opinion != 0 =>
            if (opinion == 1) inFavor += 1
            else against += 1
            belief += neighborBelief * influences(neighbors.indexOf(neighbor))

          case SendBelief(_, neighbor, _) =>
            influenceSum += influences(neighbors.indexOf(neighbor))
        }
        belief += prevBelief * (influenceSum + influences.last)
        speaking = inFavor >= against
        snapshotAgentState()
        context.parent ! BeliefUpdated
      }

  }

  override def preStart(): Unit = {
    val bimodal = new BimodalDistribution(0.25, 0.75)
    belief = bimodal.sample()
    prevBelief = belief
  }

  def fetchBeliefsFromNeighbors(callback: Seq[SendBelief] => Unit): Unit = {
    val futures = neighbors.map(neighbor => (neighbor ? RequestBelief(prevBelief)).mapTo[SendBelief])
    val aggregatedFutures = Future.sequence(futures)

    aggregatedFutures.onComplete {
      case Success(beliefs) =>
        callback(beliefs)

      case Failure(exception) =>
        println(s"Error retrieving beliefs from neighbors: $exception")
    }
  }

  def generateInfluences(): Unit = {
    val random = new Random
    val randomNumbers = Vector.fill(neighbors.size + 1)(random.nextDouble())
    val sum = randomNumbers.sum
    influences = randomNumbers.map(_ / sum)
  }

  def snapshotAgentState(): Unit = {
    agentDataSaver ! SendAgentData(round, self.path.name, belief, speaking)
  }
}
