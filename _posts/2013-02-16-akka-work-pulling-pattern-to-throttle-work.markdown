---
layout: post
title: Akka work-pulling pattern to throttle work
date: 2013-02-16 09:06:44.000000000 +01:00
permalink: akka-work-pulling-pattern-to-throttle-work
tags: [scala, akka, pattern]
---
<strong>Update: please read my <a href="http://www.michaelpollmeier.com/akka-work-pulling-pattern/">updated article</a> on the same topic!</strong>

At <a href="http://www.movio.co">Movio</a> we're using <a href="http://akka.io/">Akka</a> in more and more places - we love the Actor model for concurrency. This week we ran into a problem that I think more projects will face: we've got an Actor that produces work very quickly (i.e. it just pulls records from the database which is really fast) and sends that work as individual messages to some (routed) Actors who do some long-time processing on those. Our problem was that the producer is creating work much faster than all the worker Actors together will be able to work off. Those messages would all be waiting in the worker's mailboxes, eventually eating up the complete heap space. I discussed this on the <a href="https://groups.google.com/forum/?fromgroups=#!topic/akka-user/A0_9lUFLc74">Akka usergroup</a> and quickly got some good responses. 

The solution we came up with is a simpler version of Derek Wyatt's <a href="http://letitcrash.com/post/29044669086/balancing-workload-across-nodes-with-akka-2">Balancing Workload Across Nodes with Akka</a> that's based on acknowledgements. We defined a master Actor who has access to some work - we called it Epic which is simply an Iterable[Work]. Worker Actors can run anywhere in your cluster and register with the master at any time. When the master get's a new Epic it informs all workers that there's work available. Those then pull piece by piece from the master until there's no more work left. In the end the master simply doesn't respond with work any more and the workers stop asking. 

Your concrete implementation of this just needs to take care of the actual implementation of the worker (what does it actually do with that piece of work?), the Epic (how does work get created?) and the supervisor strategy of the Actors (in our case we made the workers restart and the master resume, as we don't want it to loose the Epic it's currently working on. Our Epic just pulls a batch of work from the database and queues it in a local queue. Once that queue is empty it refills it until there's no more work left. 

This fits nicely with the Actor model - nothing is blocking, you can distribute your workers and don't need to worry about mailbox sizes. And here's the important parts of the implementation. Our <a href="https://github.com/movio/akka-patterns">github repo</a> contains the <a href="https://github.com/movio/akka-patterns/blob/master/src/main/scala/akkapatterns/WorkPullingPattern.scala">full implementation</a> and the <a href="https://github.com/movio/akka-patterns/blob/master/src/test/scala/akkapatterns/WorkPullingPatternTest.scala">test suite</a>. Simply clone it and run `sbt test`. 

```scala
object WorkPullingPattern {
  sealed trait Message
  trait Epic[T] extends Iterable[T] //used by master to create work
  case object GimmeWork extends Message
  case object CurrentlyBusy extends Message
  case object WorkAvailable extends Message
  case class RegisterWorker(worker: ActorRef) extends Message
  case class Work[T](work: T) extends Message
}

class Master[T] extends Actor {
  val log = LoggerFactory.getLogger(getClass)
  val workers = mutable.Set.empty[ActorRef]
  var currentEpic: Option[Epic[T]] = None

  def receive = {
    case epic: Epic[T] ?
      if (currentEpic.isDefined)
        sender ! CurrentlyBusy
      else if (workers.isEmpty)
        log.error("Got work but there are no workers registered.")
      else {
        currentEpic = Some(epic)
        workers foreach { _ ! WorkAvailable }
      }

    case RegisterWorker(worker) ?
      log.info(s"worker $worker registered")
      context.watch(worker)
      workers += worker

    case Terminated(worker) ?
      log.info(s"worker $worker died - taking off the set of workers")
      workers.remove(worker)

    case GimmeWork ? currentEpic match {
      case None ?
        log.info("workers asked for work but we've no more work to do")
      case Some(epic) ?
        val iter = epic.iterator
        if (iter.hasNext)
          sender ! Work(iter.next)
        else {
          log.info(s"done with current epic $epic")
          currentEpic = None
        }
    }
  }
}

abstract class Worker[T](val master: ActorRef) extends Actor {

  override def preStart {
    master ! RegisterWorker(self)
    master ! GimmeWork // keep working on actor restart
  }

  def receive = {
    case WorkAvailable ?
      master ! GimmeWork
    case Work(work: T) ?
      doWork(work)
      master ! GimmeWork
  }

  def doWork(work: T)
}
```


For future reference here's some alternative ideas - they all have caveats for the problem we faced, but might be considerable in other situations:
<ol>
	<li>Have thousands of worker actors: doesn't work for us because they depend on a database which is our actual bottleneck</li>
	<li>Use a bounded mailbox size for the worker actors. That blocks the producing Actor when sending even more messages to the workers. Sounds like what we needed, however it doesn't work with remote Actors: instead of blocking on a full mailbox it sends the message to the Deadletter Queue</li>
	<li>Use Derek Wyatt's <a href="https://github.com/derekwyatt/PressureQueue-Concept/">PressureQueue</a>. It's basically a custom mailbox for the worker Actors that delays the submission of new messages based on the mailbox size. I'm not convinced that it fits the Actor model, partly because it's blocking in producer Actor, which means that it can't react on other messages any more. Also it doesn't seem to be used widely and the last commit is 10 months ago.</li>
	<li>The producer could only pull the Ids from the database and we hope that those fit into memory - i.e. we'd have millions of IDs as messages floating around. The workers then fetch the complete record later on and slowly get the job done. This works as long as all the Ids fit into memory - beyond that point your JVM explodes ;)</li>
	<li>Use the <a href="http://letitcrash.com/post/28901663062/throttling-messages-in-akka-2">TimerBasedThrottler</a> which makes our producer only create X amount of work per time unit. The problem here is how do I get the X and the time unit? It's only ever going to be a rough guess, so I'm either missing out on performance (if my workers could do faster) or potentially running out of memory (if my workers can't catch up, e.g. because of other load on the system)
