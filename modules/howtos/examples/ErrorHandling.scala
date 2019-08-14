// #tag::imports[]
import java.util.NoSuchElementException
import java.util.concurrent.{Executors, ThreadFactory}

import com.couchbase.client.core.error._
import com.couchbase.client.scala._
import com.couchbase.client.scala.api.MutationResult
import com.couchbase.client.scala.codec.Conversions.Codec
import com.couchbase.client.scala.durability.Durability.Majority
import com.couchbase.client.scala.durability._
import com.couchbase.client.scala.implicits.Codecs
import com.couchbase.client.scala.json._
import com.couchbase.client.scala.query.QueryError
import reactor.core.scala.publisher.Mono
import reactor.core.scala.scheduler.ExecutionContextScheduler

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
// #end::imports[]


object ErrorHandling {

  // #tag::cluster[]
  val cluster = Cluster.connect("localhost", "username", "password")
  // #end::cluster[]

  // #tag::resources[]
  val bucket = cluster.bucket("bucket-name")
  val scope = bucket.scope("scope-name")
  val collection = scope.collection("collection-name")
  // #end::resources[]

  // #tag::apis[]
  val asyncApi: AsyncCollection = collection.async
  val reactiveApi: ReactiveCollection = collection.reactive
  // #end::apis[]

  def keyDoesNotExist() {
    // #tag::key-not-found[]
    val json = JsonObject("foo" -> "bar", "baz" -> "qux")

    collection.replace("does-not-exist", json) match {
      case Success(_) => println("Successful")
      case Failure(err: KeyNotFoundException) => println("Key not found")
      case Failure(exception) => println("Error: " + exception)
    }
    // #end::key-not-found[]
  }

  def keyExists() {
    // #tag::key-exists[]
    val json = JsonObject("foo" -> "bar", "baz" -> "qux")

    collection.insert("does-already-exist", json) match {
      case Success(_) => println("Successful")
      case Failure(err: KeyExistsException) => println("Key already exists")
      case Failure(exception) => println("Error: " + exception)
    }
    // #end::key-exists[]
  }

  def get() {
    // #tag::get[]
    collection.get("document-key") match {
      case Success(result) =>
      case Failure(err: KeyNotFoundException) => println("Key not found")
      case Failure(err) => println("Error getting document: " + err)
    }
    // #end::get[]
  }


  def cas() {
    val newJson: JsonObject = null

    // #tag::cas[]
    def doOperation(guard: Int = 3): Try[MutationResult] = {
      collection.get("doc")
        .flatMap(doc => collection.replace(doc.id, newJson, cas = doc.cas)) match {

        case Success(value) => Success(value)

        case Failure(err: CASMismatchException) =>
          // Simply recursively retry until guard is hit
          if (guard == 0) doOperation(guard - 1)
          else Failure(err)

        case Failure(exception) => Failure(exception)
      }
    }
    // #end::cas[]
  }

  def insert() {
    val json: JsonObject = null
    val InitialGuard = 3

    // #tag::insert[]
    def doInsert(docId: String, json: JsonObject, guard: Int = InitialGuard): Try[String] = {
      val result = collection.insert(docId, json, durability = Durability.Majority)

      result match {

        case Success(value) => Success("ok!")

        case Failure(err: KeyExistsException) =>
          // The logic here is that if we failed to insert on the first attempt then
          // it's a true error, otherwise we retried due to an ambiguous error, and
          // it's ok to continue as the operation was actually successful
          if (guard == InitialGuard) Failure(err)
          else Success("ok!")

        // For ambiguous errors on inserts, simply retry them
        case Failure(err: DurabilityAmbiguousException) =>
          if (guard != 0) doInsert(docId, json, guard - 1)
          else Failure(err)

        case Failure(err: RequestTimeoutException) =>
          if (guard != 0) doInsert(docId, json, guard - 1)
          else Failure(err)

        case Failure(err) => Failure(err)
      }
    }
    // #end::insert[]
  }

  def insertRealWorld() {
    val InitialGuard = 3

    // #tag::insert-real[]
    def doInsert(docId: String, json: JsonObject, guard: Int = InitialGuard): Try[String] = {
      val result = collection.insert(docId, json, durability = Durability.Majority)

      result match {

        case Success(value) => Success("ok!")

        case Failure(err: KeyExistsException) =>
          // The logic here is that if we failed to insert on the first attempt then
          // it's a true error, otherwise we retried due to an ambiguous error, and
          // it's ok to continue as the operation was actually successful
          if (guard == InitialGuard) Failure(err)
          else Success("ok!")

        // Ambiguous errors
        case Failure(_: DurabilityAmbiguousException)
             | Failure(_: RequestTimeoutException)

             // Temporary/transient errors that are likely to be resolved
             // on a retry
             | Failure(_: TemporaryFailureException)

             // These transient errors won't be returned on an insert, but can be used
             // when writing similar wrappers for other mutation operations
             | Failure(_: CASMismatchException)
             | Failure(_: LockException) =>

          // Retry the operation.  Our retry strategy is very simple here, and a
          // production application may want to try something more sophisticated,
          // such as exponential back-off.
          if (guard != 0) doInsert(docId, json, guard - 1)
          // Replace this RuntimeException with your own
          else Failure(new RuntimeException("Failed to insert " + docId))

        // Other errors, propagate up
        case Failure(err) => Failure(err)
      }
    }
    // #end::insert-real[]
  }

  def query() {
    // #tag::query[]
    val stmt =
      """select * from `travel-sample` limit 10;"""
    cluster.query(stmt)
      .map(_.allRowsAs[JsonObject]) match {
      case Success(rows) =>
      case Failure(err: QueryError) => println(s"Query error: ${err.msg}")
      case Failure(err) => println(s"Error: ${err}")
    }
    // #end::query[]
  }

}
