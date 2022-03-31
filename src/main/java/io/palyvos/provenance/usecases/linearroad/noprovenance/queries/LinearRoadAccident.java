package io.palyvos.provenance.usecases.linearroad.noprovenance.queries;

import static io.palyvos.provenance.missing.PickedProvenance.KAFKA_QUERY_SOURCE_TOPIC;
import static io.palyvos.provenance.usecases.linearroad.LinearRoadConstants.ACCIDENT_WINDOW_SIZE;
import static io.palyvos.provenance.usecases.linearroad.LinearRoadConstants.ACCIDENT_WINDOW_SLIDE;
import static io.palyvos.provenance.usecases.linearroad.LinearRoadConstants.STOPPED_VEHICLE_WINDOW_SIZE;
import static io.palyvos.provenance.usecases.linearroad.LinearRoadConstants.STOPPED_VEHICLE_WINDOW_SLIDE;

import io.palyvos.provenance.usecases.linearroad.noprovenance.AccidentTuple;
import io.palyvos.provenance.usecases.linearroad.noprovenance.LinearRoadAccidentAggregate;
import io.palyvos.provenance.usecases.linearroad.noprovenance.LinearRoadInputTuple;
import io.palyvos.provenance.usecases.linearroad.noprovenance.LinearRoadVehicleAggregate;
import io.palyvos.provenance.usecases.linearroad.noprovenance.VehicleTuple;
import io.palyvos.provenance.util.ExperimentSettings;
import io.palyvos.provenance.util.FlinkSerializerActivator;
import io.palyvos.provenance.util.LatencyLoggingSink;
import io.palyvos.provenance.util.ThroughputLoggingFilter;
import java.io.IOException;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.DeserializationSchema.InitializationContext;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;

public class LinearRoadAccident {


  public static void main(String[] args) throws Exception {
    ExperimentSettings settings = ExperimentSettings.newInstance(args);

    final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
    env.getConfig().enableObjectReuse();

    FlinkSerializerActivator.NOPROVENANCE.activate(env, settings)
        .register(LinearRoadInputTuple.class, new LinearRoadInputTuple.KryoSerializer());
    env.registerType(AccidentTuple.class);
    env.registerType(VehicleTuple.class);

    final int sourceTaskParallelism = settings.kafkaPartitions(); // We are reading from kafka
    env.fromSource(linearRoadKafkaSource(settings),
            WatermarkStrategy.<LinearRoadInputTuple>forMonotonousTimestamps()
                .withTimestampAssigner((element, recordTimestamp) -> element.getTimestamp()), "SOURCE")
        .setParallelism(sourceTaskParallelism)
        .filter(new ThroughputLoggingFilter<>("SOURCE", settings))
        .setParallelism(sourceTaskParallelism)
        .filter(t -> t.getType() == 0)
        .setParallelism(sourceTaskParallelism)
        .filter(t -> t.getSpeed() == 0)
        .setParallelism(sourceTaskParallelism)
        .keyBy(t -> t.vid)
        .window(SlidingEventTimeWindows.of(STOPPED_VEHICLE_WINDOW_SIZE,
            STOPPED_VEHICLE_WINDOW_SLIDE))
        .aggregate(new LinearRoadVehicleAggregate())
        .filter(t -> t.getReports() == 4 && t.isUniquePosition())
        .keyBy(t -> t.getLatestPos())
        .window(SlidingEventTimeWindows.of(ACCIDENT_WINDOW_SIZE,
            ACCIDENT_WINDOW_SLIDE))
        .aggregate(new LinearRoadAccidentAggregate())
        .filter(t -> t.count > 1)
        .addSink(LatencyLoggingSink.newInstance(t -> t.stimulus, settings));

    env.execute("LinearRoadAccident");

  }

  public static KafkaSource<LinearRoadInputTuple> linearRoadKafkaSource(ExperimentSettings settings) {
    return KafkaSource.<LinearRoadInputTuple>builder()
        .setBootstrapServers(settings.kafkaHost())
        .setTopics(KAFKA_QUERY_SOURCE_TOPIC)
        .setStartingOffsets(OffsetsInitializer.earliest())
        .setDeserializer(new LinearRoadInputTupleDeserializationSchema())
        .build();
  }

  public static class LinearRoadInputTupleDeserializationSchema implements
      KafkaRecordDeserializationSchema<LinearRoadInputTuple> {

    private transient StringDeserializer deserializer;

    @Override
    public void open(InitializationContext context) throws Exception {
      this.deserializer = new StringDeserializer();
    }

    @Override
    public void deserialize(ConsumerRecord<byte[], byte[]> record,
        Collector<LinearRoadInputTuple> out) throws IOException {
      final String reading = deserializer.deserialize(null, record.value());
      out.collect(LinearRoadInputTuple.fromReading(reading));
    }

    @Override
    public TypeInformation<LinearRoadInputTuple> getProducedType() {
      return TypeInformation.of(LinearRoadInputTuple.class);
    }
  }


}
