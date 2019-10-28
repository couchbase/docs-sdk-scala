/*
 * Copyright (c) 2019 Couchbase, Inc.
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
import com.couchbase.client.scala.json._
import com.couchbase.client.scala.view.{ViewOptions, ViewResult}

import scala.util.{Failure, Success, Try}
// #end::imports[]

class Views {
  // #tag::cluster[]
  val cluster = Cluster.connect("localhost", "username", "password").get
  // #end::cluster[]

  // #tag::resources[]
  val bucket = cluster.bucket("bucket-name")
  val scope = bucket.scope("scope-name")
  val collection = scope.collection("collection-name")
  // #end::resources[]

  def basic(): Unit = {
    // #tag::basic[]
    val result: Try[ViewResult] = bucket.viewQuery("design-doc", "view-name")

    result match {
      case Success(result) =>
        result.rows.foreach(row => {
          val key = row.keyAs[String]
          val value = row.valueAs[JsonObject]
          println(s"Row: ${key} = ${value}")
        })
      case Failure(err) => println(s"Failure: ${err}")
    }
    // #end::basic[]
  }

  def byName(): Unit = {
    // #tag::byName[]
    val result: Try[ViewResult] = bucket.viewQuery("beers", "by_name",
      ViewOptions().startKey("A").limit(10))
    // #end::byName[]
  }

  def travel(): Unit = {
    // #tag::travel[]
    val result: Try[ViewResult] = bucket.viewQuery("landmarks", "by_name",
      ViewOptions().key("<landmark-name>"))
    // #end::travel[]
  }

}
