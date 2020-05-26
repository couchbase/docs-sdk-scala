import com.couchbase.client.scala.Cluster

import scala.util.{Failure, Success, Try}
import scala.concurrent.duration.Duration

object N1qlQuery {
  val cluster = Cluster.connect("localhost", "Administrator", "password").get
  val bucket = cluster.bucket("default")
  val collection = bucket.defaultCollection


  def createIndex(): Unit = {
    // #tag::create-index[]
    val result: Try[Unit] =
      cluster.queryIndexes.createPrimaryIndex("users")

    result match {
      case Success(_) =>
      case Failure(err) => println(s"Operation failed with err $err")
    }

    // From now on the examples will use `.get` rather than correct error handling,
    // for brevity
    cluster.queryIndexes.createIndex("users", "index_name", Seq("name")).get
    cluster.queryIndexes.createIndex("users", "index_email", Seq("email")).get
    // #end::create-index[]
  }

  def buildIndex(): Unit = {
    // #tag::build-index[]
    cluster.queryIndexes.createPrimaryIndex("users", deferred = Some(true))
    cluster.queryIndexes.createIndex("users", "index_name",
      Seq("name"), deferred = Some(true)).get
    cluster.queryIndexes.createIndex("users", "index_email",
      Seq("email"), deferred = Some(true)).get
    cluster.queryIndexes.buildDeferredIndexes("users")

    // Wait for the indexes to build
    cluster.queryIndexes.watchIndexes("users",
      Seq("index_name", "index_email", "#primary"),
      Duration("30 seconds")).get

    // #end::build-index[]
  }
}
