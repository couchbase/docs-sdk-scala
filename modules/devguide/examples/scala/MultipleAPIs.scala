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
import java.util.NoSuchElementException
import java.util.concurrent.{Executors, ThreadFactory}

import com.couchbase.client.core.error._
import com.couchbase.client.scala._
import com.couchbase.client.scala.json._
import com.couchbase.client.scala.kv.{GetResult, MutationResult}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
// #end::imports[]


object MultipleAPIs {

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

  def upsertSimple() {
    // #tag::upsert-simple[]
    val json = JsonObject("foo" -> "bar", "baz" -> "qux")

    val result = collection.upsert("document-key", json)
    // #end::upsert-simple[]
  }

  def upsert() {
    // #tag::upsert[]
    val json = JsonObject("foo" -> "bar", "baz" -> "qux")

    collection.upsert("document-key", json) match {
      case Success(_) => println("Successfully upserted")
      case Failure(exception) => println("Error: " + exception)
    }
    // #end::upsert[]
  }

  def get() {
    // #tag::get[]
    // Create some initial JSON
    val json = JsonObject("status" -> "awesome!")

    // Upsert it
    collection.upsert("document-key", json) match {
      case Success(result) =>
      case Failure(err) => println("Error: " + err)
    }

    // Get it back
    collection.get("document-key") match {
      case Success(result) =>

        // Convert the content to a JsonObjectSafe
        result.contentAs[JsonObjectSafe] match {
          case Success(json) =>

            // Pull out the JSON's status field, if it exists
            json.str("status") match {
              case Success(status) => println(s"Couchbase is $status")
              case _ => println("Field 'status' did not exist")
            }
          case Failure(err) => println("Error decoding result: " + err)
        }
      case Failure(err) => println("Error getting document: " + err)
    }
    // #end::get[]
  }

  def getMap() {
    // #tag::get-map[]
    val json = JsonObject("status" -> "awesome!")

    val result: Try[String] = collection.upsert("document-key", json)
      .flatMap(_ => collection.get("document-key"))
      .flatMap(_.contentAs[JsonObjectSafe])
      .flatMap(_.str("status"))

    result match {
      case Success(status) => println(s"Couchbase is $status")
      case Failure(err) =>    println("Error: " + err)
    }
    // #end::get-map[]
  }

  // #tag::async-import[]
  val threadPool = Executors.newCachedThreadPool(new ThreadFactory {
    override def newThread(runnable: Runnable): Thread = {
      val thread = new Thread(runnable)
      // Make it a daemon thread so it doesn't block app exit
      thread.setDaemon(true)
      thread.setName("my-thread-prefix-" + thread.getId)
      thread
    }
  })
  implicit val ec = ExecutionContext.fromExecutor(threadPool)
  // #end::async-import[]


  def upsertAsync() {
    // #tag::upsert-async[]
    val json = JsonObject("foo" -> "bar", "baz" -> "qux")

    val result: Future[MutationResult] = collection.async.upsert("document-key", json)

    result onComplete {
      case Success(_)         => println("Successfully upserted")
      case Failure(exception) => println("Error: " + exception)
    }
    // #end::upsert-async[]
  }

  def getAsync() {
    // #tag::get-async[]
    val json = JsonObject("foo" -> "bar", "baz" -> "qux")

    val result: Future[String]   = collection.async.upsert("document-key", json)
      .flatMap(_                => collection.async.get("document-key"))
      .map((v: GetResult)       => v.contentAs[JsonObject])
      .map((v: Try[JsonObject]) => v.get)
      .map((v: JsonObject)      => v.str("status"))

    result onComplete {
      case Success(status) => println(s"Status ${status}")
      case Failure(err: DocumentNotFoundException) => println("Doc not found")
      case Failure(err: NoSuchElementException) => println("JSON not in expected format")
      case Failure(exception) => println("Error: " + exception)
    }
    // #end::get-async[]
  }

  def upsertReactive() {
    // #tag::upsert-reactive[]
    val json = JsonObject("foo" -> "bar", "baz" -> "qux")

    collection.reactive.upsert("document-key", json)
        .doOnError(err  => println(s"Error during upsert: ${err}"))
        .doOnNext(_     => println("Success"))
        .subscribe()
    // #end::upsert-reactive[]
  }

  def upsertReactiveBlocking() {
    // #tag::upsert-reactive-block[]
    val json = JsonObject("foo" -> "bar", "baz" -> "qux")

    val result: MutationResult = collection.reactive.upsert("document-key", json)
      .doOnError(err => println(s"Error during upsert: ${err}"))
      .doOnNext(mutationResult => println("Success"))
      .block()
    // #end::upsert-reactive-block[]
  }

  def getReactive() {
    // #tag::get-reactive[]
    val json = JsonObject("foo" -> "bar", "baz" -> "qux")

    // This example is written in a verbose style for clarity
    collection.reactive.upsert("document-key", json)
      .flatMap(_                  => collection.reactive.get("document-key"))
      .map((v: GetResult)         => v.contentAs[JsonObject])
      .map((v: Try[JsonObject])   => v.get)
      .map((v: JsonObject)        => v.str("status"))

      .doOnError {
        case err: DocumentNotFoundException => println("Doc not found")
        case err: NoSuchElementException => println("JSON not in expected format")
        case err => println(s"Error: ${err}")
      }

      .subscribe()
    // #end::get-reactive[]
  }
}
