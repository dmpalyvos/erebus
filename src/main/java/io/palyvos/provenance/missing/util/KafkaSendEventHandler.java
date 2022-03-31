package io.palyvos.provenance.missing.util;

import com.lmax.disruptor.EventHandler;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaSendEventHandler<T> implements EventHandler<KafkaSendEvent<T>> {

  private static final Logger LOG = LoggerFactory.getLogger(KafkaSendEventHandler.class);
  private final KafkaProducer<String, T> producer;

  public KafkaSendEventHandler(KafkaProducer<String, T> producer) {
    this.producer = producer;
  }

  @Override
  public void onEvent(KafkaSendEvent<T> kafkaSendEvent, long l, boolean b) throws Exception {
    try {
      producer.send(kafkaSendEvent.record);
    } catch (Exception e) {
      LOG.error("Failed to send record {} to kafka: {}", kafkaSendEvent.record, e);
    }
    kafkaSendEvent.clear();
  }
}
