package io.palyvos.provenance.missing.util;

import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import java.io.Serializable;
import java.util.Map;
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer;
import org.apache.flink.core.memory.DataOutputSerializer;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

public class TupleContainerKafkaSerializer implements Serializer<ProvenanceTupleContainer>,
    Serializable {

  private final ThreadLocal<KryoSerializer<ProvenanceTupleContainer>> localSerializer;

  public TupleContainerKafkaSerializer(
      KryoSerializer<ProvenanceTupleContainer> serializer) {
    localSerializer = ThreadLocal.withInitial(() -> serializer.duplicate());
  }

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {

  }

  @Override
  public byte[] serialize(String topic, ProvenanceTupleContainer data) {
    try {
      if (data == null) {
        throw new NullPointerException("null data");
      }
      DataOutputSerializer view = new DataOutputSerializer(1024);
      localSerializer.get().serialize(data, view);
      return view.getSharedBuffer();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public byte[] serialize(String topic, Headers headers, ProvenanceTupleContainer data) {
    return serialize(topic, data);
  }

  @Override
  public void close() {

  }


}
