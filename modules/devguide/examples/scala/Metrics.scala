// tag::imports[]

import com.couchbase.client.metrics.opentelemetry.OpenTelemetryMeter
import com.couchbase.client.scala.env.{ClusterEnvironment, LoggingMeterConfig}
import com.couchbase.client.scala.{Cluster, ClusterOptions}
import io.opentelemetry.api.metrics.GlobalMeterProvider

import scala.concurrent.duration._
import scala.util.Try
// end::imports[]

object Metrics {
  {
    // tag::metrics-enable[]
    val config: Try[ClusterEnvironment] = ClusterEnvironment.builder
      .loggingMeterConfig(LoggingMeterConfig()
        .enabled(true))
      .build

    val cluster: Try[Cluster] = config.flatMap(c =>
      Cluster.connect("localhost", ClusterOptions
        .create("username", "password")
        .environment(c)))
    // end::metrics-enable[]
  }


  {
    // tag::metrics-enable-custom[]
    val config: Try[ClusterEnvironment] = ClusterEnvironment.builder
      .loggingMeterConfig(LoggingMeterConfig()
        .enabled(true)
        .emitInterval(10.minutes))
      .build
    // end::metrics-enable-custom[]
  }

  {
    // tag::metrics-otel[]
    val config: Try[ClusterEnvironment] = ClusterEnvironment.builder
      .meter(OpenTelemetryMeter.wrap(GlobalMeterProvider.get()))
      .build
    // end::metrics-otel[]
  }

}
