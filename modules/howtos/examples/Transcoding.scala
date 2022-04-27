// /*
//  * Copyright (c) 2020 Couchbase, Inc.
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *    http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */

// import java.nio.charset.StandardCharsets

// import com.couchbase.client.core.msg.kv.CodecFlags.CommonFlags
// import com.couchbase.client.scala.Cluster
// import com.couchbase.client.scala.codec._
// import com.couchbase.client.scala.kv.{GetOptions, UpsertOptions}
// import org.msgpack.core.MessagePack
// import org.scalatest.funsuite.AnyFunSuite

// import scala.reflect.runtime.universe
// import scala.util.{Failure, Success, Try}
// import scala.reflect.runtime.universe._

// // #tag::ujson[]
// case class MyUser(name: String, age: Int)

// object MyUser {
//   implicit object UserSerializer extends JsonSerializer[MyUser] {
//     override def serialize(content: MyUser): Try[Array[Byte]] = {
//       // It's also possible for uPickle to serialize and deserialize
//       // case classes directly to/from JSON, but for the purposes of
//       // demonstration we will generate the JSON manually.
//       val json = ujson.Obj("name" -> content.name, "age" -> content.age)
//       Success(ujson.transform(json, ujson.BytesRenderer()).toBytes)
//     }
//   }

//   implicit object UserDeserializer extends JsonDeserializer[MyUser] {
//     override def deserialize(bytes: Array[Byte]): Try[MyUser] = {
//       Try({
//         val json = upickle.default.read[ujson.Value](bytes)
//         MyUser(json("name").str, json("age").num.toInt)
//       })
//     }
//   }
// }
// // #end::ujson[]

// // #tag::overloaded1[]
// case class MyUser2(name: String, age: Int)

// object MyUser2 {
//   // First serializer uses uPickle
//   implicit object UserSerializer1 extends JsonSerializer[MyUser2] {
//     override def serialize(content: MyUser2): Try[Array[Byte]] = {
//       val json = ujson.Obj("name" -> content.name, "age" -> content.age)
//       Success(ujson.transform(json, ujson.BytesRenderer()).toBytes)
//     }
//   }

//   // Second serializer writes the JSON manually
//   implicit object UserSerializer2 extends JsonSerializer[MyUser2] {
//     override def serialize(content: MyUser2): Try[Array[Byte]] = {
//       val sb = new StringBuilder
//       sb.append("""{"name":""")
//       sb.append(content.name)
//       sb.append("""","age":""")
//       sb.append(content.age)
//       sb.append("}")
//       Success(sb.toString.getBytes(StandardCharsets.UTF_8))
//     }
//   }
// }
// // #end::overloaded1[]

// // #tag::msgpack-serializer[]
// object MsgPack {
//   implicit object MsgPackSerializer extends JsonSerializer[MyUser] {
//     override def serialize(content: MyUser): Try[Array[Byte]] = {
//       Try({
//         // MessagePack can automatically generate equivalent code,
//         // but for demonstration purposes we will do it manually
//         val packer = MessagePack.newDefaultBufferPacker()
//         packer.packString(content.name)
//         packer.packInt(content.age)
//         packer.close()
//         packer.toByteArray
//       })
//     }
//   }

//   implicit object MsgPackDeserializer extends JsonDeserializer[MyUser] {
//     override def deserialize(bytes: Array[Byte]): Try[MyUser] = {
//       Try({
//         val unpacker = MessagePack.newDefaultUnpacker(bytes)
//         MyUser(unpacker.unpackString(), unpacker.unpackInt())
//       })
//     }
//   }
// }
// // #end::msgpack-serializer[]

// // #tag::msgpack-transcoder[]
// class BinaryTranscoder extends TranscoderWithSerializer {
//   def encode[A](value: A, serializer: JsonSerializer[A]): Try[EncodedValue] = {
//     serializer
//       .serialize(value)
//       .map(bytes => EncodedValue(bytes, DocumentFlags.Binary))
//   }

//   def decode[A](
//     value: Array[Byte],
//     flags: Int,
//     serializer: JsonDeserializer[A]
//   )(implicit tag: WeakTypeTag[A]): Try[A] = {
//     serializer.deserialize(value)
//   }
// }
// // #end::msgpack-transcoder[]

// class Transcoding extends AnyFunSuite {

//   val cluster = Cluster.connect("localhost", "Administrator", "password").get
//   val collection = cluster.bucket("default").defaultCollection

//   test("ujson") {
//       // #tag::ujson-used[]
//       val user = MyUser("John Smith", 27)

