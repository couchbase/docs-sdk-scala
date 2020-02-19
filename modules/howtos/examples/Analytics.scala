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
import com.couchbase.client.scala._
import com.couchbase.client.scala.analytics.{AnalyticsOptions, AnalyticsParameters, AnalyticsResult, ReactiveAnalyticsResult}
import com.couchbase.client.scala.json._
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
// #end::imports[]


object Analytics {

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

  def simple() {
    // #tag::simple[]
    val query = """select "hello" as greeting;"""
    val result: Try[AnalyticsResult] = cluster.analyticsQuery(query)
    val rows: Try[Seq[JsonObject]] = result.flatMap(_.rowsAs[JsonObject])

    rows match {
      case Success(r: Seq[JsonObject]) => println(s"Row: ${r.head}")
      case Failure(err) => println(s"Failure ${err}")
    }
    // #end::simple[]
  }

  def simpleBetter() {
    // #tag::simple-better[]
    cluster.analyticsQuery("""select "hello" as greeting;""")
      .flatMap(_.rowsAs[JsonObject]) match {
      case Success(r)   => println(s"Row: ${r.head}")
      case Failure(err) => println(s"Failure ${err}")
    }
    // #end::simple-better[]
  }

  def parameterised() {
    // #tag::parameterised[]
    cluster.analyticsQuery(
      """select airportname, country from airports where country = ?;""",
      AnalyticsOptions().parameters(AnalyticsParameters.Positional("France")))
      .flatMap(_.rowsAs[JsonObject]) match {
      case Success(r)   => r.foreach(row => println(s"Row: ${row}"))
      case Failure(err) => println(s"Failure ${err}")
    }
    // #end::parameterised[]
  }

  def named() {
    // #tag::named[]
    cluster.analyticsQuery(
      """select airportname, country from airports where country = $country;""",
      AnalyticsOptions().parameters(AnalyticsParameters.Named(Map("country" -> "France"))))
      .flatMap(_.rowsAs[JsonObject]) match {
      case Success(r)   => r.foreach(row => println(s"Row: ${row}"))
      case Failure(err) => println(s"Failure ${err}")
    }
    // #end::named[]
  }

  def parameters() {
    // #tag::parameters[]
    cluster.analyticsQuery(
      """select airportname, country from airports where country = "France";""",
      AnalyticsOptions()
        // Ask the analytics service to give this request higher priority
        .priority(true)

        // The client context id is returned in the results, so can be used by the
        // application to correlate requests and responses
        .clientContextId("my-id")

        // Override how long the analytics query is allowed to take before timing out
        .timeout(90.seconds)
    ) match {
      case Success(r: AnalyticsResult) =>
        assert(r.metaData.clientContextId.contains("my-id"))
      case Failure(err) => println(s"Failure ${err}")
    }
  }
  // #end::parameters[]

  def metrics() {
    // #tag::metrics[]
    val stmt =
      """select airportname, country from airports where country = "France";"""
    cluster.analyticsQuery(stmt) match {
      case Success(result) =>
        val metrics = result.metaData.metrics
        println(s"Elapsed: ${metrics.elapsedTime}")
        println(s"Results: ${metrics.resultCount}")
        println(s"Errors:  ${metrics.errorCount}")
      case Failure(err) => println(s"Failure ${err}")
    }
    // #end::metrics[]

  }


  def async() {
    // #tag::async[]
    // When we work with Scala Futures an ExecutionContext must be provided.
    // For this example we'll just use the global default
    import scala.concurrent.ExecutionContext.Implicits.global

    val stmt = """select airportname, country from airports where country = "France";"""
    val future: Future[AnalyticsResult] = cluster.async.analyticsQuery(stmt)

    future onComplete {
      case Success(result) =>
        result.rowsAs[JsonObject] match {
          case Success(rows) => rows.foreach(println(_))
          case Failure(err) => println(s"Error: $err")
        }
      case Failure(err) => println(s"Error: $err")
    }
    // #end::async[]
  }

  def reactive() {
    // #tag::reactive[]
    val stmt =
      """select airportname, country from airports where country = "France";"""
    val mono: SMono[ReactiveAnalyticsResult] = cluster.reactive.analyticsQuery(stmt)

    val rows: SFlux[JsonObject] = mono
      // ReactiveQueryResult contains a rows: Flux[AnalyticsRow]
      .flatMapMany(result => result.rowsAs[JsonObject])

    // Just for example, block on the rows.  This is not best practice and apps
    // should generally not block.
    val allRows: Seq[JsonObject] = rows
      .doOnNext(row => println(row))
      .doOnError(err => println(s"Error: $err"))
      .collectSeq()
      .block()

    // #end::reactive[]
  }

}
