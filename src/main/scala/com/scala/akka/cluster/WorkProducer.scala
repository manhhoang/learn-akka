package com.scala.akka.cluster

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef}

import scala.concurrent.duration._
import scala.concurrent.forkjoin.ThreadLocalRandom

object WorkProducer {
  case object Tick
}

class WorkProducer(frontend: ActorRef) extends Actor with ActorLogging {
  import WorkProducer._
  import context.dispatcher
  def scheduler = context.system.scheduler
  def rnd = ThreadLocalRandom.current
  def nextWorkId(): String = UUID.randomUUID().toString

  var n = 0

  override def preStart(): Unit =
    scheduler.scheduleOnce(5.seconds, self, Tick)

  // override postRestart so we don't call preStart and schedule a new Tick
  override def postRestart(reason: Throwable): Unit = ()

  def receive = {
    case Tick =>
      n += 1
      log.info("Produced ID: {}", n)
      val work = Work(nextWorkId(), n)
      frontend ! work
      context.become(waitAccepted(work), discardOld = false)

  }

  def waitAccepted(work: Work): Actor.Receive = {
    case Frontend.Ok =>
      context.unbecome()
      scheduler.scheduleOnce(rnd.nextInt(3, 10).seconds, self, Tick)
    case Frontend.NotOk =>
      log.info("Work not accepted, retry after a while")
      scheduler.scheduleOnce(3.seconds, frontend, work)
  }

}