//       // The compiler will find our UserSerializer for this
//       collection.upsert("john-smith", user) match {

//         case Success(_) =>
//           collection
//             .get("john-smith")

//             // ... and our UserDeserializer for this
//             .flatMap(fetched => fetched.contentAs[MyUser]) match {

//             case Success(fetchedUser) =>
//               assert(fetchedUser == user)

//             case Failure(err) => fail(s"Failed to get doc: $err")
//           }

//         case Failure(err) => fail(s"Failed to upsert doc: $err")
//       }
//       // #end::ujson-used[]
//     }

//   test("overloaded serializers") {
//       // #tag::overloaded2[]
//       val user = MyUser2("John Smith", 27)

//       // This import will cause the compiler to prefer UserSerializer2
//       import MyUser2.UserSerializer2
//       collection.upsert("john-smith", user).get

//       // But the application can override this
//       collection.upsert("john-smith", user)(MyUser2.UserSerializer1).get
//       // #end::overloaded2[]
//     }

//   test("messagepack") {
//       // #tag::msgpack-encode[]
//       val user = MyUser("John Smith", 27)

//       // Make sure the MessagePack serializers are used
//       import MsgPack._

//       val transcoder = new BinaryTranscoder

//       // The compiler will find and use our MsgPackSerializer here
//       collection.upsert(
//         "john-smith",
//         user,
//         UpsertOptions().transcoder(transcoder)
//       ) match {
//         case Success(_) =>

//           collection
//             .get("john-smith", GetOptions().transcoder(transcoder))

//             // ... and our MsgPackDeserializer here
//             .flatMap(result => result.contentAs[MyUser]) match {

//             case Success(fetched) =>
//               assert(fetched == user)

//             case Failure(err) => fail(s"Failed to get or convert doc: $err")
//           }

//         case Failure(err) => fail(s"Failed to upsert doc: $err")
//       }
//       // #end::msgpack-encode[]
//     }

//   test("string") {
//       // #tag::string[]
//       collection.upsert(
//         "doc-id",
//         "hello world",
//         UpsertOptions().transcoder(RawStringTranscoder.Instance)
//       ) match {

//         case Success(_) =>
//           collection
//             .get(
//               "doc-id",
//               GetOptions().transcoder(RawStringTranscoder.Instance)
//             )
//             .flatMap(result => result.contentAs[String]) match {

//             case Success(fetched) =>
//               assert(fetched == "hello world")

//             case Failure(err) => fail(s"Failed to get or convert doc: $err")
//           }

//         case Failure(err) => fail(s"Failed to upsert doc: $err")
//       }
//       // #end::string[]
//     }

//   test("binary") {
//       // #tag::binary[]
//     val content: Array[Byte] = "hello world".getBytes(StandardCharsets.UTF_8)

//       collection.upsert(
//         "doc-id",
//         content,
//         UpsertOptions().transcoder(RawBinaryTranscoder.Instance)
//       ) match {
//         case Success(_) =>
//           collection
//             .get(
//               "doc-id",
//               GetOptions().transcoder(RawBinaryTranscoder.Instance)
//             )
//             .flatMap(result => result.contentAs[Array[Byte]]) match {
//             case Success(fetched) =>
//               assert(fetched(0) == 'h')
//               assert(fetched(1) == 'e')
//               assert(fetched(2) == 'l')
//               // ...

//             case Failure(err) => fail(s"Failed to get or convert doc: $err")
//           }

//         case Failure(err) => fail(s"Failed to upsert doc: $err")
//       }
//       // #end::binary[]
//     }

//   test("json") {
//     // #tag::json[]

//     val json = ujson.Obj("name" -> "John Smith", "age" -> 27)
//     val bytes: Array[Byte] = ujson.transform(json, ujson.BytesRenderer()).toBytes

//     collection.upsert(
//       "doc-id",
//       bytes,
//       UpsertOptions().transcoder(RawJsonTranscoder.Instance)
//     ) match {
//       case Success(_) =>
//         collection
//           .get(
//             "doc-id",
//             GetOptions().transcoder(RawJsonTranscoder.Instance)
//           )
//           .flatMap(result => result.contentAs[Array[Byte]]) match {
//           case Success(fetched) =>
//             val jsonFetched = upickle.default.read[ujson.Value](fetched)
//             assert(jsonFetched("name").str == "John Smith")
//             assert(jsonFetched("age").num == 27)

//           case Failure(err) => fail(s"Failed to get or convert doc: $err")
//         }

//       case Failure(err) => fail(s"Failed to upsert doc: $err")
//     }
//     // #end::json[]
//   }
// }