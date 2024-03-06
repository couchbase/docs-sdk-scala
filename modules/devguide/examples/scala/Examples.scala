import com.couchbase.client.scala.Cluster
import com.couchbase.client.scala.kv.{LookupInMacro, LookupInResult, LookupInSpec}

import scala.util.Try

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

class Examples {
  val cluster = Cluster.connect("localhost", "Administrator", "password").get
  val bucket = cluster.bucket("default")
  val collection = bucket.defaultCollection

  // #tag::lookup[]
  val result: Try[LookupInResult] =
    collection.lookupIn("doc-id",
      Seq(LookupInSpec.get(LookupInMacro.ExpiryTime).xattr))
  // #end::lookup[]
}
