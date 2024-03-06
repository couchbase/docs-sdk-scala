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
import com.couchbase.client.scala.json.JsonObject
import com.couchbase.client.scala.kv.MutationState
import com.couchbase.client.scala.search.{SearchOptions, SearchScanConsistency}
import com.couchbase.client.scala.search.queries.MatchQuery
import com.couchbase.client.scala.search.result.{SearchResult, SearchRow}

import scala.util.{Failure, Success, Try}
// #end::imports[]

class FTS {
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
    val result: Try[SearchResult] = cluster.searchQuery("travel-sample-index-hotel-description",
      MatchQuery("swanky"),
      SearchOptions().limit(10))

    result match {
      case Success(res) =>
        val rows: Seq[SearchRow] = res.rows
        // handle rows
      case Failure(err) => println(s"Failure: ${err}")
    }
    // #end::basic[]
  }

  def results(): Unit = {
    // #tag::results[]
    val result: Try[SearchResult] = cluster.searchQuery("travel-sample-index-hotel-description",
      MatchQuery("swanky"),
      SearchOptions().limit(10))

    result match {
      case Success(res) =>

        // Rows
        res.rows.foreach(row => {
          val id: String = row.id
          val score: Double = row.score
          // ...
        })

        // MetaData
        val maxScore: Double = res.metaData.metrics.maxScore
        val successCount: Long = res.metaData.metrics.successPartitionCount

      case Failure(err) => println(s"Failure: ${err}")
    }
    // #end::results[]
  }

  def consistency(): Unit = {
    // #tag::consistency[]
    collection.insert("newHotel",
      JsonObject("name" -> "Hotel California", "desc" -> "Such a lonely place")) match {

      case Success(upsertResult) =>
        upsertResult.mutationToken.foreach(mutationToken => {

          val ms = MutationState(Seq(mutationToken))

          // Will wait until the the index contains the specified mutation
          val result = cluster.searchQuery(
            "travel-sample-index-hotel-description",
            MatchQuery("lonely"),
            SearchOptions()
              .limit(10)
              .scanConsistency(SearchScanConsistency.ConsistentWith(ms))
          )
        })

      case Failure(err) => println(s"Failure: ${err}")
    }
    // #end::consistency[]
  }

}
