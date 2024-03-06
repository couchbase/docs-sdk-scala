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

import com.couchbase.client.core.error.CasMismatchException
import com.couchbase.client.scala.json.{JsonObject, JsonObjectSafe}
import com.couchbase.client.scala.kv.MutationResult
import com.couchbase.client.scala.{Cluster, Collection}

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object CAS {
  val cluster = Cluster.connect("localhost", "Administrator", "password").get
  val bucket = cluster.bucket("default")
  val collection = bucket.defaultCollection

  // #tag::loop[]
  def casLoop(collection: Collection,
              docId: String,
              guard: Int = 10): Try[MutationResult] = {
    val mutateResult = for {
      // Get the current document contents
      getResult  <- collection.get(docId)

      // Get the content
      content    <- getResult.contentAs[JsonObjectSafe]
      visitCount <- content.num("visitCount")

      // Modify the content (it's a mutable object)
      _          <- content.put("visitCount", visitCount + 1)

      // Try to replace the document, using CAS
      result     <- collection.replace(docId, content, getResult.cas)
    } yield result

    mutateResult match {
      // Succeeded, we're done
      case Success(_) => mutateResult

      // Failed on CAS
      case Failure(err: CasMismatchException) =>
        if (guard > 0) casLoop(collection, docId, guard - 1)
        else mutateResult

      // Something else went wrong, fast-fail
      case Failure(err) => mutateResult
    }
  }
  // #end::loop[]

  def lock(): Unit = {
    // #tag::locking[]
    collection.getAndLock("key", 10.seconds) match {
      case Success(lockedDoc) =>
        collection.unlock("key", lockedDoc.cas) match {
          case Success(_) =>
          case Failure(err) => println(s"Failed to unlock doc: $err")
        }
      case Failure(err) => println(s"Failed to get doc: $err")
    }
    // #end::locking[]
  }

}
