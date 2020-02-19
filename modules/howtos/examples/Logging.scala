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

import com.couchbase.client.core.cnc.Event
import com.couchbase.client.scala.{Cluster, ClusterOptions}
import com.couchbase.client.scala.env.ClusterEnvironment

import scala.util.{Failure, Success}
object Logging {
  def example(): Unit = {
    // #tag::example1[]
    import java.util.logging.{ConsoleHandler, Level, Logger}
    val logger = Logger.getLogger("com.couchbase.client")
    logger.setLevel(Level.FINE)
    for (h <- logger.getParent.getHandlers) {
      h match {
        case x: ConsoleHandler => h.setLevel(Level.FINE)
        case _                 =>
      }
    }
    // #end::example1[]
  }

  def env(): Unit = {
    // #tag::env[]
    import com.couchbase.client.scala.env.{ClusterEnvironment, LoggerConfig}

    val environment = ClusterEnvironment.builder
      .loggerConfig(
        LoggerConfig()
          .fallbackToConsole(true)
          .disableSlf4J(true)
      )
      .build
    // #end::env[]
  }

  def events(): Unit = {
    // #tag::events[]
    val environmentTry = ClusterEnvironment.builder.build

    environmentTry match {
      case Success(env) =>
        env.core.eventBus.subscribe(event => {
          // handle events as they arrive
          if (event.severity() == Event.Severity.INFO || event
                .severity() == Event.Severity.WARN) {
            println(event)
          }
        })

        Cluster.connect(
          "127.0.0.1",
          ClusterOptions
            .create("Administrator", "password")
            .environment(env)
        ) match {
          case Success(cluster) =>
            val bucket = cluster.bucket("travel-sample")

            cluster.disconnect()
            cluster.env.shutdown()

          case Failure(err) => println(s"Failed to connect to cluster: ${err}")
        }

      case Failure(err) => println(s"Failed to create environment: ${err}")
    }
    // #end::events[]
  }
}
