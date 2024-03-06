/*
 * Copyright (c) 2020 Couchbase, Inc.
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

// #tag::imports[]
import java.util.concurrent.TimeUnit

import com.couchbase.client.core.error._
import com.couchbase.client.scala._
import com.couchbase.client.scala.durability._
import com.couchbase.client.scala.json._
import com.couchbase.client.scala.kv.MutationResult

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
// #end::imports[]


object ErrorHandling {

  // #tag::cluster[]
  val cluster = Cluster.connect("localhost", "username", "password").get
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
      case Failure(err: DocumentNotFoundException) => println("Key not found")
      case Failure(exception) => println("Error: " + exception)
    }
    // #end::key-not-found[]
  }

  def keyExists() {
    // #tag::key-exists[]
    val json = JsonObject("foo" -> "bar", "baz" -> "qux")

    collection.insert("does-already-exist", json) match {
      case Success(_) => println("Successful")
      case Failure(err: DocumentExistsException) => println("Key already exists")
      case Failure(exception) => println("Error: " + exception)
    }
    // #end::key-exists[]
  }

  def get() {
    // #tag::get[]
    collection.get("document-key") match {
      case Success(result) =>
      case Failure(err: DocumentNotFoundException) => println("Key not found")
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

        case Failure(err: CasMismatchException) =>
          // Simply recursively retry until guard is hit
          if (guard != 0) doOperation(guard - 1)
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

        case Failure(err: DocumentExistsException) =>
          // The logic here is that if we failed to insert on the first attempt then
          // it's a true error, otherwise we retried due to an ambiguous error, and
          // it's ok to continue as the operation was actually successful
          if (guard == InitialGuard) Failure(err)
          else Success("ok!")

        // For ambiguous errors on inserts, simply retry them
        case Failure(err: DurabilityAmbiguousException) =>
          if (guard != 0) doInsert(docId, json, guard - 1)
          else Failure(err)

        case Failure(err: TimeoutException) =>
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
    def doInsert(docId: String,
                 json: JsonObject,
                 guard: Int = InitialGuard,
                 delay: Duration = Duration(10, TimeUnit.MILLISECONDS)): Try[String] = {
      val result = collection.insert(docId, json, durability = Durability.Majority)

      result match {

        case Success(value) => Success("ok!")

        case Failure(err: DocumentExistsException) =>
          // The logic here is that if we failed to insert on the first attempt then
          // it's a true error, otherwise we retried due to an ambiguous error, and
          // it's ok to continue as the operation was actually successful
          if (guard == InitialGuard) Failure(err)
          else Success("ok!")

        // Ambiguous errors.  The operation may or may not have succeeded.  For inserts,
        // the insert can be retried, and a DocumentExistsException indicates it was
        // successful.
        case Failure(_: DurabilityAmbiguousException)
             | Failure(_: TimeoutException)

             // Temporary/transient errors that are likely to be resolved
             // on a retry
             | Failure(_: TemporaryFailureException)
             | Failure(_: DurableWriteInProgressException)
             | Failure(_: DurableWriteReCommitInProgressException)

             // These transient errors won't be returned on an insert, but can be used
             // when writing similar wrappers for other mutation operations
             | Failure(_: CasMismatchException) =>

          if (guard != 0) {
            // Retry the operation after a sleep (which increases on each failure),
            // to avoid potentially further overloading an already failing server.
            Thread.sleep(delay.toMillis)
            doInsert(docId, json, guard - 1, delay * 2)
          }
          // Replace this CouchbaseException with your own
          else Failure(new CouchbaseException("Failed to insert " + docId))

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
      .map(_.rowsAs[JsonObject]) match {
      case Success(rows) =>
      case Failure(err) => println(s"Error: ${err}")
    }
    // #end::query[]
  }

}
