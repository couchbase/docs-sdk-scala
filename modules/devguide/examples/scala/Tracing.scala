// tag::imports[]

import com.couchbase.client.core.cnc.RequestSpan.StatusCode
import com.couchbase.client.core.cnc.RequestTracer
import com.couchbase.client.scala.durability.Durability
import com.couchbase.client.scala.env.{ClusterEnvironment, ThresholdRequestTracerConfig}
import com.couchbase.client.scala.json.JsonObject
import com.couchbase.client.scala.kv.{GetOptions, UpsertOptions}
import com.couchbase.client.scala.{Cluster, ClusterOptions, Collection}
import com.couchbase.client.tracing.opentelemetry.{OpenTelemetryRequestSpan, OpenTelemetryRequestTracer}
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.{BatchSpanProcessor, SimpleSpanProcessor}
import io.opentelemetry.sdk.trace.samplers.Sampler

import scala.concurrent.duration._
import scala.util.Try
// end::imports[]

object Tracing {

  def config() {
    // tag::tracing-configure[]
    val config: Try[ClusterEnvironment] = ClusterEnvironment.builder
      .thresholdRequestTracerConfig(ThresholdRequestTracerConfig()
        .emitInterval(1.minutes)
        .kvThreshold(2.seconds))
      .build

    val cluster: Try[Cluster] = config.flatMap(c =>
      Cluster.connect("localhost", ClusterOptions
        .create("username", "password")
        .environment(c)))
    // end::tracing-configure[]
  }

  def main(args: Array[String]): Unit = {
    // Alternative configuration for Honeycomb, for local testing
//    val spanExporter = OtlpGrpcSpanExporter.builder()
//      .setEndpoint("https://api.honeycomb.io:443")
//      .addHeader("x-honeycomb-team", "HONEYCOMB_API_HERE")
//      .addHeader("x-honeycomb-dataset", "sdk")
//      .build()
//    val tracerProvider = SdkTracerProvider.builder
//      .setSampler(Sampler.alwaysOn)
//      .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
//      .build

    // tag::otel-configure[]

    // Configure OpenTelemetry
    val spanExporter = OtlpGrpcSpanExporter.getDefault
    val spanProcessor = BatchSpanProcessor.builder(spanExporter)
      .setScheduleDelay(java.time.Duration.ofMillis(100))
      .build

    val tracerProvider = SdkTracerProvider.builder
      .setSampler(Sampler.alwaysOn)
      .addSpanProcessor(spanProcessor)
      .build
    // end::otel-configure[]

    // tag::otel-use[]
    // Get a Couchbase RequestTracer from the OpenTelemetry TracerProvider
    val tracer: RequestTracer = OpenTelemetryRequestTracer.wrap(tracerProvider)

    // Use the RequestTracer
    val config: Try[ClusterEnvironment] = ClusterEnvironment.builder
      .requestTracer(tracer)
      .build

    val cluster: Try[Cluster] = config.flatMap(c =>
      Cluster.connect("localhost", ClusterOptions
        .create("Administrator", "password")
        .environment(c)))

    // end::otel-use[]

    // For local testing
    val coll = cluster.map(c => c.bucket("default").defaultCollection).get

    Range(0, 1000).foreach(v => {
      val span = tracer.requestSpan("test", null)

      coll.upsert("test", JsonObject.create, UpsertOptions().parentSpan(span).durability(Durability.Majority)).get

      span.status(StatusCode.ERROR)

      span.end()
    })
  }

  // tag::otel-options[]
  def getWithSpan(collection: Collection, span: io.opentelemetry.api.trace.Span) {
    collection.get("id", GetOptions()
      .parentSpan(OpenTelemetryRequestSpan.wrap(span)))
  }
  // end::otel-options[]

}
