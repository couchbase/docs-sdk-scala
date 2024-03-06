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
import com.couchbase.client.scala.implicits.Codec
import com.couchbase.client.scala.json._
import com.couchbase.client.scala.kv.MutationState
import com.couchbase.client.scala.query._
import reactor.core.scala.publisher._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
// #end::imports[]

object Queries {
  def main(args: Array[String]): Unit = {

// #tag::cluster[]
    val cluster = Cluster.connect("localhost", "Administrator", "password").get
    val bucket = cluster.bucket("travel-sample")
    val collection = bucket.defaultCollection
// #end::cluster[]

    def simple() {
// #tag::simple[]
      val statement = """select * from `travel-sample` limit 10;"""
      val result: Try[QueryResult] = cluster.query(statement)
// #end::simple[]

// #tag::simple-results[]
      result match {
        case Success(result: QueryResult) =>
          result.rowsAs[JsonObject] match {
            case Success(rows) =>
              println(s"Got ${rows} rows")
            case Failure(err) => println(s"Error: $err")
          }
        case Failure(err) => println(s"Error: $err")
      }
// #end::simple-results[]
    }

    def getRows() {
// #tag::get-rows[]
      cluster
        .query("""select * from `travel-sample` limit 10;""")
        .flatMap(_.rowsAs[JsonObject]) match {
        case Success(rows: Seq[JsonObject]) =>
          rows.foreach(row => println(row))
        case Failure(err) =>
          println(s"Error: $err")
      }
// #end::get-rows[]
    }

    def caseClasses() {
// #tag::codec[]
      case class Address(line1: String)
      case class User(name: String, age: Int, addresses: Seq[Address])
      object User {
        implicit val codec: Codec[User] = Codec.codec[User]
      }
// #end::codec[]

// #tag::case-classes[]

      val statement =
        """select `users`.* from `users` limit 10;"""

      val users = cluster
        .query(statement)
        .flatMap(_.rowsAs[User]) match {
        case Success(rows: Seq[User]) =>
          rows.foreach(row => println(row))
        case Failure(err) =>
          println(s"Error: $err")
      }
// #end::case-classes[]
    }

    def positional() {
      // also demonstrates adhoc as per usage from concept-docs page
      // #tag::positional[]
      val stmt =
        """select count(*)
        from `travel-sample`.inventory.airport
        where country=$1;"""
      val result = cluster.query(
        stmt,
        QueryOptions()
          .adhoc(false)
          .parameters(QueryParameters.Positional("United States"))
      )
      // #end::positional[]
      println(result)
    }

    def named() {
// #tag::named[]
      val stmt =
        """select `travel-sample`.* from `travel-sample` where type=$type and country=$country limit 10;"""
      val result = cluster.query(
        stmt,
        QueryOptions().parameters(
          QueryParameters.Named(Map("type" -> "airline", "country" -> "United States"))
        )
      )
// #end::named[]
    }

    def requestPlus() {
// #tag::request-plus[]
      val result = cluster.query(
        "select `travel-sample`.* from `travel-sample` limit 10;",
        QueryOptions().scanConsistency(QueryScanConsistency.RequestPlus())
      )
// #end::request-plus[]
    }

    def atPlus() {
      val content = JsonObject.create
      // #tag::at-plus[]
      val result = collection.upsert("id", content)
        .flatMap(upsertResult => {
          val ms = MutationState.from(upsertResult)

          cluster.query(
            "select `travel-sample`.* from `travel-sample` limit 10;",
              QueryOptions().scanConsistency(QueryScanConsistency.ConsistentWith(ms))
          )
        })

      result match {
        case Success(_) =>
        case Failure(err) => println(s"Operation failed with error $err")
      }
      // #end::at-plus[]
    }

    def async() {
// #tag::async[]
// When we work with Scala Futures an ExecutionContext must be provided.
// For this example we'll just use the global default
      import scala.concurrent.ExecutionContext.Implicits.global

      val stmt = """select `travel-sample`.* from `travel-sample` limit 10;"""
      val future: Future[QueryResult] = cluster.async.query(stmt)

      future onComplete {
        case Success(result) =>
          result.rowsAs[JsonObject] match {
            case Success(rows) => rows.foreach(println(_))
            case Failure(err)  => println(s"Error: $err")
          }
        case Failure(err) => println(s"Error: $err")
      }
// #end::async[]
    }

    def reactive() {
// #tag::reactive[]
      val stmt = """select `travel-sample`.* from `travel-sample`;"""
      val mono: SMono[ReactiveQueryResult] = cluster.reactive.query(stmt)

      val rows: SFlux[JsonObject] = mono
      // ReactiveQueryResult contains a rows: Flux[QueryRow]
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

    println("Running examples...")

    positional()

    println("DONE")

  }
}
