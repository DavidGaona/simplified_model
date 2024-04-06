import akka.actor.{Actor, ActorRef, Props}

import scala.util.control.Breaks._

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


def createRunDirectory(basePath: String): String = {
  val currentDateTime = LocalDateTime.now()
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_SSS")
  val formattedDateTime = currentDateTime.format(formatter)

  val dirName = s"run_$formattedDateTime"
  val fullPath = s"$basePath/$dirName"

  // Create the directory
  val directory = new File(fullPath)
  if (!directory.exists()) {
    val result = directory.mkdirs()
    if (result) {
      println(s"Directory created successfully at $fullPath")
    } else {
      println(s"Failed to create directory at $fullPath")
    }
  } else {
    println(s"Directory already exists at $fullPath")
  }
  fullPath
}

case object BuildNetwork
case object RunNetwork
case object StartAnalysis
case object RunBatch

class Monitor extends Actor {
  val dataSavingPath: String =  createRunDirectory("src/data/runs")
  val agentLimit = 1000000

  var networks: Vector[ActorRef] = Vector.empty

  var batches = 0
  var networksPerBatch = 0
  var curBatch = 0
  var numberOfNetworksFinished = 0


  def receive: Receive = {
    case AddNetworks(numberOfNetworks, numberOfAgents, density, degreeDistribution) =>
      val (batches, networksPerBatch) = calculateBatches(numberOfNetworks, numberOfAgents)
      this.batches = batches
      this.networksPerBatch = math.min(networksPerBatch, numberOfNetworks)
      for (i <- 0 until numberOfNetworks) {
        val network = context.actorOf(Props(
          new Network(numberOfAgents, density, degreeDistribution, dataSavingPath)),
          s"network${i + 1}"
        )
        networks = networks :+ network
      }
      self ! RunBatch

    case RunBatch =>
      for (i <- numberOfNetworksFinished until networksPerBatch + numberOfNetworksFinished) {
        if (i < networks.size) networks(i) ! BuildNetwork
      }
      curBatch += 1

    case BuildingComplete =>
      val network = sender()
      println(s"Finished Building ${network.path.name}")
      network ! RunNetwork

    case RunningComplete =>
      val network = sender()
      val networkName = network.path.name
      val networkNumber = "\\d+".r.findFirstIn(networkName).map(_.toInt).getOrElse(0)
      numberOfNetworksFinished += 1

      println(s"Finished Running ${network.path.name} $numberOfNetworksFinished $batches")
      if (numberOfNetworksFinished == batches * networksPerBatch) {
        networks.foreach(network => network ! StartAnalysis)
      } else if (numberOfNetworksFinished % networksPerBatch == 0) {
        self ! RunBatch
      }

    case AnalysisComplete =>
      println("Done!")

  }

  def calculateBatches(numberOfNetworks: Int, numberOfAgents: Int): (Int, Int) = {
    if (numberOfAgents >= agentLimit) {
      (numberOfNetworks, 1)
    } else {
      val networksPerBatch = agentLimit / numberOfAgents
      val batches = math.ceil(numberOfNetworks.toDouble / networksPerBatch).toInt
      (batches, networksPerBatch)
    }
  }
}
