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

// tag::imports[]
import com.couchbase.client.core.error._
import com.couchbase.client.scala._
import com.couchbase.client.scala.durability._
import com.couchbase.client.scala.implicits.Codec
import com.couchbase.client.scala.json._
import com.couchbase.client.scala.kv.{GetOptions, InsertOptions, MutationResult, ReplaceOptions}

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
// end::imports[]

// tag::cc-create[]
case class Address(line1: String)
case class User(name: String, age: Int, addresses: Seq[Address])
// end::cc-create[]

// tag::cc-codec[]
object User {
  implicit val codec: Codec[User] = Codec.codec[User]
}
// end::cc-codec[]

object KvOperations {
  def main(args: Array[String]) {

    // tag::cluster[]
    val cluster = Cluster.connect("localhost", "Administrator", "password").get
    // end::cluster[]

    // tag::resources[]
    val bucket = cluster.bucket("travel-sample")
    val scope = bucket.scope("_default")
    val collection = scope.collection("_default")
    // end::resources[]

    // tag::apis[]
    val asyncApi: AsyncCollection = collection.async
    val reactiveApi: ReactiveCollection = collection.reactive
    // end::apis[]

    json1()
    upsert(collection)
    namedCollectionUpsert(bucket)
    insert(collection)
    getSimple(collection)
    get(collection)
    getFor(collection)
    getMap(collection)
    replace(collection)
    replaceRetry(collection)
    remove(collection)
    durability(collection)
    expiryInsert(collection)
    expiryGet(collection)
    expiryTouch(collection)
    counterIncrement(collection)
    caseClass(collection)
  }
  
  def json1() {
    println("[json1] - Example\n")
    // User("Eric Wimp", 9, Seq(Address("29 Acacia Road")))
    // tag::json1[]
    val json = JsonObject(
      "name" -> "Eric Wimp",
      "age" -> 9,
      "addresses" -> JsonArray(JsonObject("address" -> "29 Acacia Road"))
    )

    val str = json.toString()
    // """{"name":"Eric Wimp","age":9,"addresses":[{"address","29 Acacia Road"}]}"""
    // end::json1[]

    println("[json-mutable] - Example\n")
    // tag::json-mutable[]
    val obj = JsonObject.create.put("name", "Eric Wimp")
    obj.put("age", 9)
    val arr = JsonArray.create
    arr.add(JsonObject("address" -> "29 Acacia Road"))
    obj.put("addresses", arr)
    // end::json-mutable[]

    println("[json2] - Example\n")
    // tag::json2[]
    json.str("name") // "Eric Wimp"
    json.arr("addresses").obj(0).str("address") // "29 Acacia Road"
    // end::json2[]
    
    println("[json3] Example\n")
    // tag::json3[]
    json.dyn.name.str // "Eric Wimp"
    json.dyn.addresses(0).address.str // "29 Acacia Road"
    // end::json3[]

    println("[json4] - Example\n")
    // tag::json4[]
    val safe: JsonObjectSafe = json.safe

    val r: Try[String] = safe.str("name")

    r match {
      case Success(name) => println(s"Their name is $name")
      case Failure(err)  => println(s"Could not find field 'name': $err")
    }
    // end::json4[]
  }

  def upsert(collection:Collection) {
    println("\n[upsert] - Example\n")
    // tag::upsert[]
    val json = JsonObject("foo" -> "bar", "baz" -> "qux")

    collection.upsert("document-key", json) match {
      case Success(result)    => println("Document upsert successful")
      case Failure(exception) => println("Error: " + exception)
    }
    // end::upsert[]
  }

  def namedCollectionUpsert(bucket:Bucket) {
    println("\n[named-collection-upsert] - Example\n")
    // tag::named-collection-upsert[]
    val agentScope = bucket.scope("tenant_agent_00")
    val usersCollection = agentScope.collection("users")
    val json = JsonObject("name" -> "John Doe", "preferred_email" -> "johndoe111@test123.test")

    usersCollection.upsert("user-key", json) match {
      case Success(result)    => println("Document upsert successful")
      case Failure(exception) => println("Error: " + exception)
    }
    // end::named-collection-upsert[]
  }

  def insert(collection:Collection) {
    println("\n[insert] - Example\n")
    // tag::insert[]
    val json = JsonObject("foo" -> "bar", "baz" -> "qux")

    collection.insert("document-key2", json) match {
      case Success(result) => println("Document inserted successfully")
      case Failure(err: DocumentExistsException) =>
        println("The document already exists")
      case Failure(err) => println("Error: " + err)
    }
    // end::insert[]
  }

