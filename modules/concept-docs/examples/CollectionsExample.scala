import com.couchbase.client.scala.Cluster

import scala.util.{Failure, Success, Try}
import scala.concurrent.duration.Duration

object CollectionsExample {
  val cluster = Cluster.connect("localhost", "Administrator", "password").get
  val bucket = cluster.bucket("travel-sample")
  var collection = bucket.defaultCollection

  def main(args: Array[String]): Unit = {

    // tag::collections_1[]
    collection = bucket.collection("bookings") // in default scope
    // end::collections_1[]

    // tag::collections_2[]
    collection = bucket.scope("tenant_agent_00").collection("bookings")
    // end::collections_2[]

    println("DONE")
  }
}
