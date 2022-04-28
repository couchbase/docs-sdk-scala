import com.couchbase.client.core.error._
import com.couchbase.client.scala.Cluster
import com.couchbase.client.scala.manager.query.QueryIndexManager

import scala.util.{Failure, Success, Try}
import scala.concurrent.duration.Duration

object QueryIndexManagerExample {
  // tag::creating-index-mgr[]
  val cluster = Cluster.connect("localhost", "Administrator", "password").get
  val queryIndexMgr = cluster.queryIndexes
  // end::creating-index-mgr[]

  def createPrimaryIndex(): Unit = {
    println("[primary]")
    // tag::primary[]
    val result: Try[Unit] = queryIndexMgr.createPrimaryIndex(
      "travel-sample",
      // Set this if you wish to use a custom name
      // indexName = Option("custom_name"),
      scopeName = Option("tenant_agent_01"),
      collectionName = Option("users"),
      ignoreIfExists = true
    )
    result match {
      case Success(_)   =>
      case Failure(err) => throw err
    }
    // end::primary[]
  }

  def createIndex(): Unit = {
    println("[secondary]")
    // tag::secondary[]
    val result: Try[Unit] = queryIndexMgr.createIndex(
      "travel-sample",
      "tenant_agent_01_users_email",
      Array("preferred_email"),
      scopeName = Option("tenant_agent_01"),
      collectionName = Option("users")
    )
    result match {
      case Success(_) =>
      case Failure(err: IndexExistsException) => println("Index already exists!")
      case Failure(err) => throw err
    }
    // end::secondary[]
  }

  def deferAndWatchIndex(): Unit = {
    println("[defer-indexes]")
    // tag::defer-indexes[]
    // Create a deferred index
    var result: Try[Unit] = queryIndexMgr.createIndex(
      "travel-sample",
      "tenant_agent_01_users_phone",
      Array("preferred_phone"),
      scopeName = Option("tenant_agent_01"),
      collectionName = Option("users"),
      deferred = Option(true)
    )
    result match {
      case Success(_) =>
      case Failure(err: IndexExistsException) => println("Index already exists!")
      case Failure(err) => throw err
    }

    // Build any deferred indexes within `travel-sample`.tenant_agent_01.users
    result = queryIndexMgr.buildDeferredIndexes(
      "travel-sample",
      scopeName = Option("tenant_agent_01"),
      collectionName = Option("users")
    )
    result match {
      case Success(_)   =>
      case Failure(err) => throw err
    }

    // Wait for indexes to come online
    result = queryIndexMgr.watchIndexes(
      "travel-sample",
      Array("tenant_agent_01_users_phone"),
      Duration("30 seconds"),
      scopeName = Option("tenant_agent_01"),
      collectionName = Option("users")
    )
    result match {
      case Success(_) =>
      case Failure(err: IndexExistsException) => println("Index already exists!")
      case Failure(err) => throw err
    }
    // end::defer-indexes[]
  }

  def dropPrimaryAndSecondaryIndex(): Unit = {
    println("[drop-primary-or-secondary-index]")
    // tag::drop-primary-or-secondary-index[]
    // Drop a primary index
    var result: Try[Unit] = queryIndexMgr.dropPrimaryIndex(
      "travel-sample",
      scopeName = Option("tenant_agent_01"),
      collectionName = Option("users")
    )
    result match {
      case Success(_)   =>
      case Failure(err) => throw err
    }

    // Drop a secondary index
   result = queryIndexMgr.dropIndex(
      "travel-sample",
      "tenant_agent_01_users_email",
      scopeName = Option("tenant_agent_01"),
      collectionName = Option("users")
    )
    result match {
      case Success(_)   =>
      case Failure(err) => throw err
    }
    // end::drop-primary-or-secondary-index[]
  }

  def main(args: Array[String]): Unit = {
    createPrimaryIndex()
    createIndex()
    deferAndWatchIndex()
    dropPrimaryAndSecondaryIndex()
  }
}
