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
import java.nio.charset.StandardCharsets

import com.couchbase.client.scala._
import com.couchbase.client.scala.implicits.Codec
import com.couchbase.client.scala.json._
import com.couchbase.client.scala.kv._

import scala.util.{Failure, Success, Try}
// #end::imports[]


object JSON {
  // #tag::cluster[]
  val cluster = Cluster.connect("localhost", "username", "password").get
  val bucket = cluster.bucket("travel-sample")
  val collection = bucket.defaultCollection
  // #end::cluster[]

  def scoped() {
    // #tag::cc[]
    case class Address(address: String)
    case class User(name: String, age: Int, addresses: Seq[Address])

    val user = User("John Smith", 29, List(Address("123 Fake Street")))
    // #end::cc[]
  }


  def circe1(): Unit = {
    case class Address(address: String)
    case class User(name: String, age: Int, addresses: Seq[Address])

    val user = User("John Smith", 29, List(Address("123 Fake Street")))

    // #tag::circe1[]
    import io.circe.generic.auto._
    import io.circe.syntax._

    val json: io.circe.Json = user.asJson

    val result: Try[io.circe.Json] = for {
      _       <- collection.insert("id", json)
      doc     <- collection.get("id")
      content <- doc.contentAs[io.circe.Json]
    } yield content
    // #end::circe1[]
  }

  def circe3(): Unit = {
    case class Address(address: String)
    case class User(name: String, age: Int, addresses: Seq[Address])

    val user = User("John Smith", 29, List(Address("123 Fake Street")))

    // #tag::circe3[]
    import io.circe.generic.auto._
    import io.circe.syntax._

    val json: io.circe.Json = user.asJson

    val result: Try[io.circe.Json] = collection.insert("id", json)
        .flatMap(_ => collection.get("id"))
        .flatMap(doc => doc.contentAs[io.circe.Json])
    // #end::circe3[]
  }

  def circe2(): Unit = {
    case class Address(address: String)
    case class User(name: String, age: Int, addresses: Seq[Address])

    val user = User("John Smith", 29, List(Address("123 Fake Street")))

    // #tag::circe2[]
    import io.circe.generic.auto._
    import io.circe.syntax._

    // Circe can encode case classes directly to its `Json` type, with no codec code required
    val json: io.circe.Json = user.asJson

    // Can provide Circe types for all mutation operations
    val result1: Try[MutationResult] = collection.insert("id", json)

    result1 match {
      case Success(_) =>
        val result2: Try[GetResult] = collection.get("id")

        result2 match {
          case Success(doc) =>
            // Can retrieve document content as Circe types
            val content: Try[io.circe.Json] = doc.contentAs[io.circe.Json]

          case Failure(err) => println(s"Error: ${err}")
        }

      case Failure(err) => println(s"Error: ${err}")
    }
    // #end::circe2[]
  }

  def upickle(): Unit = {
    // #tag::upickle[]
    val content = ujson.Obj("name" -> "John Smith",
      "age" -> 29,
      "addresses" -> ujson.Arr(
        ujson.Obj("address" -> "123 Fake Street")
      ))

    val result: Try[ujson.Obj] = for {
      // Can provide upickle types for all mutation operations
      _       <- collection.insert("id", content)
      doc     <- collection.get("id")

      // Can retrieve document content as upickle types
      content <- doc.contentAs[ujson.Obj]
    } yield content
    // #end::upickle[]
  }

  def json4s(): Unit = {
    // #tag::json4s[]
    import org.json4s.JsonAST._
    import org.json4s.JsonDSL._

    val json: JValue =
      ("name" -> "John Smith") ~
        ("age" -> 29) ~
        ("addresses" -> List(
          "address" -> "123 Fake Street")
          )

    val result: Try[JValue] = for {
      // Can provide Json4s types for all mutation operations
      _ <- collection.insert("id", json)
      doc <- collection.get("id")

      // Can retrieve document content as Json4s types
      content <- doc.contentAs[JValue]
    } yield content
    // #end::json4s[]
  }

