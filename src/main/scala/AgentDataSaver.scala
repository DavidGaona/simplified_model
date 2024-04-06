import akka.actor.{Actor, ActorRef}

case class AgentData
(
  round: Int,
  agentName: String,
  belief: Double,
  isSpeaking: Boolean
)
case object AnalysisComplete

class AgentDataSaver(dataSavingPath: String) extends Actor {
  var data = Vector.empty[AgentData]
  val saveThreshold = 125000 // Every ~5.5 MB save
  val path = s"$dataSavingPath/${context.parent.path.name}.csv"

  def exportData(): Unit = {
    saveToCsv(
      path,
      "round,agentName,belief,isSpeaking",
      data,
      d => s"${d.round},${d.agentName},${d.belief},${d.isSpeaking}"
    )
    data = Vector.empty[AgentData]
  }

  def receive: Receive = {
    case SendAgentData(round, name, belief, isSpeaking) =>
      data = data :+ AgentData(round, name, belief, isSpeaking)
      if (data.length >= saveThreshold) exportData()

    case SaveAgentsState =>
      exportData()
      val monitor = context.system.actorSelection("akka://simplified/user/Monitor")
      monitor ! AnalysisComplete
  }
}
