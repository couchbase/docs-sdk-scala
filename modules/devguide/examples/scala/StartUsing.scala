/*
 * Copyright (c) 2024 Couchbase, Inc.
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

import com.couchbase.client.scala.durability.Durability
import com.couchbase.client.scala.env.{
  ClusterEnvironment,
  SecurityConfig,
  TimeoutConfig
}
import com.couchbase.client.scala.json.{JsonObject, JsonObjectSafe}
import com.couchbase.client.scala.kv.ReplaceOptions
import com.couchbase.client.scala.{Cluster, ClusterOptions}
import io.netty.handler.ssl.util.InsecureTrustManagerFactory

import java.nio.file.Path
import java.util.UUID
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object StartUsing {
  def main(args: Array[String]): Unit = {
    // tag::connect[]
    // Update this to your cluster
    val username = "Administrator"
    val password = "password"
    val bucketName = "travel-sample"

    val env = ClusterEnvironment.builder
      // You should uncomment this if running in a production environment.
      // .securityConfig(
      //   SecurityConfig()
      //     .enableTls(true)
      // )
      .build
      .get

    val cluster = Cluster
      .connect(
        // For a secure cluster connection, use `couchbases://<your-cluster-ip>` instead.
        "couchbase://localhost",
        ClusterOptions
          .create(username, password)
          .environment(env)
      )
      .get
    // end::connect[]

    val bucket = cluster.bucket(bucketName)
    bucket.waitUntilReady(30.seconds).get

    val collection = bucket.defaultCollection

    val json = JsonObject("status" -> "awesome")

    val docId = UUID.randomUUID().toString
    collection.upsert(docId, json) match {
      case Success(result)    =>
      case Failure(exception) => println("Error: " + exception)
    }

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

    def getFor() {
      val result: Try[String] = for {
        result <- collection.get(docId)
        json <- result.contentAs[JsonObjectSafe]
        status <- json.str("status")
      } yield status

      result match {
        case Success(status) => println(s"Couchbase is $status")
        case Failure(err)    => println("Error: " + err)
      }
    }

    def getMap() {
      val result: Try[String] = collection
        .get(docId)
        .flatMap(_.contentAs[JsonObjectSafe])
        .flatMap(_.str("status"))

      result match {
        case Success(status) => println(s"Couchbase is $status")
        case Failure(err)    => println("Error: " + err)
      }
    }

    def replaceOptions() {
      collection.replace(
        docId,
        json,
        ReplaceOptions()
          .expiry(10.seconds)
          .durability(Durability.Majority)
      ) match {
        case Success(status) =>
        case Failure(err)    => println("Error: " + err)
      }
    }

    def replaceNamed() {
      collection.replace(docId, json, durability = Durability.Majority) match {
        case Success(status) =>
        case Failure(err)    => println("Error: " + err)
      }
    }
  }
}
