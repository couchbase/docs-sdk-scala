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

import com.couchbase.client.core.error.BucketNotFlushableException
import com.couchbase.client.scala.Cluster
import com.couchbase.client.scala.manager.analytics.AnalyticsIndexManager
import com.couchbase.client.scala.manager.bucket.{BucketManager, BucketSettings, BucketType, ConflictResolutionType, CreateBucketSettings}
import com.couchbase.client.scala.manager.collection.CollectionManager
import com.couchbase.client.scala.manager.query.QueryIndexManager
import com.couchbase.client.scala.manager.search.SearchIndexManager
import com.couchbase.client.scala.manager.user.UserManager
import com.couchbase.client.scala.manager.view.{DesignDocument, View, ViewIndexManager}
import com.couchbase.client.scala.view.DesignDocumentNamespace

import scala.util.{Failure, Success, Try}

object ClusterResources {
  val cluster = Cluster.connect("localhost", "Administrator", "password").get
  val bucket = cluster.bucket("default")
  val collection = bucket.defaultCollection

  def managers(): Unit = {
    // #tag::managers[]
    val bucketManager: BucketManager = cluster.buckets
    val userManager: UserManager = cluster.users
    val queryIndexManager: QueryIndexManager = cluster.queryIndexes
    val analyticsIndexManager: AnalyticsIndexManager = cluster.analyticsIndexes
    val searchIndexManager: SearchIndexManager = cluster.searchIndexes
    val collectionManager: CollectionManager = bucket.collections
    val viewIndexManager: ViewIndexManager = bucket.viewIndexes
    // #end::managers[]
  }

  def bucketManager(): Unit = {
    // #tag::bucket[]
    val bucketManager: BucketManager = cluster.buckets

    val result: Try[Unit] = bucketManager.create(
      CreateBucketSettings("hello", ramQuotaMB = 1024)
        .flushEnabled(false)
        .replicaIndexes(false)
        .numReplicas(1)
        .bucketType(BucketType.Couchbase)
        .conflictResolutionType(ConflictResolutionType.SequenceNumber))

    result match {
      case Success(_) =>
      case Failure(err) => print(s"Failed with error $err")
    }
    // #end::bucket[]
  }

  def bucketManagerFlushEnable(): Unit = {
    val bucketManager: BucketManager = cluster.buckets

    // #tag::flush[]
    val result: Try[Unit] = bucketManager.getBucket("hello")
      .flatMap((bucket: BucketSettings) => {
        val updated = bucket.toCreateBucketSettings.flushEnabled(true)
        bucketManager.updateBucket(updated)
      })

    // Result error-checking omitted for brevity
    // #end::flush[]
  }


  def bucketManagerDrop(): Unit = {
    val bucketManager: BucketManager = cluster.buckets

    // #tag::drop[]
    val result: Try[Unit] = bucketManager.dropBucket("hello")

    // Result error-checking omitted for brevity
    // #end::drop[]
  }

  def bucketManagerFlush(): Unit = {
    val bucketManager: BucketManager = cluster.buckets

    // #tag::flush-real[]
    val result: Try[Unit] = bucketManager.flushBucket("hello")

    result match {
      case Success(_) =>
      case Failure(err: BucketNotFlushableException) =>
        print("Flushing not enabled on this bucket")
      case Failure(err) => print(s"Failed with error $err")
    }
    // #end::flush-real[]
  }

  def indexManagers(): Unit = {
    // #tag::index-managers[]
    val queryIndexManager: QueryIndexManager = cluster.queryIndexes
    val analyticsIndexManager: AnalyticsIndexManager = cluster.analyticsIndexes
    val searchIndexManager: SearchIndexManager = cluster.searchIndexes
    val viewIndexManager: ViewIndexManager = bucket.viewIndexes
    // #end::index-managers[]
  }

  def viewManager(): Unit = {
    // #tag::view-manager[]
    val viewIndexManager: ViewIndexManager = bucket.viewIndexes
    // #end::view-manager[]

    // #tag::view-upsert[]
    val view1 = View("function (doc, meta) { if (doc.type == 'landmark') { emit([doc.country, doc.city], null); } }")
    val view2 = View("function (doc, meta) { if (doc.type == 'landmark') { emit(doc.activity, null); } }", reduce = Some("_count"))

    val designDoc = DesignDocument("landmarks",
      Map("by_country" -> view1, "by_activity " -> view2))

    viewIndexManager.upsertDesignDocument(designDoc, DesignDocumentNamespace.Development)
    // #end::view-upsert[]
  }

  def viewGet(): Unit = {
    val viewIndexManager: ViewIndexManager = bucket.viewIndexes

    // #tag::view-get[]
    val designDoc: Try[DesignDocument] =
      viewIndexManager.getDesignDocument("landmarks", DesignDocumentNamespace.Development)
    // #end::view-get[]

    // #tag::view-push[]
    val publishResult = viewIndexManager.publishDesignDocument("landmarks")

    // Result error-handling omitted for brevity
    // #end::view-push[]

    // #tag::view-remove[]
    val dropResult = viewIndexManager.dropDesignDocument("landmarks", DesignDocumentNamespace.Production)
    // Result error-handling omitted for brevity
    // #end::view-remove[]
  }

}
