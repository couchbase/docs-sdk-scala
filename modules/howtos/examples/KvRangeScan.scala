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

// tag::imports[]
import com.couchbase.client.scala._
import com.couchbase.client.scala.kv.{ScanOptions, ScanResult, ScanType}

import scala.util.{Failure, Success, Try}
// end::imports[]


object KvRangeScan {
  private val cluster = Cluster.connect("localhost", "Administrator", "password").get

  private val bucket = cluster.bucket("travel-sample")
  private val scope = bucket.scope("_default")
  private val collection = scope.collection("_default")

  def rangeScanAllDocuments() {
    // tag::rangeScanAllDocuments[]
    val results: Try[Iterator[ScanResult]] = collection.scan(ScanType.RangeScan(from = None, to = None))

    results match {
      case Success(value) =>
        value.foreach(scanResult => println(scanResult))
      case Failure(exception) =>
        println(s"Scan operation failed with ${exception}")
    }
    // end::rangeScanAllDocuments[]
  }

  def rangeScanPrefix() {
    // tag::rangeScanPrefix[]
    val results = collection.scan(ScanType.PrefixScan(prefix = "alice::"))
    // end::rangeScanPrefix[]
  }

  def rangeScanSample() {
    // tag::rangeScanSample[]
    val results = collection.scan(ScanType.SamplingScan(limit = 100))
    // end::rangeScanSample[]
  }

  def rangeScanAllDocumentIds() {
    // tag::rangeScanAllDocumentIds[]
    val results: Try[Iterator[ScanResult]] = collection.scan(ScanType.RangeScan(from = None, to = None),
      ScanOptions().idsOnly(true))

    results match {
      case Success(value) =>
        // Note only the id is present on each result - all other fields will be Option.None
        value.foreach(scanResult => println(scanResult.id))
      case Failure(exception) =>
        println(s"Scan operation failed with ${exception}")
    }
    // end::rangeScanAllDocumentIds[]
  }
}