  def json1() {

    // User("Eric Wimp", 9, Seq(Address("29 Acacia Road")))
    // #tag::json1[]
    val json = JsonObject("name" -> "Eric Wimp",
      "age" -> 9,
      "addresses" -> JsonArray(JsonObject("address" -> "29 Acacia Road")))

    val str = json.toString()
    // """{"name":"Eric Wimp","age":9,"addresses":[{"address","29 Acacia Road"}]}"""
    // #end::json1[]

    // #tag::json-mutable[]
    val obj = JsonObject.create.put("name", "Eric Wimp")
    obj.put("age", 9)
    val arr = JsonArray.create
    arr.add(JsonObject("address" -> "29 Acacia Road"))
    obj.put("addresses", arr)
    // #end::json-mutable[]

    // #tag::json2[]
    json.str("name") // "Eric Wimp"
    json.arr("addresses").obj(0).str("address") // "29 Acacia Road"
    // #end::json2[]

    // #tag::json3[]
    json.dyn.name.str // "Eric Wimp"
    json.dyn.addresses(0).address.str // "29 Acacia Road"
    // #end::json3[]

    // #tag::json4[]
    val safe: JsonObjectSafe = json.safe

    val r: Try[String] = safe.str("name")

    r match {
      case Success(name) => println(s"Their name is $name")
      case Failure(err) => println(s"Could not find field 'name': $err")
    }
    // #end::json4[]

    // #tag::json5[]
    val address: Try[String] = for {
      addresses <- safe.arr("addresses")
      address   <- addresses.obj(0)
      line      <- address.str("address")
    } yield line
    // #end::json5[]

    // #tag::json6[]
    val add: Try[String] = safe.dyn.addresses(0).address.str
    // #end::json6[]

  }

  def jsonObject(): Unit = {
    // #tag::jsonObject[]
    val json = JsonObject("name" -> "Eric Wimp",
      "age" -> 9,
      "addresses" -> JsonArray(JsonObject("address" -> "29 Acacia Road")))

    val result: Try[JsonObject] = for {
      // Can provide JsonObject for all mutation operations
      _       <- collection.insert("id", json)
      doc     <- collection.get("id")

      // Can retrieve document content as JsonOject (and JsonObjectSafe)
      content <- doc.contentAs[JsonObject]
    } yield content
    // #end::jsonObject[]
  }

  def jawn(): Unit = {
    // #tag::jawn[]
    import org.typelevel.jawn.ast._

    val json = JObject.fromSeq(Seq("name" -> JString("John Smith"),
      "age" -> JNum(29),
      "address" -> JArray.fromSeq(Seq(JObject.fromSeq(Seq("address" -> JString("123 Fake Street")))))))

    val result: Try[JValue] = for {
      // Can provide Jawn types for all mutation operations
      _       <- collection.insert("id", json)
      doc     <- collection.get("id")

      // Can retrieve document content as Jawn types
      content <- doc.contentAs[JValue]
    } yield content
    // #end::jawn[]
  }

  def playJson(): Unit = {
    // #tag::playJson[]
    import play.api.libs.json.Json._
    import play.api.libs.json._

    val json = obj("name" -> "John Smith",
      "age" -> 29,
      "address" -> arr(obj("address" -> "123 Fake Street")))

    val result: Try[JsValue] = for {
      // Can provide Play JSON types for all mutation operations
      _       <- collection.insert("id", json)
      doc     <- collection.get("id")

      // Can retrieve document content as Play JSON types
      content <- doc.contentAs[JsValue]
    } yield content
    // #end::playJson[]
  }

  def string(): Unit = {
    // #tag::string[]
    val json = """{"hello":"world"}"""

    val result: Try[String] = for {
      // Can provide Play JSON types for all mutation operations
      _       <- collection.insert("id", json)
      doc     <- collection.get("id")

      // Can retrieve document content as String
      content <- doc.contentAs[String]
    } yield content
    // #end::string[]
  }

  def bytes(): Unit = {
    // #tag::bytes[]
    val json: Array[Byte] = """{"hello":"world"}""".getBytes(StandardCharsets.UTF_8)

    val result: Try[Array[Byte]] = for {
      // Can provide Play JSON types for all mutation operations
      _       <- collection.insert("id", json)
      doc     <- collection.get("id")

      // Can retrieve document content as String
      content <- doc.contentAs[Array[Byte]]
    } yield content
    // #end::bytes[]
  }

  def caseClasses() = {
    // #tag::cc-create[]
    case class Address(line1: String)
    case class User(name: String, age: Int, addresses: Seq[Address])
    // #end::cc-create[]

    // #tag::cc-codec[]
    object User {
      implicit val codec: Codec[User] = Codec.codec[User]
    }
    // #end::cc-codec[]

    // #tag::cc-fails[]
    val user = User("Eric Wimp", 9, Seq(Address("29 Acacia Road")))

    // Will fail to compile
    collection.insert("eric-wimp", user)
    // #end::cc-fails[]

    // #tag::cc-get[]
    val r: Try[User] = for {
      _     <- collection.upsert("document-key", user)
      doc   <- collection.get("document-key")
      user  <- doc.contentAs[User]
    } yield user
    // #end::cc-get[]

  }
}
