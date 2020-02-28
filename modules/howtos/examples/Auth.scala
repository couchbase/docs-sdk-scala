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

import java.io.InputStream
import java.security.KeyStore

import com.couchbase.client.core.env.CertificateAuthenticator
import com.couchbase.client.scala.{Cluster, ClusterOptions}
import com.couchbase.client.scala.env.{ClusterEnvironment, SecurityConfig}
import com.couchbase.client.scala.json.JsonObject
import javax.net.ssl.{KeyManagerFactory, TrustManagerFactory}
import org.scalatest.FunSuite
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration._
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

class Auth extends AnyFunSuite {
  test("basic cluster") {
    // #tag::basic[]
    Cluster.connect("10.112.180.101", "username", "password") match {
      case Success(cluster) => // Use the cluster
      case Failure(err) => println(s"Failed to open cluster: $err")
    }
    // #end::basic[]
  }

  test("auth") {
    val hostname = "ec2-18-130-37-57.eu-west-2.compute.amazonaws.com"

    // #tag::auth[]
    // Open the keystore using standard JVM classes
    val keystorePassword = new String("storepass").toCharArray
    val keystoreFilename = "my.keystore"

    // The format to use here depends on the format of the keystore.
    // "PKCS12" is what JDK 9+ creates by default.
    // "JKS" was the default for JDK 8 and below.
    val keystore = KeyStore.getInstance("PKCS12")
    val keystoreStream =
      getClass.getClassLoader.getResourceAsStream(keystoreFilename)
    keystore.load(keystoreStream, keystorePassword)
    keystoreStream.close()

    val kmf =
      KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(keystore, keystorePassword)

    val trustMan =
      TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustMan.init(keystore)

    // Create a Couchbase CertificateAuthenticate that will use that keystore
    val auth =
      CertificateAuthenticator.fromKeyManagerFactory(() => kmf)

    // Create a Couchbase ClusterEnvironment to enable TLS (required)
    val cluster = ClusterEnvironment.builder
      .securityConfig(
        SecurityConfig().enableTls(true).trustManagerFactory(trustMan)
      )
      .build
      .flatMap(
        env => Cluster.connect(hostname, ClusterOptions(auth).environment(env))
      )
    // #end::auth[]
  }

  test("basic") {
    val cluster = Cluster
      .connect(
        "ec2-18-130-37-57.eu-west-2.compute.amazonaws.com",
        "Administrator",
        "password"
      )
      .get
    cluster.waitUntilReady(30.seconds)
    println("ready?")
    cluster
      .bucket("default")
      .defaultCollection
      .upsert("test", JsonObject.create)
      .get
  }

  test("tls") {
    val keystorePassword = "storepass".toCharArray
    val keystore = KeyStore.getInstance("JKS")
    val keystoreStream =
      getClass.getClassLoader.getResourceAsStream("my.keystore")
    keystore.load(keystoreStream, keystorePassword)
    keystoreStream.close()

    val trustMan =
      TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustMan.init(keystore)

    val cluster = ClusterEnvironment.builder
      .securityConfig(
        SecurityConfig()
          .enableTls(true)
          .trustManagerFactory(trustMan)
      )
      .build
      .flatMap(
        env =>
          // Connect to the cluster with the authenticator and environment
          Cluster.connect(
            "ec2-18-130-37-57.eu-west-2.compute.amazonaws.com",
            ClusterOptions
              .create("Administrator", "password")
              .environment(env)
        )
      )
      .get
    cluster.waitUntilReady(30.seconds)
    println("ready?")
    cluster
      .bucket("default")
      .defaultCollection
      .upsert("test", JsonObject.create)
      .get
  }

}

object Auth {}
