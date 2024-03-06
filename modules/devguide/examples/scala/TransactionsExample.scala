/*
 * Copyright (c) 2023 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// tag::imports[]
import TransactionsExample.{calculateLevelForExperience, handleTransactionResult}
import com.couchbase.client.core.cnc.events.transaction._
import com.couchbase.client.core.env.PasswordAuthenticator
import com.couchbase.client.core.error.DocumentNotFoundException
import com.couchbase.client.core.msg.kv.DurabilityLevel
import com.couchbase.client.scala.env.ClusterEnvironment
import com.couchbase.client.scala.json.JsonObject
import com.couchbase.client.scala.query.QueryParameters.Positional
import com.couchbase.client.scala.query.QueryProfile
import com.couchbase.client.scala.transactions.config.{TransactionOptions, TransactionsCleanupConfig, TransactionsConfig}
import com.couchbase.client.scala.transactions.error.{TransactionCommitAmbiguousException, TransactionFailedException}
import com.couchbase.client.scala.transactions._
import com.couchbase.client.scala.{Cluster, ClusterOptions, Collection, json}
import io.opentelemetry.api.trace.Span
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

import java.util.logging.Logger
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
// end::imports[]

object TransactionsExample {
  val logger: Logger = Logger.getLogger(classOf[TransactionsExample].getName)
  var cluster: Cluster = null
  var collection: Collection = null

  def main(args: String*): Unit = {
    // tag::init[]
    // Initialize the Couchbase cluster
    val cluster = Cluster.connect("localhost", "username", "password").get
    val bucket = cluster.bucket("travel-sample")
    val scope = bucket.scope("inventory")
    val collection = scope.collection("airport")
    // end::init[]
    TransactionsExample.cluster = cluster
    TransactionsExample.collection = collection
    queryExamples()
  }

  def config(): Unit = {
    // tag::config[]

    val env = ClusterEnvironment.builder
      .transactionsConfig(TransactionsConfig()
        .durabilityLevel(DurabilityLevel.PERSIST_TO_MAJORITY)
        .cleanupConfig(TransactionsCleanupConfig()
          .cleanupWindow(30.seconds)))
      .build.get
    val cluster = Cluster.connect("hostname",
      ClusterOptions(PasswordAuthenticator.create("username", "password"), Some(env))).get

    // Use the cluster
    // ...

    // Shutdown
    cluster.disconnect()
    env.shutdown()
    // end::config[]
  }

  def createSimple(): Unit = {
    val doc1Content = JsonObject.create
    val doc2Content = JsonObject.create
    // tag::create-simple[]
    cluster.transactions.run((ctx: TransactionAttemptContext) => {
      ctx.insert(collection, "doc1", doc1Content).get
      val doc2 = ctx.get(collection, "doc2").get
      ctx.replace(doc2, doc2Content).get
      Success()
    }).get
    // end::create-simple[]
  }

  def create(): Unit = {
    // tag::create[]
    // tag::logging[]

    val result = cluster.transactions.run((ctx: TransactionAttemptContext) => {
      Success()
    })

    result match {
      case Failure(err: TransactionFailedException) =>
        logger.warning("Transaction did not reach commit point")
        err.logs.foreach(msg => logger.warning(msg.toString))

      case Failure(err: TransactionCommitAmbiguousException) =>
        logger.warning("Transaction possibly reached the commit point")
        err.logs.foreach(msg => logger.warning(msg.toString))

      case Success(_) =>
        logger.info("Transaction succeeded!")
    }
    // end::logging[]
    // end::create[]
  }

  def loggingReactive(): Unit = {
    // tag::async_logging[]
    cluster.reactive.transactions.run((ctx: ReactiveTransactionAttemptContext) => {
      SMono.just(())
    }).doOnError {
      case err: TransactionFailedException =>
        logger.warning("Transaction did not reach commit point")
        err.logs.foreach(msg => logger.warning(msg.toString))

      case err: TransactionCommitAmbiguousException =>
        logger.warning("Transaction possibly reached the commit point")
        err.logs.foreach(msg => logger.warning(msg.toString))

      case err =>
      // This will not happen as all errors derive from TransactionFailedException
    }.block()
    // end::async_logging[]
  }

  def loggingSucccess(): Unit = {
    // tag::logging-success[]
    val result = cluster.transactions.run((ctx: TransactionAttemptContext) => {
      Success()
    }).get
    result.logs.foreach((message: TransactionLogEvent) => logger.info(message.toString))
    // end::logging-success[]
  }

  def createReactive(): Unit = {
    // tag::createReactive[]
    val result = cluster.reactive.transactions.run((ctx: ReactiveTransactionAttemptContext) => {

      // 'ctx' is a TransactionAttemptContextReactive, providing asynchronous versions of the
      // TransactionAttemptContext methods.
      // Your transaction logic here: as an example, get and remove a doc
      ctx.get(collection.reactive, "document-id")
        .flatMap(doc => ctx.remove(doc))

    }).doOnError(e => logTransactionFailure(e)).block()
    // end::createReactive[]
  }

  def examples(): Unit = {
    // tag::examples[]
    val inventory = cluster.bucket("travel-sample").scope("inventory")

    val result = cluster.transactions.run((ctx: TransactionAttemptContext) => {

      // Inserting a doc:
      ctx.insert(collection, "doc-a", JsonObject.create).get

      // Getting documents:
      val docA = ctx.get(collection, "doc-a").get

      // Replacing a doc:
      val docB = ctx.get(collection, "doc-b").get
      val content = docB.contentAs[JsonObject].get
      content.put("transactions", "are awesome")
      ctx.replace(docB, content).get

      // Removing a doc:
      val docC = ctx.get(collection, "doc-c").get
      ctx.remove(docC).get

      // Performing a SELECT N1QL query against a scope:
      val qr = ctx.query(inventory, "SELECT * FROM hotel WHERE country = $1",
        TransactionQueryOptions().parameters(Positional("United Kingdom"))).get

      val rows = qr.rowsAs[JsonObject].get

      // Performing an UPDATE N1QL query on multiple documents, in the `inventory` scope:
      ctx.query(inventory, "UPDATE route SET airlineid = $1 WHERE airline = $2",
        TransactionQueryOptions().parameters(Positional("airline_137", "AF"))).get

      Success()
    })

    handleTransactionResult(result).get
    // end::examples[]
  }

  def examplesReactive(): Unit = {
    // tag::examplesReactive[]
    val inventory = cluster.bucket("travel-sample").scope("inventory").reactive
    val result = cluster.reactive.transactions.run((ctx: ReactiveTransactionAttemptContext) => {

      // Inserting a doc:
      ctx.insert(collection.reactive, "doc-a", JsonObject.create)

        // Getting documents:
        .`then`(ctx.get(collection.reactive, "doc-b")

          // Replacing a doc:
          .flatMap(docB => {
            val content = docB.contentAs[JsonObject].get
            content.put("transactions", "are awesome")
            ctx.replace(docB, content)
          }))

        // Removing a doc:
        .`then`(ctx.get(collection.reactive, "doc-c")
          .flatMap(doc => ctx.remove(doc)))

        // Performing a SELECT N1QL query against a scope:
        .`then`(ctx.query(inventory, "SELECT * FROM hotel WHERE country = $1",
          TransactionQueryOptions().parameters(Positional("United Kingdom")))
          .flatMapMany(queryResult => SFlux.fromIterable(queryResult.rowsAs[JsonObject].get))
          .doOnNext(row => {
            // The application would do something with the rows here.
          })
          .collectSeq())

        // Performing an UPDATE N1QL query on multiple documents, in the `inventory` scope:
        .`then`(ctx.query(inventory, "UPDATE route SET airlineid = $1 WHERE airline = $2",
          TransactionQueryOptions().parameters(Positional("airline_137", "AF"))))

        .`then`()

    }).block()
    // end::examplesReactive[]
  }

  def insert(): Unit = {
    // tag::insert[]
    val result = cluster.transactions.run((ctx: TransactionAttemptContext) => {
      ctx.insert(collection, "docId", JsonObject.create).get
      Success()
    }).get
    // end::insert[]
  }

  def insertReactive(): Unit = {
    // tag::insertReactive[]
    cluster.reactive.transactions.run((ctx: ReactiveTransactionAttemptContext) => {
      ctx.insert(collection.reactive, "docId", JsonObject.create).`then`()
    }).block()
    // end::insertReactive[]
  }

  def getCatch(): Unit = {
    // tag::get-catch[]
    cluster.transactions.run((ctx: TransactionAttemptContext) => {
      ctx.get(collection, "a-doc") match {
        case Failure(_: DocumentNotFoundException) =>
          // By not propagating this failure, the application is allowing the transaction to continue.
        case _ =>
      }
      Success()
    }).get
    // end::get-catch[]
  }

  def get(): Unit = {
    // tag::get[]
    cluster.transactions.run((ctx: TransactionAttemptContext) => {
      val doc = ctx.get(collection, "a-doc").get
      Success()
    }).get
    // end::get[]
  }

  def getReadOwnWrites(): Unit = {
    // tag::getReadOwnWrites[]
    cluster.transactions.run((ctx: TransactionAttemptContext) => {
      val docId = "docId"
      ctx.insert(collection, docId, JsonObject.create).get
      val doc = ctx.get(collection, docId).get
      Success()
    }).get
    // end::getReadOwnWrites[]
  }

  def replace(): Unit = {
    // tag::replace[]
    cluster.transactions.run((ctx: TransactionAttemptContext) => {
      val doc = ctx.get(collection, "doc-id").get
      val content = doc.contentAs[JsonObject].get
      content.put("transactions", "are awesome")
      ctx.replace(doc, content).get
      Success()
    }).get
    // end::replace[]
  }

  def replaceReactive(): Unit = {
    // tag::replaceReactive[]
    cluster.reactive.transactions.run((ctx: ReactiveTransactionAttemptContext) => {
      ctx.get(collection.reactive, "doc-id")
        .flatMap(doc => {
          val content = doc.contentAs[JsonObject].get
          content.put("transactions", "are awesome")
          ctx.replace(doc, content)
        })
        .`then`()
    }).block()
    // end::replaceReactive[]
  }

  def remove(): Unit = {
    // tag::remove[]
    cluster.transactions.run((ctx: TransactionAttemptContext) => {
      val doc = ctx.get(collection, "doc-id").get
      ctx.remove(doc).get
      Success()
    }).get
    // end::remove[]
  }

  def removeReactive(): Unit = {
    // tag::removeReactive[]
    cluster.reactive.transactions.run((ctx: ReactiveTransactionAttemptContext) => {
      ctx.get(collection.reactive, "anotherDoc")
        .flatMap(doc => ctx.remove(doc))
    }).block()
    // end::removeReactive[]
  }

  def calculateLevelForExperience(experience: Int): Int = experience / 10

  // end::full[]
  def concurrency(): Unit = {
    // tag::concurrency[]
    cluster.env.core.eventBus.subscribe {
      case _: IllegalDocumentStateEvent =>
      // Do something with the event
      case _ =>
    }
    // end::concurrency[]
  }

  def cleanupEvents(): Unit = {
    // tag::cleanup-events[]
    cluster.env.core.eventBus.subscribe {
      case _: TransactionCleanupAttemptEvent | TransactionCleanupEndRunEvent =>
      // Do something with the event
      case _ =>
    }
    // end::cleanup-events[]
  }

  def rollbackCause(): Unit = {
    val costOfItem = 10
    // tag::rollback-cause[]
    class BalanceInsufficient extends RuntimeException {}

    cluster.transactions.run((ctx: TransactionAttemptContext) => {
      val customer = ctx.get(collection, "customer-name").get
      val customerBalance = customer.contentAs[json.JsonObject].get.num("balance")
      if (customerBalance < costOfItem) {
        Failure(new BalanceInsufficient())
      }
      else {
        Success()
      }
    }) match {
      case Failure(e: TransactionFailedException) =>
        e.getCause match {
          case _: BalanceInsufficient =>
          // Perform application logic here
          case _ => println(s"Transaction failed with ${e}")
        }
      case _ =>
    }
    // end::rollback-cause[]
  }

  def configExpiration(): Unit = {
    // tag::config-expiration[]
    val env = ClusterEnvironment.builder
      .transactionsConfig(TransactionsConfig().timeout(120.seconds))
      .build.get
    val cluster = Cluster.connect("hostname",
      ClusterOptions(PasswordAuthenticator.create("username", "password"), Some(env))).get
    // end::config-expiration[]
  }

  def configExpirationPer(): Unit = {
    // tag::config-expiration-per[]
    cluster.transactions.run((ctx: TransactionAttemptContext) => {
      Success()
    }, Some(TransactionOptions().timeout(60.seconds))).get
    // end::config-expiration-per[]
  }

  def configCleanup(): Unit = {
    // tag::config-cleanup[]
    val env = ClusterEnvironment.builder
      .transactionsConfig(TransactionsConfig()
        .cleanupConfig(TransactionsCleanupConfig()
          .cleanupClientAttempts(true)
          .cleanupLostAttempts(true)
          .cleanupWindow(120.seconds)
          .collections(Set(TransactionKeyspace("bucketName", Some("scopeName"), Some("collectionName"))))
        ))
      .build.get
    val cluster = Cluster.connect("hostname",
      ClusterOptions(PasswordAuthenticator.create("username", "password"), Some(env))).get
    // end::config-cleanup[]
  }

  def concurrentOps(): Unit = {
    // tag::concurrentOps[]
    val docIds = Seq("doc1", "doc2", "doc3", "doc4", "doc5")
    val concurrency = 100 // This many operations will be in-flight at once

    cluster.reactive.transactions.run((ctx: ReactiveTransactionAttemptContext) => {
      SFlux.fromIterable(docIds)
        .parallel(concurrency)
        .runOn(Schedulers.boundedElastic)
        .map(docId =>
          ctx.get(collection.reactive, docId)
            .flatMap(doc => {
              val content = doc.contentAs[JsonObject].get
              content.put("value", "updated")
              ctx.replace(doc, content)
            }))
        .sequential()
        .`then`
    }).block()
    // end::concurrentOps[]
  }

  def completeErrorHandling(): Unit = {
    // tag::full-error-handling[]
    val result = cluster.transactions.run((ctx: TransactionAttemptContext) => {
      Success()
    })

    result match {
      case Failure(e: TransactionCommitAmbiguousException) =>
        logger.info("Transaction possibly reached the commit point.  Logs:")
        e.logs.foreach(msg => logger.warning(msg.toString))
      case Failure(e: TransactionFailedException) =>
        logger.info("Transaction failed before reaching the commit point.  Logs:")
        e.logs.foreach(msg => logger.warning(msg.toString))
      case Success(value) =>
        // The transaction definitely reached the commit point.
        // Unstaging the individual documents may or may not have completed.
        if (!value.unstagingComplete) {
          logger.info("Transaction succeeded but may not have completed the commit process.  Cleanup will finish the process asynchronously.")
        }
        else {
          logger.info("Transaction succeeded")
        }
    }
    // end::full-error-handling[]
  }

  def queryExamples(): Unit = {
    // tag::queryExamplesSelect[]
    cluster.transactions.run((ctx: TransactionAttemptContext) => {
      val st = "SELECT * FROM `travel-sample`.inventory.hotel WHERE country = $1"
      val qr = ctx.query(st, TransactionQueryOptions().parameters(Positional("United Kingdom"))).get
      val rows = qr.rowsAs[JsonObject].get
      Success()
    }).get
    // end::queryExamplesSelect[]
    // tag::queryExamplesSelectScope[]
    val travelSample = cluster.bucket("travel-sample")
    val inventory = travelSample.scope("inventory")

    cluster.transactions.run((ctx: TransactionAttemptContext) => {
      val qr = ctx.query(inventory, "SELECT * FROM hotel WHERE country = $1",
        TransactionQueryOptions().parameters(Positional("United States"))).get
      val rows = qr.rowsAs[JsonObject].get
      Success()
    }).get
    // end::queryExamplesSelectScope[]
    // tag::queryExamplesUpdate[]
    val hotelChain = "http://marriot%"
    val country = "United States"

    cluster.transactions.run((ctx: TransactionAttemptContext) => {
      val qr = ctx.query(inventory, "UPDATE hotel SET price = $1 WHERE url LIKE $2 AND country = $3",
        TransactionQueryOptions().parameters(Positional((99.99, hotelChain, country)))).get
      assert(qr.metaData.metrics.get.mutationCount == 1)
      Success()
    }).get
    // end::queryExamplesUpdate[]
    // tag::queryExamplesComplex[]
    cluster.transactions.run((ctx: TransactionAttemptContext) => {

      // Find all hotels of the chain
      val qr = ctx.query(inventory, "SELECT reviews FROM hotel WHERE url LIKE $1 AND country = $2",
        TransactionQueryOptions().parameters(Positional(hotelChain, country))).get

      // This function (not provided here) will use a trained machine learning model to provide a
      // suitable price based on recent customer reviews.
      val updatedPrice = priceFromRecentReviews(qr)

      // Set the price of all hotels in the chain
      ctx.query(inventory, "UPDATE hotel SET price = $1 WHERE url LIKE $2 AND country = $3",
        TransactionQueryOptions().parameters(Positional(updatedPrice, hotelChain, country))).get

      Success()
    }).get
    // end::queryExamplesComplex[]
  }

  def priceFromRecentReviews(qr: TransactionQueryResult) = 1.0

  def queryInsert(): Unit = {
    // tag::queryInsert[]
    cluster.transactions.run((ctx: TransactionAttemptContext) => {
      ctx.query("INSERT INTO `default` VALUES ('doc', {'hello':'world'})").get // <1>

      // Performing a 'Read Your Own Write'
      val st = "SELECT `default`.* FROM `default` WHERE META().id = 'doc'" // <2>

      val qr = ctx.query(st).get
      assert(qr.metaData.metrics.get.resultCount == 1)

      Success()
    }).get
    // end::queryInsert[]
  }

  // tag::logger[]
  def handleTransactionResult(result: Try[TransactionResult]): Try[TransactionResult] = {
    result match {
      case Failure(err: TransactionFailedException) =>
        logger.warning("Transaction did not reach commit point")
        err.logs.foreach(msg => logger.warning(msg.toString))

      case Failure(err: TransactionCommitAmbiguousException) =>
        logger.warning("Transaction possibly reached the commit point")
        err.logs.foreach(msg => logger.warning(msg.toString))

      case Failure(err) =>
      // This will not happen as all errors derive from TransactionFailedException

      case Success(_) =>
        logger.info("Transaction succeeded!")
    }

    result
  }
  // end::logger[]

  def logTransactionFailure(e: Throwable): Unit = {
    e match {
      case err: TransactionFailedException =>
        logger.warning("Transaction did not reach commit point")
        err.logs.foreach(msg => logger.warning(msg.toString))

      case err: TransactionCommitAmbiguousException =>
        logger.warning("Transaction possibly reached the commit point")
        err.logs.foreach(msg => logger.warning(msg.toString))

      case err =>
      // This will not happen as all errors derive from TransactionFailedException
    }
  }

  def queryRyow(): Unit = {
    // tag::queryRyow[]
    cluster.transactions.run((ctx: TransactionAttemptContext) => {
      ctx.insert(collection, "doc", JsonObject.create.put("hello", "world")).get // <1>

      // Performing a 'Read Your Own Write'
      val st = "SELECT `default`.* FROM `default` WHERE META().id = 'doc'" // <2>

      val qr = ctx.query(st).get
      assert(qr.metaData.metrics.get.resultCount == 1)

      Success()
    }).get
    // end::queryRyow[]
  }

  def queryOptions(): Unit = {
    // tag::queryOptions[]
    cluster.transactions.run((ctx: TransactionAttemptContext) => {
      ctx.query("INSERT INTO `default` VALUES ('doc', {'hello':'world'})",
        TransactionQueryOptions().profile(QueryProfile.Timings)).get
      Success()
    }).get
    // end::queryOptions[]
  }

  def customMetadata(): Unit = {
    // tag::custom-metadata[]
    val env = ClusterEnvironment.builder
      .transactionsConfig(TransactionsConfig()
        .metadataCollection(TransactionKeyspace("bucketName", Some("scopeName"), Some("collectionName"))))
      .build.get
    val cluster = Cluster.connect("hostname",
      ClusterOptions(PasswordAuthenticator.create("username", "password"), Some(env))).get
    // end::custom-metadata[]
  }

  def customMetadataPer(): Unit = {
    // tag::custom-metadata-per[]
    cluster.transactions.run((ctx: TransactionAttemptContext) => {
      Success()
    }, Some(TransactionOptions().metadataCollection(collection)))
    // end::custom-metadata-per[]
  }

  def tracing(): Unit = {
    // #tag::tracing[]
    val span = cluster.env.core.requestTracer.requestSpan("your-span-name", null)
    cluster.transactions.run((ctx: TransactionAttemptContext) => {
      Success()
    }, Some(TransactionOptions().parentSpan(span)))
    // #end::tracing[]
  }

  def tracingWrapped(): Unit = {
    // #tag::tracing-wrapped[]
    val span = Span.current // this is a span created by your code earlier

    val wrapped = OpenTelemetryRequestSpan.wrap(span)
    cluster.transactions.run((ctx: TransactionAttemptContext) => {
      Success()
    }, Some(TransactionOptions().parentSpan(wrapped)))
    // #end::tracing-wrapped[]
  }
}

class TransactionsExample {
  // tag::full[]
  def playerHitsMonster(cluster: Cluster, collection: Collection, damage: Int, playerId: String, monsterId: String): Unit = {
    val result = cluster.transactions.run((ctx: TransactionAttemptContext) => {
      val monsterDoc = ctx.get(collection, monsterId).get
      val playerDoc = ctx.get(collection, playerId).get

      val monsterHitpoints = monsterDoc.contentAs[JsonObject].get.num("hitpoints")
      val monsterNewHitpoints = monsterHitpoints - damage

      if (monsterNewHitpoints <= 0) {
        // Monster is killed. The remove is just for demoing, and a more realistic
        // example would set a "dead" flag or similar.
        ctx.remove(monsterDoc).get

        // The player earns experience for killing the monster
        val experienceForKillingMonster = monsterDoc.contentAs[JsonObject].get.num("experienceWhenKilled")
        val playerExperience = playerDoc.contentAs[JsonObject].get.num("experience")
        val playerNewExperience = playerExperience + experienceForKillingMonster
        val playerNewLevel = calculateLevelForExperience(playerNewExperience)
        val playerContent = playerDoc.contentAs[JsonObject].get
        playerContent.put("experience", playerNewExperience)
        playerContent.put("level", playerNewLevel)

        ctx.replace(playerDoc, playerContent).get
      }
      else {
        // Monster is damaged but still alive
        val monsterContent = monsterDoc.contentAs[JsonObject].get
        monsterContent.put("hitpoints", monsterNewHitpoints)

        ctx.replace(monsterDoc, monsterContent).get
      }
      Success()
    })

    handleTransactionResult(result).get
  }
  // end::full[]
}
