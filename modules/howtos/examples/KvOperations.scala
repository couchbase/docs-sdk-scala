// #tag::imports[]
import com.couchbase.client.core.error._
import com.couchbase.client.scala._
import com.couchbase.client.scala.api.MutationResult
import com.couchbase.client.scala.codec.Conversions.Codec
import com.couchbase.client.scala.durability._
import com.couchbase.client.scala.implicits.Codecs
import com.couchbase.client.scala.json._
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
// #end::imports[]

// #tag::cc-create[]
case class Address(line1: String)
case class User(name: String, age: Int, addresses: Seq[Address])
// #end::cc-create[]

// #tag::cc-codec[]
object User {
  implicit val codec: Codec[User] = Codecs.codec[User]
}
// #end::cc-codec[]

object KvOperations {
  def main(args: Array[String]) {

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
json.arr("addresses").obj(0).str("address")  // "29 Acacia Road"
// #end::json2[]

  // #tag::json3[]
json.dyn.name.str // "Eric Wimp"
json.dyn.addresses(0).address.str  // "29 Acacia Road"
// #end::json3[]

// #tag::json4[]
val safe: JsonObjectSafe = json.safe

val r: Try[String] = safe.str("name")

r match {
  case Success(name) => println(s"Their name is $name")
  case Failure(err)  => println(s"Could not find field 'name': $err")
}
// #end::json4[]
}

// #tag::cluster[]
val cluster = Cluster.connect("localhost", "username", "password")
// #end::cluster[]

// #tag::resources[]
val bucket = cluster.bucket("bucket-name")
val scope = bucket.scope("scope-name")
val collection = scope.collection("collection-name")
// #end::resources[]

// #tag::apis[]
val asyncApi: AsyncCollection = collection.async
val reactiveApi: ReactiveCollection = collection.reactive
// #end::apis[]

// #tag::upsert[]
val json = JsonObject("foo" -> "bar", "baz" -> "qux")

collection.upsert("document-key", json) match {
  case Success(result) =>
  case Failure(exception) => println("Error: " + exception)
}
// #end::upsert[]

def insert() {
// #tag::insert[]
collection.insert("document-key", json) match {
  case Success(result) =>
  case Failure(err: KeyExistsException) =>
    println("The document already exists")
  case Failure(err) => println("Error: " + err)
}
// #end::insert[]
}

// #tag::get-simple[]
collection.get("document-key") match {
  case Success(result) => println("Document fetched successfully")
  case Failure(err) => println("Error getting document: " + err)
}
// #end::get-simple[]

def get() {
// #tag::get[]
// Create some initial JSON
val json = JsonObject("status" -> "awesome!")

// Insert it
collection.insert("document-key", json) match {
  case Success(result) =>
  case Failure(err) => println("Error: " + err)
}

// Get it back
collection.get("document-key") match {
  case Success(result) =>

    // Convert the content to a JsonObjectSafe
    result.contentAs[JsonObjectSafe] match {
      case Success(json) =>

        // Pull out the JSON's status field, if it exists
        json.str("status") match {
          case Success(status) => println(s"Couchbase is $status")
          case _ => println("Field 'status' did not exist")
        }
      case Failure(err) => println("Error decoding result: " + err)
    }
  case Failure(err) => println("Error getting document: " + err)
}
// #end::get[]
}

def getFor() {
// #tag::get-for[]
val r: Try[String] = for {
  result <- collection.get("document-key")
  json   <- result.contentAs[JsonObjectSafe]
  status <- json.str("status")
} yield status

r match {
  case Success(status) => println(s"Couchbase is $status")
  case Failure(err)    => println("Error: " + err)
}
// #end::get-for[]
}

def getMap() {
// #tag::get-map[]
val r: Try[String] = collection.get("document-key")
  .flatMap(_.contentAs[JsonObjectSafe])
  .flatMap(_.str("status"))

r match {
  case Success(status) => println(s"Couchbase is $status")
  case Failure(err)    => println("Error: " + err)
}
// #end::get-map[]
}

def replace() {
// #tag::replace[]
val initial = JsonObject("status" -> "great")

val r: Try[MutationResult] = for {
  // Insert a document.  Don't care about the exact details of the result, just
  // whether it was successful, so store result in _
  _      <- collection.insert("document-key", initial)

  // Get the document back
  doc    <- collection.get("document-key")

  // Extract the content as a JsonObjectSafe
  json   <- doc.contentAs[JsonObjectSafe]

  // Modify the content.  JsonObjectSafe is mutable so we don't need to store the
  // result, store it in _
  _      <- json.put("status", "awesome!")

  // Replace the document with the updated content, and the document's CAS value
  // (which we'll cover in a moment)
  result <- collection.replace("document-key", json, cas = doc.cas)
} yield result

r match {
  case Success(result) =>
  case Failure(err: CASMismatchException) =>
    println("Could not write as another agent has concurrently modified the document")
  case Failure(err) => println("Error: " + err)
}
// #end::replace[]
}

def replaceRetry() {
// #tag::replace-retry[]
val initial = JsonObject("status" -> "great")

// Insert some initial data
collection.insert("document-key", json) match {
  case Success(result) =>

    // This is the get-and-replace we want to do, as a lambda
    val op = () => for {
      doc    <- collection.get("document-key")
      json   <- doc.contentAs[JsonObjectSafe]
      _      <- json.put("status", "awesome!")
      result <- collection.replace("document-key", json, cas = doc.cas)
    } yield result

    // Send our lambda to retryOnCASMismatch to take care of retrying it
    // For space reasons, error-handling of r is left out
    val r: Try[MutationResult] = retryOnCASMismatch(op)

  case Failure(err) => println("Error: " + err)
}

// Try the provided operation, retrying on CASMismatchException
def retryOnCASMismatch(op: () => Try[MutationResult]): Try[MutationResult] = {
  // Perform the operation
  val result = op()

  result match {
    // Retry on any CASMismatchException errors
    case Failure(err: CASMismatchException) =>
      retryOnCASMismatch(op)

    // If Success or any other Failure, return it
    case _ => result
  }
}
// #end::replace-retry[]
}

def remove() {
// #tag::remove[]
collection.remove("document-key") match {
  case Success(result) =>
  case Failure(err: KeyNotFoundException) =>
    println("The document does not exist")
  case Failure(err) => println("Error: " + err)
}
// #end::remove[]
}

def durability() {
// #tag::durability[]
collection.remove("document-key", durability = Durability.Majority) match {
  case Success(result) =>
  // The mutation is available in-memory on at least a majority of replicas
  case Failure(err: KeyNotFoundException) =>
    println("The document does not exist")
  case Failure(err)    => println("Error: " + err)
}
// #end::durability[]

// #tag::durability-observed[]
collection.remove("document-key",
  durability = Durability.ClientVerified(ReplicateTo.Two, PersistTo.None)) match {
  case Success(result) =>
  // The mutation is available in-memory on at least two replicas
  case Failure(err: KeyNotFoundException) =>
    println("The document does not exist")
  case Failure(err)    => println("Error: " + err)
}
// #end::durability-observed[]
}

def expiryInsert() {
// #tag::expiry-insert[]
collection.insert("document-key", json, expiration = 2.hours) match {
  case Success(result) =>
  case Failure(err)    => println("Error: " + err)
}
// #end::expiry-insert[]
}

def expiryGet() {
// #tag::expiry-get[]
collection.get("document-key", withExpiration = true) match {
  case Success(result) =>

    result.expiration match {
      case Some(expiry) => print(s"Got expiry: $expiry")
      case _            => println("Err: no expiration field")
    }

  case Failure(err) => println("Error getting document: " + err)
}
// #end::expiry-get[]
}

def expiryReplace() {
// #tag::expiry-replace[]
val r: Try[MutationResult] = for {
  doc    <- collection.get("document-key", withExpiration = true)
  expiry <- Try(doc.expiration.get)
            // ^^ doc.expiration is an Option, but we can't mix Try and
            // Option inside the same for-comprehension, so convert here
  json   <- doc.contentAs[JsonObjectSafe]
  _      <- json.put("foo", "bar")
  result <- collection.replace("document-key", json, expiration = expiry)
} yield result

r match {
  case Success(status) =>
  case Failure(err)    => println("Error: " + err)
}
// #end::expiry-replace[]
}

def expiryTouch() {
// #tag::expiry-touch[]
collection.getAndTouch("document-key", expiration = 4.hours) match {
  case Success(result) =>
  case Failure(err)    => println("Error: " + err)
}
// #end::expiry-touch[]
}

def counterIncrement() {
// #tag::counter-increment[]
// Increase a counter by 1, seeding it at an initial value of 0 if it does not exist
collection.binary.increment("document-key", delta = 1, initial = Some(0)) match {
  case Success(result) =>
    println(s"Counter now: ${result.content}")
  case Failure(err)    => println("Error: " + err)
}

// Decrease a counter by 1, seeding it at an initial value of 10 if it does not exist
collection.binary.decrement("document-key", delta = 1, initial = Some(10)) match {
  case Success(result) =>
    println(s"Counter now: ${result.content}")
  case Failure(err)    => println("Error: " + err)
}
// #end::counter-increment[]
}

def caseClass() {

// #tag::cc-fails[]
val user = User("Eric Wimp", 9, Seq(Address("29 Acacia Road")))

// Will fail to compile
collection.insert("eric-wimp", user)
// #end::cc-fails[]

// #tag::cc-get[]
val r: Try[User] = for {
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
}
