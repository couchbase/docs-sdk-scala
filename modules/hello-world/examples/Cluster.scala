import java.util.UUID

import com.couchbase.client.scala.Cluster
import com.couchbase.client.scala.json.JsonObject

import scala.util.{Failure, Success}

class Cluster {
  // #tag::cluster[]
  // TODO .get will probably go
  val cluster = Cluster.connect("10.112.180.101", "username", "password").get
  // #end::cluster[]

  // #tag::resources[]
  // TODO .gets will probably go
  val bucket     = cluster.bucket("bucket-name").get
  val scope      = bucket.scope("scope-name").get
  val collection = scope.collection("collection-name").get
  // #end::resources[]

  // #tag::json[]
  val json = JsonObject.create.put("status", "awesome")
  // #end::json[]

  // #tag::upsert[]
  val docId = UUID.randomUUID().toString
  collection.upsert(docId, json) match {
    case Success(result)    =>
    case Failure(exception) => println("Error: " + exception)
  }
  // #end::upsert[]

  // #tag::get[]
  // Get a document
  collection.get(docId) match {
    case Success(result) =>

      // Convert the content to a JsonObject
      result.contentAs[JsonObject] match {
        case Success(json) =>

          // Pull out the JSON's status field, if it exists
          json.strTry("status") match {
            case Success(hello) => println(s"Couchbase is $hello")
            case _              => println("Field 'status' did not exist")
          }
        case Failure(err) => println("Error decoding result: " + err)
      }
    case Failure(err) => println("Error getting document: " + err)
  }
  // #end::get[]

  // #tag::get-for[]
  (for {
    result <- collection.get(docId)
    json   <- result.contentAs[JsonObject]
    status <- json.strTry("status")
  } yield status) match {
    case Success(status) => println(s"Couchbase is $status")
    case Failure(err)    => println("Error: " + err)
  }
  // #end::get-for[]

  // #tag::get-map[]
  collection.get(docId)
    .flatMap(_.contentAs[JsonObject])
    .flatMap(_.strTry("status")) match {
    case Success(status) => println(s"Couchbase is $status")
    case Failure(err)    => println("Error: " + err)
  }
  // #end::get-map[]
}
