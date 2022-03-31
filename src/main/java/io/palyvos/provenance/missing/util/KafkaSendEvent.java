package io.palyvos.provenance.missing.util;

import org.apache.kafka.clients.producer.ProducerRecord;

public class KafkaSendEvent<T> {


  public ProducerRecord<String, T> record;

  public void set(ProducerRecord<String, T> record) {
    this.record = record;
  }

  void clear() {
    this.record = null;
  }

  @Override
  public String toString() {
    return String.valueOf(record);
  }
}
