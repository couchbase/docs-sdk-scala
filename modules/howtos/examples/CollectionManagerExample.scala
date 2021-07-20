/*
 * Copyright (c) 2021 Couchbase, Inc.
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

package examples.collectionmanager
// tag::imports[]
import com.couchbase.client.scala._
import com.couchbase.client.scala.env._
import com.couchbase.client.scala.manager.user.{User, Role}
import com.couchbase.client.scala.manager.collection.CollectionSpec
import com.couchbase.client.core.error.{ScopeExistsException, ScopeNotFoundException}
import com.couchbase.client.core.error.{CollectionExistsException, CollectionNotFoundException}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
// end::imports[]

object CollectionManagerExample {
  def main(args: Array[String]): Unit = {

    val clusterTry = Cluster.connect("couchbase://localhost", "Administrator", "password")
    clusterTry match {
      case Success(cluster) =>
        collectionManagerExample(cluster)
      case Failure(err) =>
        println(s"Failed to open cluster: $err")
    }
  }

  def collectionManagerExample(cluster: Cluster): Unit = {
    println("scopeAdmin")
    // tag::scopeAdmin[]
    val users = cluster.users
    val user = User("scopeAdmin")
      .password("password")
      .roles(
        new Role("scope_admin", Some("travel-sample")),
        new Role("data_reader", Some("travel-sample")))
    users.upsertUser(user).get
    // end::scopeAdmin[]

    val clusterTry = Cluster.connect("couchbase://localhost", "scopeAdmin", "password")
    clusterTry match {
      case Success(cluster) =>
        val bucket = cluster.bucket("travel-sample")

        println("create-collection-manager")
        // tag::create-collection-manager[]
        val collectionMgr = bucket.collections;
        // end::create-collection-manager[]

        println("create-scope")
        // tag::create-scope[]
        collectionMgr.createScope("example-scope") match {
          case Success(_) =>
            println("Created scope OK")
          case Failure(err: ScopeExistsException) =>
            println("scope already exists")
          case Failure(err) =>
            println(err)
        }
        // end::create-scope[]

        println("create-collection");
        // tag::create-collection[]
        val spec = CollectionSpec("example-collection", "example-scope");

        collectionMgr.createCollection(spec)  match {
          case Success(_) =>
            println("Created collection OK")
          case Failure(err: ScopeNotFoundException) =>
            println("scope not found")
          case Failure(err: CollectionExistsException) =>
            println("collection already exists")
          case Failure(err) =>
            println(err)
        }
        // end::create-collection[]

        println("drop-collection");
        // tag::drop-collection[]
        collectionMgr.dropCollection(spec)
        match {
          case Success(_) =>
            println("Dropped collection OK")
          case Failure(err: ScopeNotFoundException) =>
            println("scope not found")
          case Failure(err: CollectionNotFoundException) =>
            println("collection not found")
          case Failure(err) =>
            println(err)
        }
        // end::drop-collection[]

        println("drop-scope");
        // tag::drop-scope[]
        collectionMgr.dropScope("example-scope")
        match {
          case Success(_) =>
            println("Dropped scope OK")
          case Failure(err: ScopeNotFoundException) =>
            println("scope not found")
          case Failure(err) =>
            println(err)
        }
        // end::drop-scope[]

        cluster.disconnect()
      case Failure(err) =>
        println(s"Failed to open cluster: $err")
    }

    cluster.disconnect()
  }
}
