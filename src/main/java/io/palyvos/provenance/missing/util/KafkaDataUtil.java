package io.palyvos.provenance.missing.util;

import static io.palyvos.provenance.missing.PickedProvenance.DUMMY_WATERMARK;
import static io.palyvos.provenance.missing.PickedProvenance.HELPER_QUERY_TERMINATE_TIMESTAMP;
import static io.palyvos.provenance.missing.PickedProvenance.KAFKA_ANSWERS_TOPIC;
import static org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.util.CountStat;
import io.palyvos.provenance.util.ExperimentSettings;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ThreadFactory;
import org.apache.commons.lang3.Validate;
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaDataUtil {

  private static final EventTranslatorOneArg<KafkaSendEvent<ProvenanceTupleContainer>, ProducerRecord<String, ProvenanceTupleContainer>> KAFKA_SEND_EVENT_TRANSLATOR
      = (event, sequence, record) -> event.set(record);
  private static final Logger LOG = LoggerFactory.getLogger(PastCheckerUtil.class);
  private final KafkaProducer<String, ProvenanceTupleContainer> producer;
  private final CountStat predicateInStat;
  private final CountStat predicateOutStat;
  private final int kafkaPartitions;
  private int kafkaPartitionIndex;
  private final Disruptor<KafkaSendEvent<ProvenanceTupleContainer>> disruptor;

  public KafkaDataUtil(String operatorName,
      KryoSerializer<ProvenanceTupleContainer> serializer,
      ExperimentSettings settings) {
    Validate.notBlank(operatorName, "blank operatorName");
    Validate.notNull(serializer, "serializer");
    Validate.notNull(settings, "settings");
    producer = newProducer(serializer, settings);
    List<String> statFiles = settings.predicateInOutFiles(operatorName);
    this.predicateInStat = new CountStat(statFiles.get(0), settings.autoFlush());
    this.predicateOutStat = new CountStat(statFiles.get(1), settings.autoFlush());
    this.kafkaPartitions = settings.kafkaPartitions();
    this.disruptor = new Disruptor<>(KafkaSendEvent::new, settings.kafkaSenderQueueSize(),
        namedDaemonThreadFactory(operatorName),
        ProducerType.SINGLE, new BlockingWaitStrategy());
    this.disruptor.handleEventsWith(new KafkaSendEventHandler<>(producer));
    this.disruptor.start();
  }

  private ThreadFactory namedDaemonThreadFactory(String operatorName) {
    return r -> {
      Thread newThread = new Thread(r);
      newThread.setDaemon(true);
      newThread.setName(
          String.format("DisruptorConsumer-%s-%d-%d", operatorName, Thread.currentThread().getId(),
              newThread.getId()));
      return newThread;
    };
  }

  private KafkaProducer<String, ProvenanceTupleContainer> newProducer(
      KryoSerializer<ProvenanceTupleContainer> serializer,
      ExperimentSettings settings) {
    Properties kafkaProperties = new Properties();
    kafkaProperties.put(BOOTSTRAP_SERVERS_CONFIG, settings.kafkaHost());
    kafkaProperties.put("linger.ms", 10);
    return new KafkaProducer<>(kafkaProperties, new StringSerializer(),
        new TupleContainerKafkaSerializer(serializer));
  }

  public void trySendTuple(String operator, long timestamp, ProvenanceTupleContainer<?> tuple,
      Predicate predicate) {
    predicateInStat.increase(1);
    int predicateOut = 0;
    if (predicate.isEnabled() && predicate.evaluate(tuple, timestamp)) {
      sendTupleAsync(operator, timestamp, tuple);
      predicateOut += 1;
    }
    // Call increase even with 0 outputs,
    // otherwise nothing will be outputted if predicate is always false
    predicateOutStat.increase(predicateOut);
  }

  public void sendTupleAsync(String operator, long timestamp, ProvenanceTupleContainer<?> tuple) {
    sendAsync(tupleRecord(timestamp, operator, tuple));
  }

  public void sendTupleSync(String operator, long timestamp, ProvenanceTupleContainer<?> tuple) {
    sendSync(tupleRecord(timestamp, operator, tuple));
  }

  private ProducerRecord<String, ProvenanceTupleContainer> tupleRecord(long timestamp,
      String operator, ProvenanceTupleContainer<?> tuple) {
    return new ProducerRecord<>(KAFKA_ANSWERS_TOPIC, partitionRoundRobin(), timestamp, operator,
        tuple);
  }

  public void sendWatermark(String operator, long watermark) {
    if (watermark <= 0) {
      LOG.warn("Ignoring negative watermark from component {}: {}", operator, watermark);
      return;
    }
    final ProducerRecord<String, ProvenanceTupleContainer> record = new ProducerRecord<>(
        KAFKA_ANSWERS_TOPIC, partitionRoundRobin(), watermark, operator,
        ProvenanceTupleContainer.newWatermark((watermark)));
    sendAsync(record);
  }

  public void sendMessage(String operator, String message, long timestamp) {
    final long kafkaTimestamp = timestamp > 0 ? timestamp : 0;
    final ProducerRecord<String, ProvenanceTupleContainer> record = new ProducerRecord<>(
        KAFKA_ANSWERS_TOPIC, partitionRoundRobin(), kafkaTimestamp, operator,
        ProvenanceTupleContainer.newMessage(message));
    sendAsync(record);
  }

  public void sendTermination() {
    sendAsync(tupleRecord(
        HELPER_QUERY_TERMINATE_TIMESTAMP, DUMMY_WATERMARK,
        ProvenanceTupleContainer.newWatermark(HELPER_QUERY_TERMINATE_TIMESTAMP)));
  }

  private void sendAsync(ProducerRecord<String, ProvenanceTupleContainer> record) {
    disruptor.getRingBuffer().publishEvent(KAFKA_SEND_EVENT_TRANSLATOR, record);
  }

  private void sendSync(ProducerRecord<String, ProvenanceTupleContainer> record) {
    producer.send(record);
  }

  private int partitionRoundRobin() {
    kafkaPartitionIndex = (kafkaPartitionIndex + 1) % kafkaPartitions;
    return kafkaPartitionIndex;
  }

  public void close() {
  }

}
