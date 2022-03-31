package io.palyvos.provenance.missing;

import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import io.palyvos.provenance.missing.operator.MessageHandlerFilter;
import io.palyvos.provenance.missing.operator.PickedProvenanceFileSink;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.predicate.QueryGraphInfo;
import io.palyvos.provenance.util.ExperimentSettings;
import io.palyvos.provenance.util.ProvenanceActivator;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.eventtime.Watermark;
import org.apache.flink.api.common.eventtime.WatermarkGenerator;
import org.apache.flink.api.common.eventtime.WatermarkGeneratorSupplier;
import org.apache.flink.api.common.eventtime.WatermarkOutput;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.DeserializationSchema.InitializationContext;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.core.memory.DataInputDeserializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PickedProvenance {

  public static final String KAFKA_ANSWERS_TOPIC = "answers";
  public static final String KAFKA_QUERY_SOURCE_TOPIC = "source";
  public static final String KAFKA_PREDICATE_TOPIC = "predicate";
  // Can be increased for MA-1 but makes inspection more complex
  public static final int META_QUERY_SINK_PARALLELISM = 1;

  private static final Logger LOG = LoggerFactory.getLogger(PickedProvenance.class);
  public static final long HELPER_QUERY_TERMINATE_TIMESTAMP = Long.MAX_VALUE;
  public static final String DUMMY_WATERMARK = "TERMINATION_NOTICE";
  public static final String DROPPED_SINK_NAME = "DROPPED";
  public static final String META_ANSWERS_NAME = "META";
  public static final String QUERY_SINK_NAME = "SINK";

  public static void initKafka() {
    // Do nothing, initialization moved to external shell script
    // After that call, KAFKA_DATA_TOPIC and KAFKA_PREDICATE_TOPIC must exist
  }

  /**
   * Must be called from the operator thread.
   */
  public static String uniqueOperatorName(String operatorName) {
    final String jvm = ManagementFactory.getRuntimeMXBean().getName();
    final long tid = Thread.currentThread().getId();
    return String.format("%s:%s:%d", operatorName, jvm, tid);
  }


  private static KafkaSource<ProvenanceTupleContainer> newKafkaSource(
      KryoSerializer<ProvenanceTupleContainer> tupleContainerSerializer,
      ExperimentSettings settings) {
    KafkaSource<ProvenanceTupleContainer> whyNotSource = KafkaSource.<ProvenanceTupleContainer>builder()
        .setBootstrapServers(settings.kafkaHost())
        .setTopics(KAFKA_ANSWERS_TOPIC)
        .setStartingOffsets(OffsetsInitializer.earliest())
        .setDeserializer(
            new ProvenanceTupleContainerWithKeyDeserializationSchema(tupleContainerSerializer))
        .build();
    return whyNotSource;
  }

  public static void addLoggerQueryWithPredicate(StreamExecutionEnvironment env,
      ExperimentSettings settings,
      Predicate predicate,
      Collection<String> operators,
      QueryGraphInfo queryInfo) {
    Validate.notEmpty(operators, "Please provide operators");
    checkForDuplicates(operators);
    List<String> operatorsWithSink = new ArrayList<>(operators);
    operatorsWithSink.add(PickedProvenance.QUERY_SINK_NAME);
    Validate.notNull(predicate, "predicate");
    final KafkaSource<ProvenanceTupleContainer> whyNotSource =
        newKafkaSource(newSerializer(env), settings);
    final DataStream<ProvenanceTupleContainer> droppedTuples =
        kafkaSourceStreamWithUIDs(env, settings, whyNotSource);
    droppedTuples
        .addSink(new PickedProvenanceFileSink<>(DROPPED_SINK_NAME, settings,
            predicate, queryInfo.maxDelay()))
        .name(DROPPED_SINK_NAME)
        .setParallelism(META_QUERY_SINK_PARALLELISM);
  }

  private static void checkForDuplicates(Collection<String> operators) {
    Set<String> unique = new HashSet<>(operators);
    Validate.isTrue(unique.size() == operators.size(),
        "Operators contain duplicate values: %s", operators);
  }


  private static SingleOutputStreamOperator<ProvenanceTupleContainer> kafkaSourceStreamWithUIDs(
      StreamExecutionEnvironment env, ExperimentSettings settings,
      KafkaSource<ProvenanceTupleContainer> whyNotSource) {
    final MapFunction<ProvenanceTupleContainer, ProvenanceTupleContainer> uidAssigner =
        settings.provenanceActivator()
            .uidAssigner(ProvenanceActivator.sinkComponentIndex(settings, 0),
                settings.maxParallelism());
    return env.fromSource(whyNotSource, pickedProvenanceWatermarkStrategy(),
            "Kafka Source")
        .setParallelism(settings.kafkaPartitions())
        .filter(t -> !t.isWatermark()) // Watermark-tuples are already used in the wm strategy above
        .setParallelism(settings.kafkaPartitions())
        .map(uidAssigner)
        .setParallelism(settings.kafkaPartitions())
        .returns(ProvenanceTupleContainer.class)
        .filter(new MessageHandlerFilter(settings))
        .setParallelism(META_QUERY_SINK_PARALLELISM);
  }

  private static WatermarkStrategy<ProvenanceTupleContainer> pickedProvenanceWatermarkStrategy() {
    return WatermarkStrategy.forGenerator(
            (WatermarkGeneratorSupplier<ProvenanceTupleContainer>) context ->
                new PickedProvenanceWatermarkGenerator())
        .withTimestampAssigner((element, recordTimestamp) -> element.getTimestamp());
  }

  public static KryoSerializer<ProvenanceTupleContainer> newSerializer(
      StreamExecutionEnvironment env) {
    return new KryoSerializer<>(ProvenanceTupleContainer.class, env.getConfig());
  }

  private PickedProvenance() {

  }

  private static class ProvenanceTupleContainerWithKeyDeserializationSchema implements
      KafkaRecordDeserializationSchema<ProvenanceTupleContainer> {

    private transient StringDeserializer stringDeserializer;
    private final KryoSerializer<ProvenanceTupleContainer> tupleContainerSerializer;

    private ProvenanceTupleContainerWithKeyDeserializationSchema(
        KryoSerializer<ProvenanceTupleContainer> tupleContainerSerializer) {
      this.tupleContainerSerializer = tupleContainerSerializer;
    }

    @Override
    public void open(InitializationContext context) throws Exception {
      this.stringDeserializer = new StringDeserializer();
    }

    @Override
    public void deserialize(ConsumerRecord<byte[], byte[]> record,
        Collector<ProvenanceTupleContainer> out) throws IOException {
      final String key = stringDeserializer.deserialize(null, record.key());
      final ProvenanceTupleContainer<?> tupleContainer = tupleContainerSerializer.deserialize(
          new DataInputDeserializer(record.value()));
      tupleContainer.setOperator(key);
      tupleContainer.setTimestamp(record.timestamp());
      tupleContainer.setUID(0);
      out.collect(tupleContainer);
    }

    @Override
    public TypeInformation<ProvenanceTupleContainer> getProducedType() {
      return TypeInformation.of(ProvenanceTupleContainer.class);
    }
  }

  private static class PickedProvenanceWatermarkGenerator implements
      WatermarkGenerator<ProvenanceTupleContainer> {

    public static final long START_WATERMARK = -1L;
    private final Map<String, Long> operatorWatermarks = new HashMap<>();
    private long lastWatermark = START_WATERMARK;

    public PickedProvenanceWatermarkGenerator() {
    }

    @Override
    public void onEvent(ProvenanceTupleContainer event, long eventTimestamp,
        WatermarkOutput output) {
      if (event.isWatermark()) {
        if (terminationReached(event)) {
          output.emitWatermark(new Watermark(HELPER_QUERY_TERMINATE_TIMESTAMP));
          return;
        }
        // Each operator is a kafka topic
        // we want the min of all operators
        Long previousWatermark = operatorWatermarks.put(event.operator(), event.watermark());
        Validate.validState(previousWatermark == null || previousWatermark <= event.watermark(),
            "Decreasing watermark for operator %s: %d -> %d", event.operator(), previousWatermark,
            event.watermark());
        final long newWatermark = operatorWatermarks.values().stream().min(Long::compareTo).get();
        if (newWatermark > lastWatermark) {
          output.emitWatermark(new Watermark(newWatermark));
          lastWatermark = newWatermark;
        }
      }
    }

    @Override
    public void onPeriodicEmit(WatermarkOutput output) {
      output.emitWatermark(new Watermark(lastWatermark));
    }
  }

  public static boolean terminationReached(ProvenanceTupleContainer event) {
    return DUMMY_WATERMARK.equals(event.operator()) && event.getTimestamp() == HELPER_QUERY_TERMINATE_TIMESTAMP;
  }

  public static boolean terminationWatermark(long watermark) {
    return watermark == HELPER_QUERY_TERMINATE_TIMESTAMP;
  }
}
