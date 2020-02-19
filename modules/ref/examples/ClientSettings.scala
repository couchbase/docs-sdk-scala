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

import com.couchbase.client.core.env.NetworkResolution
import com.couchbase.client.core.retry.BestEffortRetryStrategy
import com.couchbase.client.scala.{Cluster, ClusterOptions}
import com.couchbase.client.scala.durability.Durability
import com.couchbase.client.scala.env._
import com.couchbase.client.scala.json.{JsonObject, JsonObjectSafe}
import com.couchbase.client.scala.kv.ReplaceOptions

import scala.util.{Failure, Success, Try}
import concurrent.duration._
// #end::imports[]

object ClusterSettings {
  def createEnv() {
// #tag::create-env[]
    val envTry: Try[ClusterEnvironment] = ClusterEnvironment.builder
    // Customize settings here
    .build

    envTry match {
      case Success(env) =>
        val clusterTry: Try[Cluster] = Cluster.connect(
          "localhost",
          ClusterOptions
            .create("username", "password")
            .environment(env)
        )

        clusterTry match {
          case Success(cluster) =>
            // Work with cluster

            // Shutdown gracefully
            cluster.disconnect()
            env.shutdown()

          case Failure(err) => println(s"Failed to connect to cluster: $err")
        }

      case Failure(err) => println(s"Failed to create environment: $err")
    }

// #end::create-env[]
  }

  def createEnvSimple() {
    // #tag::create-env-simple[]
    val clusterTry: Try[Cluster] = ClusterEnvironment.builder
    // Customize settings here
    .build
      .flatMap(
        env =>
          Cluster.connect(
            "localhost",
            ClusterOptions
              .create("username", "password")
              .environment(env)
        )
      )

    clusterTry match {
      case Success(cluster) =>
        // Work with cluster

        // Shutdown gracefully
        cluster.disconnect()
        cluster.env.shutdown()

      case Failure(err) => println(s"Failure: $err")
    }
    // #end::create-env-simple[]
  }

  def timeout() {
    // #tag::timeout[]
    ClusterEnvironment.builder
      .timeoutConfig(
        TimeoutConfig()
          .kvTimeout(5.seconds)
          .queryTimeout(10.seconds)
      )
      .build
    // #end::timeout[]
  }

  def systemProperties() {
    // #tag::system-properties[]
    System.setProperty("com.couchbase.env.timeout.kvTimeout", "10s") // <1>
    System.setProperty("com.couchbase.env.timeout.queryTimeout", "15s")

    ClusterEnvironment.builder
      .timeoutConfig(
        TimeoutConfig()
          .kvTimeout(5.seconds) // <2>
      )
      .build
    // #end::system-properties[]
  }

  def security() {
    // #tag::security[]
    ClusterEnvironment.builder
      .securityConfig(SecurityConfig().enableTls(true))
      .build
    // #end::security[]
  }

  def ioOptions() {
    // #tag::io-config[]
    ClusterEnvironment.builder
      .ioConfig(IoConfig().networkResolution(NetworkResolution.AUTO))
      .build
    // #end::io-config[]
  }

  def circuitBreaker() {
    // #tag::circuit-breaker[]
    ClusterEnvironment.builder
      .ioConfig(
        IoConfig()
          .kvCircuitBreakerConfig(
            CircuitBreakerConfig()
              .enabled(true)
              .volumeThreshold(45)
              .errorThresholdPercentage(25)
              .sleepWindow(1.seconds)
              .rollingWindow(2.minutes)
          )
      )
      .build
    // #end::circuit-breaker[]
  }

  def compression() {
    // #tag::compression[]
    ClusterEnvironment.builder
      .compressionConfig(CompressionConfig().enable(true))
      .build
    // #end::compression[]
  }

  def general() {
    // #tag::general[]
    ClusterEnvironment.builder
      .retryStrategy(BestEffortRetryStrategy.INSTANCE)
      .build
    // #end::general[]
  }

}
