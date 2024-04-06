import akka.actor.{Actor, ActorRef, ActorSelection, Props}

case object BuildingComplete

case object RunningComplete

case object UpdateBelief

case object SaveAgentsState

class Network(numberOfAgents: Int, density: Int, degreeDistributionParameter: Double, dataSavingPath: String)
  extends Actor {
  val agents: Array[ActorRef] = Array.ofDim[ActorRef](numberOfAgents)
  val maxIterations: Int = 500
  val monitor: ActorRef = context.parent
  val agentDataSaver: ActorRef = context.actorOf(Props(new AgentDataSaver(dataSavingPath)))
  var pendingResponses = 0
  var round = 0

  def receive: Receive = building

  def createNewAgent(agentName: String): ActorRef = {
    context.actorOf(Props(new Agent(agentDataSaver)), agentName)
  }

  def building: Receive = {
    case BuildNetwork =>
      val fenwickTree = new FenwickTree(numberOfAgents, density, degreeDistributionParameter - 2)

      // Initialize the first n=density agents
      for (i <- 0 until density) {
        val newAgent = createNewAgent(s"Agent${i + 1}")
        agents(i) = newAgent
        for (j <- 0 until i) {
          agents(j) ! AddToNeighborhood(newAgent)
          newAgent ! AddToNeighborhood(agents(j))
        }
      }

      // Create and link the agents
      for (i <- density - 1 until numberOfAgents - 1) {
        // Create the new agent
        val newAgent = createNewAgent(s"Agent${i + 2}")
        agents(i + 1) = newAgent

        // Pick the agents based on their atractiveness score and link them
        val agentsPicked = fenwickTree.pickRandoms()
        agentsPicked.foreach { agent =>
          agents(agent) ! AddToNeighborhood(newAgent)
          newAgent ! AddToNeighborhood(agents(agent))
        }
      }
      context.become(running)
      monitor ! BuildingComplete
  }

  def running: Receive = {
    case RunNetwork =>
      pendingResponses = agents.length
      agents.foreach { agent => agent ! UpdateBelief }

    case BeliefUpdated =>
      pendingResponses -= 1

      if (pendingResponses == 0) {
        round += 1
        if (round <= maxIterations)
          self ! RunNetwork
        else
          monitor ! RunningComplete
          context.become(analyzing)
      }
  }

  def analyzing: Receive = {
    case StartAnalysis =>
      agentDataSaver ! SaveAgentsState
  }
}
