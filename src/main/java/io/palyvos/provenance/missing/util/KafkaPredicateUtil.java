package io.palyvos.provenance.missing.util;

import static org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG;

import io.palyvos.provenance.missing.PickedProvenance;
import io.palyvos.provenance.missing.operator.PredicateUpdatable;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.predicate.PredicateKafkaSerializer;
import io.palyvos.provenance.util.ExperimentSettings;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.Validate;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaPredicateUtil implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(KafkaPredicateUtil.class);

  private static final int PREDICATE_PROPAGATE_TIMEOUT_SECONDS = 10;
  private static final Set<String> registrations = new ConcurrentSkipListSet<>();
  private static final Duration KAFKA_POLL_TIMEOUT = Duration.ofSeconds(60);

  private final String operator;
  private volatile boolean closed;
  private transient KafkaConsumer<String, Predicate> consumer;
  private transient Thread runner;
  private final PredicateUpdatable component;
  private final ExperimentSettings settings;

  public KafkaPredicateUtil(String operator,
      PredicateUpdatable component, ExperimentSettings settings) {
    Validate.notBlank(operator, "blank operator");
    Validate.notNull(component, "component");
    Validate.notNull(component.category(), "null component category");
    // Prevent accidental duplicate registrations. Can only detect collisions in the same JVM
    if (!registrations.add(operator)) {
      LOG.warn(
          "Already registered operator {}. You can ignore this if parallelism > 1. Current Registrations: {}",
          operator, registrations);
    };
    this.operator = operator;
    this.component = component;
    this.settings = settings;
  }

  public void start() {
    runner = new Thread(this);
    runner.setName(KafkaPredicateUtil.class.getSimpleName() + "-" + operator);
    runner.setDaemon(true);
    runner.start();
  }

  @Override
  public void run() {
    try {
      initConsumer();
      while (!closed) {
        final ConsumerRecords<String, Predicate> records = consumer.poll(KAFKA_POLL_TIMEOUT);
        Validate.validState(records.count() <= 1,
            "Failed to set predicate from Kafka. At most one predicate should be sent at a time but %d found!",
            records.count());
        records.forEach(r -> setPredicate(r));
      }
    } catch (WakeupException e) {
      if (!closed) {
        LOG.error("Received wakeup but not because of close!");
      }
    } catch (Exception e) {
      LOG.error("Thread crashed", e);
    } finally {
      consumer.close();
    }
  }

  private void initConsumer() {
    // Each group reads the message once, and we want all operator instances to read the message
    // thus each operator needs to have a unique group.id
    // We concat the operator name with a UUID to handle multiple operator instances (e.g., parallelism)
    // that have the same name
    final String groupId = this.operator + "-" + UUID.randomUUID();
    LOG.info(
        "Creating kafka consumer to receive predicates with group.id = {}. No duplicates allowed!",
        groupId);
    Properties kafkaProperties = new Properties();
    kafkaProperties.put(BOOTSTRAP_SERVERS_CONFIG, settings.kafkaHost());
    kafkaProperties.put("group.id", groupId);
    consumer = new KafkaConsumer<>(kafkaProperties,
        new StringDeserializer(), new PredicateKafkaSerializer());
    consumer.subscribe(Arrays.asList(PickedProvenance.KAFKA_PREDICATE_TOPIC));
  }

  private void setPredicate(ConsumerRecord<String, Predicate> record) {
    final ComponentCategory newPredicateCategory = ComponentCategory.valueOf(record.key());
    if (!component.category().matches(newPredicateCategory)) {
      // Only set predicate if it targets this component category
      return;
    }
    final Predicate newPredicate = record.value();
    Validate.notNull(newPredicate, "Null predicate from kafka!");
    Validate.isTrue(newPredicate.isEnabled(),
        "Received new predicate from kafka but it is disabled!");
    LOG.info("Updating predicate to {} for {}", newPredicate.uid(), operator);
    this.component.updatePredicate(newPredicate);
  }

  public void close() {
    this.closed = true;
    if (consumer != null) {
      consumer.wakeup();
    }
  }

  public static Future<RecordMetadata> sendPredicate(
      ComponentCategory category,
      Predicate predicate, String kafkaHost) {
    Validate.notNull(category, "category");
    Validate.notNull(predicate, "predicate");
    Properties kafkaProperties = new Properties();
    kafkaProperties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
        kafkaHost);
    KafkaProducer<String, Predicate> producer = new KafkaProducer<>
        (kafkaProperties, new StringSerializer(), new PredicateKafkaSerializer());
    final Future<RecordMetadata> future = producer.send(
        new ProducerRecord<>(PickedProvenance.KAFKA_PREDICATE_TOPIC, null,
            System.currentTimeMillis(), category.name(), predicate));
    producer.flush();
    producer.close();
    return future;
  }

  public static void sendPredicateAsync(ComponentCategory category, Predicate newPredicate,
      long delaySeconds, String kafkaHost) {
    final Thread predicateSenderThread = new Thread(() -> {
      try {
        if (delaySeconds > 0) {
          Thread.sleep(TimeUnit.SECONDS.toMillis(delaySeconds));
        }
        sendPredicate(category, newPredicate, kafkaHost).get(
            PREDICATE_PROPAGATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (Exception e) {
        throw new RuntimeException("Failed to propagate predicate", e);
      }
    });
    predicateSenderThread.setName("DelayedPredicateSender");
    predicateSenderThread.setDaemon(true);
    predicateSenderThread.start();
  }

}
