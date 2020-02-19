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
import java.util.UUID

import com.couchbase.client.scala.Cluster
import com.couchbase.client.scala.durability.Durability
import com.couchbase.client.scala.json.{JsonObject, JsonObjectSafe}
import com.couchbase.client.scala.kv.ReplaceOptions

import scala.util.{Failure, Success, Try}
import concurrent.duration._
// #end::imports[]

object ClusterExample {
  def main(args: Array[String]) {
    // #tag::cluster[]
    val cluster = Cluster.connect("10.112.180.101", "username", "password").get
    // #end::cluster[]

    // #tag::resources[]
    val bucket = cluster.bucket("bucket-name")
    val collection = bucket.defaultCollection
    // #end::resources[]

    // #tag::json[]
    val json = JsonObject("status" -> "awesome")
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
        // Convert the content to a JsonObjectSafe
        result.contentAs[JsonObjectSafe] match {
          case Success(json) =>
            // Pull out the JSON's status field, if it exists
            json.str("status") match {
              case Success(hello) => println(s"Couchbase is $hello")
              case _              => println("Field 'status' did not exist")
            }
          case Failure(err) => println("Error decoding result: " + err)
        }
      case Failure(err) => println("Error getting document: " + err)
    }
    // #end::get[]

    def getFor() {
      // #tag::get-for[]
      val result: Try[String] = for {
        result <- collection.get(docId)
        json <- result.contentAs[JsonObjectSafe]
        status <- json.str("status")
      } yield status

      result match {
        case Success(status) => println(s"Couchbase is $status")
        case Failure(err)    => println("Error: " + err)
      }
      // #end::get-for[]
    }

    def getMap() {
      // #tag::get-map[]
      val result: Try[String] = collection
        .get(docId)
        .flatMap(_.contentAs[JsonObjectSafe])
        .flatMap(_.str("status"))

      result match {
        case Success(status) => println(s"Couchbase is $status")
        case Failure(err)    => println("Error: " + err)
      }
      // #end::get-map[]
    }

    def replaceOptions() {
      // #tag::replace-options[]
      collection
        .replace(docId, json, ReplaceOptions()
          .expiry(10.seconds)
          .durability(Durability.Majority)) match {
        case Success(status) =>
        case Failure(err)    => println("Error: " + err)
      }
      // #end::replace-options[]
    }

    def replaceNamed() {
      // #tag::replace-named[]
      collection.replace(docId, json, durability = Durability.Majority) match {
        case Success(status) =>
        case Failure(err)    => println("Error: " + err)
      }
      // #end::replace-named[]
    }

  }
}
