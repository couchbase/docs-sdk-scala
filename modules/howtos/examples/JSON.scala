// #tag::imports[]
import com.couchbase.client.core.error.subdoc.PathExistsException
import com.couchbase.client.scala._
import com.couchbase.client.scala.codec.Conversions.Codec
import com.couchbase.client.scala.implicits.Codecs
import com.couchbase.client.scala.json._
import com.couchbase.client.scala.kv.{LookupInResult, LookupInSpec, MutateInSpec}
import com.couchbase.client.scala.query._
import reactor.core.scala.publisher._
import com.couchbase.client.scala.kv._
import com.couchbase.client.scala.kv.MutateInSpec._
import com.couchbase.client.scala.kv.LookupInSpec._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}
// #end::imports[]


object JSON {
  // #tag::cluster[]
  val cluster = Cluster.connect("localhost", "username", "password")
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


  def circe(): Unit = {
    case class Address(address: String)
    case class User(name: String, age: Int, addresses: Seq[Address])

    val user = User("John Smith", 29, List(Address("123 Fake Street")))

    // #tag::circe[]
    import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

    // Circe can encode case classes directly to its `Json` type, with no codec code required
    val json: io.circe.Json = user.asJson

    val result: Try[io.circe.Json] = for {
      // Can provide Circe types for all mutation operations
      _       <- collection.insert("id", json)
      doc     <- collection.get("id")

      // Can retrieve document content as Circe types
      content <- doc.contentAs[io.circe.Json]
    } yield content
    // #end::circe[]
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
      // Can provide upickle types for all mutation operations
      _       <- collection.insert("id", json)
      doc     <- collection.get("id")

      // Can retrieve document content as Circe types
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

  def caseClasses() = {
    // #tag::cc-create[]
    case class Address(line1: String)
    case class User(name: String, age: Int, addresses: Seq[Address])
    // #end::cc-create[]

    // #tag::cc-codec[]
    object User {
      implicit val codec: Codec[User] = Codecs.codec[User]
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

    r match {
      case Success(user: User) => println(s"User: ${user}")
      case Failure(err)        => println("Error: " + err)
    }
    // #end::cc-get[]

  }
}
