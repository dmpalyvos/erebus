package io.palyvos.provenance.missing.predicate;

import java.util.Map;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;

public class PredicateKafkaSerializer implements Serializer<Predicate>, Deserializer<Predicate> {

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
  }

  @Override
  public Predicate deserialize(String topic, byte[] data) {
    return SerializationUtils.deserialize(data);
  }

  @Override
  public byte[] serialize(String topic, Predicate data) {
    return SerializationUtils.serialize(data);
  }

  @Override
  public void close() {
  }

}