  def getSimple(collection:Collection) {
    println("\n[get-simple] - Example\n")
    // tag::get-simple[]
    collection.get("document-key") match {
      case Success(result) => println("Document fetched successfully")
      case Failure(err)    => println("Error getting document: " + err)
    }
    // end::get-simple[]
  }

  def get(collection:Collection) {
    println("\n[get] - Example\n")
    // tag::get[]
    // Create some initial JSON
    val json = JsonObject("status" -> "awesome!")
    
    // Insert it
    collection.insert("document-key3", json) match {
      case Success(result) => println("Document inserted successfully")
      case Failure(err)    => println("Error: " + err)
    }
    
    // Get it back
    collection.get("document-key3") match {
      case Success(result) =>
        // Convert the content to a JsonObjectSafe
        result.contentAs[JsonObjectSafe] match {
          case Success(json) =>
            // Pull out the JSON's status field, if it exists
            json.str("status") match {
              case Success(status) => println(s"Couchbase is $status")
              case _               => println("Field 'status' did not exist")
            }
          case Failure(err) => println("Error decoding result: " + err)
        }
      case Failure(err) => println("Error getting document: " + err)
    }
    // end::get[]
  }

  def getFor(collection:Collection) {
    println("\n[get-for] - Example\n")
    // tag::get-for[]
    val r: Try[String] = for {
      result <- collection.get("document-key3")
      json <- result.contentAs[JsonObjectSafe]
      status <- json.str("status")
    } yield status

    r match {
      case Success(status) => println(s"Couchbase is $status")
      case Failure(err)    => println("Error: " + err)
    }
    // end::get-for[]
  }

  def getMap(collection:Collection) {
    println("\n[get-map] - Example\n")
    // tag::get-map[]
    val r: Try[String] = collection
      .get("document-key3")
      .flatMap(_.contentAs[JsonObjectSafe])
      .flatMap(_.str("status"))

    r match {
      case Success(status) => println(s"Couchbase is $status")
      case Failure(err)    => println("Error: " + err)
    }
    // end::get-map[]
  }

  def replace(collection:Collection) {
    println("\n[replace] - Example\n")
    // tag::replace[]
    val initial = JsonObject("status" -> "great")

    val r: Try[MutationResult] = for {
      // Insert a document.  Don't care about the exact details of the result, just
      // whether it was successful, so store result in _
      _ <- collection.insert("document-key4", initial)

      // Get the document back
      doc <- collection.get("document-key4")

      // Extract the content as a JsonObjectSafe
      json <- doc.contentAs[JsonObjectSafe]

      // Modify the content (JsonObjectSafe is mutable)
      _ <- json.put("status", "awesome!")

      // Replace the document with the updated content, and the document's CAS value
      // (which we'll cover in a moment)
      result <- collection.replace("document-key4", json, cas = doc.cas)
    } yield result

    r match {
      case Success(result) => println("Document replaced successfully")
      case Failure(err: CasMismatchException) =>
        println(
          "Could not write as another agent has concurrently modified the document"
        )
      case Failure(err) => println("Error: " + err)
    }
    // end::replace[]
  }

  def replaceRetry(collection:Collection) {
    println("\n[replace-retry] - Example\n")
    // tag::replace-retry[]
    val initial = JsonObject("status" -> "great")

    // Insert some initial data
    collection.insert("document-key5", initial) match {
      case Success(result) =>
        // This is the get-and-replace we want to do, as a lambda
        val op = () =>
          for {
            doc <- collection.get("document-key5")
            json <- doc.contentAs[JsonObjectSafe]
            _ <- json.put("status", "awesome!")
            result <- collection.replace("document-key5", json, cas = doc.cas)
          } yield result

        // Send our lambda to retryOnCASMismatch to take care of retrying it
        // For space reasons, error-handling of r is left out
        val r: Try[MutationResult] = retryOnCASMismatch(op)

      case Failure(err) => println("Error: " + err)
    }

    // Try the provided operation, retrying on CasMismatchException
    def retryOnCASMismatch(
      op: () => Try[MutationResult]
    ): Try[MutationResult] = {
      // Perform the operation
      val result = op()

      result match {
        // Retry on any CasMismatchException errors
        case Failure(err: CasMismatchException) =>
          retryOnCASMismatch(op)

        // If Success or any other Failure, return it
        case _ => result
      }
    }
    // end::replace-retry[]
  }

  def remove(collection:Collection) {
    println("\n[remove] - Example\n")
    // tag::remove[]
    collection.remove("document-key") match {
      case Success(result) => println("Document removed successfully")
      case Failure(err: DocumentNotFoundException) =>
        println("The document does not exist")
      case Failure(err) => println("Error: " + err)
    }
    // end::remove[]
  }

