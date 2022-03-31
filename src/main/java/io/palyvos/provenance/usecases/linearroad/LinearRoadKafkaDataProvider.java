package io.palyvos.provenance.usecases.linearroad;

import static io.palyvos.provenance.missing.PickedProvenance.KAFKA_QUERY_SOURCE_TOPIC;
import static org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG;

import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import io.palyvos.provenance.missing.util.KafkaSendEvent;
import io.palyvos.provenance.missing.util.KafkaSendEventHandler;
import io.palyvos.provenance.util.CountStat;
import io.palyvos.provenance.util.ExperimentSettings;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

public class LinearRoadKafkaDataProvider {


  private static final int DISRUPTOR_SIZE = 2 << 13;
  private static final EventTranslatorOneArg<KafkaSendEvent<String>, ? super ProducerRecord<String, String>> KAFKA_SEND_EVENT_TRANSLATOR =
      (event, sequence, record) -> event.set(record);

  public static void main(String[] args) throws Exception {
    ExperimentSettings settings = ExperimentSettings.newInstance(args);
    final String inputFile = settings.inputFile("txt");
    final Properties kafkaProperties = new Properties();
    kafkaProperties.put(BOOTSTRAP_SERVERS_CONFIG, settings.kafkaHost());
    final KafkaProducer<String, String> producer = new KafkaProducer<>(kafkaProperties,
        new StringSerializer(), new StringSerializer());
    final Disruptor<KafkaSendEvent<String>> disruptor = new Disruptor<>(
        KafkaSendEvent::new, DISRUPTOR_SIZE,
        DaemonThreadFactory.INSTANCE);
    disruptor.handleEventsWith(
        PartitionedKafkaEventHandler.handlers(producer, settings.kafkaPartitions()));
    disruptor.start();
    CountStat throughputStatistic = new CountStat(
        settings.kafkaThroughputFile(0, "KAFKA-SOURCE"),
        settings.autoFlush());

    System.out.println(String.format("Starting kafka producer reading from %s", inputFile));
    int partition = 0;
    try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {
      String line = br.readLine();
      while (line != null) {
        throughputStatistic.increase(1);
        final ProducerRecord<String, String> record = new ProducerRecord<>(KAFKA_QUERY_SOURCE_TOPIC,
            partition, null, line.trim());
        disruptor.getRingBuffer().publishEvent(KAFKA_SEND_EVENT_TRANSLATOR, record);
        partition = (partition + 1) % settings.kafkaPartitions();
        line = br.readLine();
      }
    }
    System.out.println(String.format("Kafka producer done"));
  }

  private static class PartitionedKafkaEventHandler<T> extends KafkaSendEventHandler<T> {

    private final int partition;

    public static <V> PartitionedKafkaEventHandler<V>[] handlers(KafkaProducer<String, V> producer,
        int nPartitions) {
      final PartitionedKafkaEventHandler<V>[] handlers = new PartitionedKafkaEventHandler[nPartitions];
      for (int i = 0; i < handlers.length; i++) {
        handlers[i] = new PartitionedKafkaEventHandler<>(producer, i);
      }
      return handlers;
    }

    public PartitionedKafkaEventHandler(KafkaProducer<String, T> producer, int partition) {
      super(producer);
      this.partition = partition;
    }

    @Override
    public void onEvent(KafkaSendEvent<T> kafkaSendEvent, long l, boolean b) throws Exception {
      try {
        final ProducerRecord<String, T> record = kafkaSendEvent.record;
        if (record == null || record.partition() != partition) {
          return;
        }
      }
      catch (NullPointerException e) {
        System.out.format("NullPointerException at producer for partition %d\n", partition);
      }
      super.onEvent(kafkaSendEvent, l, b);
    }
  }

}
