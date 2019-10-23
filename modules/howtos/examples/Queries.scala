// #tag::imports[]
import com.couchbase.client.scala._
import com.couchbase.client.scala.implicits.Codec
import com.couchbase.client.scala.json._
import com.couchbase.client.scala.query._
import reactor.core.scala.publisher._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
// #end::imports[]


object Queries {
  def main(args: Array[String]): Unit = {

// #tag::cluster[]
val cluster = Cluster.connect("localhost", "username", "password").get
val bucket = cluster.bucket("travel-sample")
// #end::cluster[]

def simple() {
// #tag::simple[]
val statement = """select * from `travel-sample` limit 10;"""
val result: Try[QueryResult] = cluster.query(statement)
// #end::simple[]

// #tag::simple-results[]
result match {
  case Success(result: QueryResult) =>
    result.rowsAs[JsonObject] match {
      case Success(rows) =>
        println(s"Got ${rows} rows")
      case Failure(err) => println(s"Error: $err")
    }
  case Failure(err) => println(s"Error: $err")
}
// #end::simple-results[]
}

def getRows() {
// #tag::get-rows[]
cluster.query("""select * from `travel-sample` limit 10;""")
  .flatMap(_.rowsAs[JsonObject]) match {
  case Success(rows: Seq[JsonObject]) =>
    rows.foreach(row => println(row))
  case Failure(err) =>
    println(s"Error: $err")
}
// #end::get-rows[]
}

def caseClasses() {
// #tag::codec[]
case class Address(line1: String)
case class User(name: String, age: Int, addresses: Seq[Address])
object User {
  implicit val codec: Codec[User] = Codec.codec[User]
}
// #end::codec[]

// #tag::case-classes[]

val statement = """select `travel-sample`.* from `travel-sample` limit 10;"""

cluster.query(statement)
  .flatMap(_.rowsAs[User]) match {
  case Success(rows: Seq[User]) =>
    rows.foreach(row => println(row))
  case Failure(err) =>
    println(s"Error: $err")
}
// #end::case-classes[]
}

def positional() {
// #tag::positional[]
val stmt = """select `travel-sample`.* from `travel-sample` where type=$1 and country=$2 limit 10;"""
val result = cluster.query(stmt,
  QueryOptions().positionalParameters(Seq("airline", "United States")))
// #end::positional[]
}

def named() {
// #tag::named[]
val stmt = """select `travel-sample`.* from `travel-sample` where type=$type and country=$country limit 10;"""
val result = cluster.query(stmt,
  QueryOptions().namedParameters(Map("type" -> "airline", "country" -> "United States")))
// #end::named[]
}

def requestPlus() {
// #tag::request-plus[]
val result = cluster.query("""select `travel-sample`.* from `travel-sample` limit 10;""",
  QueryOptions().scanConsistency(QueryScanConsistency.RequestPlus()))
// #end::request-plus[]
}

def async() {
// #tag::async[]
// When we work with Scala Futures an ExecutionContext must be provided.
// For this example we'll just use the global default
import scala.concurrent.ExecutionContext.Implicits.global

val stmt = """select `travel-sample`.* from `travel-sample` limit 10;"""
val future: Future[QueryResult] = cluster.async.query(stmt)

future onComplete {
  case Success(result) =>
    result.rowsAs[JsonObject] match {
      case Success(rows) => rows.foreach(println(_))
      case Failure(err) => println(s"Error: $err")
    }
  case Failure(err)    => println(s"Error: $err")
}
// #end::async[]
}

def reactive() {
// #tag::reactive[]
val stmt = """select `travel-sample`.* from `travel-sample`;"""
val mono: SMono[ReactiveQueryResult] = cluster.reactive.query(stmt)

val rows: SFlux[JsonObject] = mono
  // ReactiveQueryResult contains a rows: Flux[QueryRow]
  .flatMapMany(result => result.rowsAs[JsonObject])

// Just for example, block on the rows.  This is not best practice and apps
// should generally not block.
val allRows: Seq[JsonObject] = rows
  .doOnNext(row => println(row))
  .doOnError(err => println(s"Error: $err"))
  .collectSeq()
  .block()

// #end::reactive[]
}

}
}
