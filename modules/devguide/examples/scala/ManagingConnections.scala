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
import com.couchbase.client.scala.env._
import com.couchbase.client.scala.kv.GetResult
import reactor.core.scala.publisher.SMono

import java.nio.file.Path
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
// #end::imports[]

class ManagingConnections {
  def initial(): Unit = {
    // #tag::initial[]
    val clusterTry: Try[Cluster] =
      Cluster.connect("127.0.0.1", "username", "password")

    clusterTry match {

      case Success(cluster) =>
        val bucket = cluster.bucket("beer-sample")
        val customerA = bucket.scope("customer-a")
        val widgets = customerA.collection("widgets")

        cluster.disconnect()

      case Failure(err) =>
        println(s"Failed to open cluster: $err")
    }
    // #end::initial[]
  }

  def waitUntilReady(): Unit = {
    // #tag::wait-until-ready[]
    val clusterTry: Try[Cluster] =
      Cluster.connect("127.0.0.1", "username", "password")

    clusterTry match {

      case Success(cluster) =>
        val bucket = cluster.bucket("beer-sample")

        bucket.waitUntilReady(30.seconds) match {
          case Success(_) =>
            val collection = bucket.defaultCollection
            // ... continue to use collection as normal ...

          case Failure(err) =>
            println(s"Failed to open bucket: $err")
        }

        cluster.disconnect()

      case Failure(err) =>
        println(s"Failed to open cluster: $err")
    }
    // #end::wait-until-ready[]
  }


  def multiple(): Unit = {
    // #tag::multiple[]
    val connectionString = "192.168.56.101,192.168.56.102"
    val cluster = Cluster.connect(connectionString, "username", "password")
    // #end::multiple[]

  }

  def env(): Unit = {
    // #tag::env[]
    val envTry: Try[ClusterEnvironment] = ClusterEnvironment.builder
      .timeoutConfig(TimeoutConfig().kvTimeout(10.seconds))
      .build

    envTry match {

      case Success(env) =>
        val clusterTry = Cluster.connect(
          "127.0.0.1",
          ClusterOptions
            .create("username", "password")
            .environment(env)
        )

        clusterTry match {
          case Success(cluster) =>
            // ... use the Cluster

            // Shutdown gracefully by shutting down the environment after
            // any Clusters using it
            cluster.disconnect()
            env.shutdown()

          case Failure(err) => println(s"Failed to open cluster: $err")
        }

      case Failure(err) => println(s"Failed to create environment: $err")
    }
    // #end::env[]
  }

  def doSomethingWithClusters(clusters: Cluster*): Try[Unit] = Success(Unit)

  def shared(): Unit = {

    // #tag::shared[]
    val result = for {
      env <- ClusterEnvironment.builder
        .timeoutConfig(TimeoutConfig().kvTimeout(10.seconds))
        .build

      clusterA <- Cluster.connect(
        "clusterA.example.com",
        ClusterOptions
          .create("username", "password")
          .environment(env)
      )

      clusterB <- Cluster.connect(
        "clusterB.example.com",
        ClusterOptions
          .create("username", "password")
          .environment(env)
      )

      result <- doSomethingWithClusters(clusterA, clusterB)

      _ <- Success(clusterA.disconnect())
      _ <- Success(clusterB.disconnect())
      _ <- Success(env.shutdown())
    } yield result

    result match {
      case Failure(err) => println(s"Failure: $err")
      case _            =>
    }
    // #end::shared[]
  }

  def seedNodes(): Unit = {

    // #tag::seed-nodes[]
    val customKvPort = 1234
    val customManagerPort = 2345
    val seedNodes = Set(
      SeedNode("127.0.0.1", Some(customKvPort), Some(customManagerPort))
    )

    val cluster = Cluster.connect(seedNodes, ClusterOptions.create("username", "password"))
    // #end::seed-nodes[]
  }

  def secure(): Unit = {
    // tag::secure[]
    val env = ClusterEnvironment.builder
       .securityConfig(
         SecurityConfig()
           .enableTls(true)
           .trustCertificate(Path.of("/path/to/cluster-root-certificate.pem"))
       )
      .build
      .get
    // end::secure[]
  }

  def blockingToAsync(): Unit = {
    // #tag::blocking-to-async[]
    Cluster.connect("127.0.0.1", "username", "password") match {
      case Success(cluster: Cluster) =>
        val bucket: Bucket = cluster.bucket("travel-sample")
        val async: AsyncBucket = bucket.async
        val reactive: ReactiveBucket = bucket.reactive

        val r1: Try[GetResult] = bucket.defaultCollection.get("id")
        val r2: Future[GetResult] = async.defaultCollection.get("id")
        val r3: SMono[GetResult] = reactive.defaultCollection.get("id")

        cluster.disconnect()

      case Failure(err) => println(s"Failure: $err")
    }
    // #end::blocking-to-async[]
  }

  def async(): Unit = {
    // #tag::async[]
    AsyncCluster.connect("127.0.0.1", "username", "password") match {
      case Success(cluster: AsyncCluster) =>
        val async: AsyncBucket = cluster.bucket("travel-sample")

        cluster.disconnect()

      case Failure(err) => println(s"Failure: $err")
    }
    // #end::async[]
  }

  def reactive(): Unit = {
    // #tag::reactive[]
    ReactiveCluster.connect("127.0.0.1", "username", "password") match {
      case Success(cluster: ReactiveCluster) =>
        val async: ReactiveBucket = cluster.bucket("travel-sample")

        cluster.disconnect()

      case Failure(err) => println(s"Failure: $err")
    }
    // #end::reactive[]
  }

}
