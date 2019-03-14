// #tag::imports[]
import com.couchbase.client.scala._
import com.couchbase.client.scala.json._
import com.couchbase.client.scala.query._
import reactor.core.scala.publisher._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
// #end::imports[]


object Queries {
  def main(args: Array[String]): Unit = {

// #tag::cluster[]
val cluster = Cluster.connect("localhost", "username", "password")
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
    println(s"Got ${result.rows.size} rows")
  case Failure(err) => println(s"Error: $err")
}
// #end::simple-results[]

result match {
  case Success(result: QueryResult) =>
// #tag::simple-results-full[]
result.rows.foreach(row => {
  row.contentAs[JsonObject] match {
    case Success(content) => println(content)
    case Failure(err)     => println(s"Error: $err")
  }
})
// #end::simple-results-full[]
  case Failure(err)    => println(s"Error: $err")
}

  assert(result.get.rows.size == 10)
}

def getRows() {
// #tag::get-rows[]
cluster.query("""select * from `travel-sample` limit 10;""")
  // Drop any rows that fail to convert
  .map(_.rows.flatMap(_.contentAs[JsonObject].toOption)) match {
  case Success(rows: Seq[JsonObject]) =>
    rows.foreach(row => println(row))
  case Failure(err) =>
    println(s"Error: $err")
}
// #end::get-rows[]
}

def positional() {
// #tag::positional[]
val stmt = """select * from `travel-sample` where type=$1 and country=$2 limit 10;"""
val result = cluster.query(stmt,
  QueryOptions().positionalParameters("airline", "United States"))
// #end::positional[]

  assert(cluster.query("""select * from `travel-sample` where type=$1 and country=$2 limit 10;""",
    QueryOptions().positionalParameters("airline", "United States")).get.rows.size == 10)
}

def named() {
// #tag::named[]
val stmt = """select * from `travel-sample` where type=$type and country=$country limit 10;"""
val result = cluster.query(stmt,
  QueryOptions().namedParameters("type" -> "airline", "country" -> "United States"))
// #end::named[]

  assert(cluster.query("""select * from `travel-sample` where type=$type and country=$country limit 10;""",
    QueryOptions().namedParameters("type" -> "airline", "country" -> "United States")).get.rows.size == 10)
}

def requestPlus() {
// #tag::request-plus[]
val result = cluster.query("""select * from `travel-sample` limit 10;""",
  QueryOptions().scanConsistency(ScanConsistency.RequestPlus()))
// #end::request-plus[]

  assert(cluster.query("""select * from `travel-sample` where type=$type and country=$country limit 10;""",
    QueryOptions().namedParameters("type" -> "airline", "country" -> "United States")).get.rows.size == 10)
}

def async() {
// #tag::async[]
// When we work with Scala Futures an ExecutionContext must be provided.
// For this example we'll just use the global default
import scala.concurrent.ExecutionContext.Implicits.global

val stmt = """select * from `travel-sample` limit 10;"""
val future: Future[QueryResult] = cluster.async.query(stmt)

future onComplete {
  case Success(result) =>
    result.rows.foreach(row => println(row.contentAs[JsonObject]))
  case Failure(err)    => println(s"Error: $err")
}
// #end::async[]

  assert(cluster.query("""select * from `travel-sample` where type=$type and country=$country limit 10;""",
    QueryOptions().namedParameters("type" -> "airline", "country" -> "United States")).get.rows.size == 10)
}

def reactive() {
// #tag::reactive[]
val stmt = """select * from `travel-sample`;"""
val mono: Mono[ReactiveQueryResult] = cluster.reactive.query(stmt)

val rows: Flux[JsonObject] = mono
  // ReactiveQueryResult contains a rows: Flux[QueryRow]
  .flatMapMany(result => result.rows)
  // contentAs[T] returns Try[T]. Try.get will throw an exception on failure,
  // which is exactly what we want as Reactor will map this to a Reactor error
  .map(row => row.contentAs[JsonObject].get)

// Just for example, block on the rows.  This is not best practice and apps
// should generally not block.
val allRows: Seq[JsonObject] = rows
  .doOnNext(row => println(row))
  .doOnError(err => println(s"Error: $err"))
  .collectSeq()
  .block()

// #end::reactive[]
  assert(allRows.size == 10)
}

}
}