  def durability(collection:Collection) {
    println("\n[durability] - Example\n")
    // tag::durability[]
    collection.remove("document-key2", durability = Durability.Majority) match {
      case Success(result) => println("Document removed successfully")
      // The mutation is available in-memory on at least a majority of replicas
      case Failure(err: DocumentNotFoundException) =>
        println("The document does not exist")
      case Failure(err) => println("Error: " + err)
    }
    // end::durability[]

    println("\n[durability-observed] - Example\n")
    // tag::durability-observed[]
    collection.remove(
      "document-key3",
      durability = Durability.ClientVerified(ReplicateTo.Two, PersistTo.None)
    ) match {
      case Success(result) => println("Document successfully removed")
      // The mutation is available in-memory on at least two replicas
      case Failure(err: DocumentNotFoundException) =>
        println("The document does not exist")
      case Failure(err) => println("Error: " + err)
    }
    // end::durability-observed[]
  }

  def expiryInsert(collection:Collection) {
    println("\n[expiry-insert] - Example\n")
    // tag::expiry-insert[]
    val json = JsonObject("foo" -> "bar", "baz" -> "qux")

    collection.insert("document-key", json, InsertOptions().expiry(2.hours)) match {
      case Success(result) => println("Document with expiry inserted successfully")
      case Failure(err)    => println("Error: " + err)
    }
    // end::expiry-insert[]
  }

  def expiryGet(collection:Collection) {
    println("\n[expiry-get] - Example\n")
    // tag::expiry-get[]
    collection.get("document-key", GetOptions().withExpiry(true)) match {
      case Success(result) =>
        result.expiry match {
          case Some(expiry) => println(s"Got expiry: $expiry")
          case _            => println("Err: no expiration field")
        }

      case Failure(err) => println("Error getting document: " + err)
    }
    // end::expiry-get[]
  }

  def expiryReplace(collection:Collection) {
    println("\n[expiry-replace] - Example\n")
    // tag::expiry-replace[]
    val r: Try[MutationResult] = for {
      doc <- collection.get("document-key", GetOptions().withExpiry(true))
      expiry <- Try(doc.expiry.get)
      // ^^ doc.expiration is an Option, but we can't mix Try and
      // Option inside the same for-comprehension, so convert here
      json <- doc.contentAs[JsonObjectSafe]
      _ <- json.put("foo", "bar")
      result <- collection.replace("document-key", json, ReplaceOptions().expiry(expiry))
    } yield result

    r match {
      case Success(status) => println("Document with expiry replaced successfully")
      case Failure(err)    => println("Error: " + err)
    }
    // end::expiry-replace[]
  }

  def expiryTouch(collection:Collection) {
    println("\n[expiry-touch] - Example\n")
    // tag::expiry-touch[]
    collection.getAndTouch("document-key", expiry = 4.hours) match {
      case Success(result) => println("Document fetched and updated with expiry")
      case Failure(err)    => println("Error: " + err)
    }
    // end::expiry-touch[]
  }

  def counterIncrement(collection:Collection) {
    println("\n[counter-increment] - Example\n")
    // tag::counter-increment[]
    // Increase a counter by 1, seeding it at an initial value of 1 if it does not exist
    collection.binary.increment("document-key6", delta = 1, initial = Some(1)) match {
      case Success(result) =>
        println(s"Counter now: ${result.content}")
      case Failure(err) => println("Error: " + err)
    }
    
    // Decrease a counter by 1, seeding it at an initial value of 10 if it does not exist
    collection.binary.decrement("document-key6", delta = 1, initial = Some(10)) match {
      case Success(result) =>
        println(s"Counter now: ${result.content}")
      case Failure(err) => println("Error: " + err)
    }
    // end::counter-increment[]
  }

  def caseClass(collection:Collection) {
    println("\n[cc-fails] - Example\n")
    // tag::cc-fails[]
    val user = User("Eric Wimp", 9, Seq(Address("29 Acacia Road")))

    // Will fail to compile
    collection.insert("eric-wimp", user) 
    // end::cc-fails[]

    println("\n[cc-get] - Example\n")
    // tag::cc-get[]
    val r: Try[User] = for {
      doc <- collection.get("eric-wimp")
      user <- doc.contentAs[User]
    } yield user

    r match {
      case Success(user: User) => println(s"User: ${user}")
      case Failure(err)        => println("Error: " + err)
    }
    // end::cc-get[]
  }
}
