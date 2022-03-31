package io.palyvos.provenance.usecases.cars.local;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.io.Serializable;
import java.util.UUID;

public class UUIDKryoSerializer extends Serializer<UUID> implements
    Serializable {


  @Override
  public void write(Kryo kryo, Output output, UUID object) {
    output.writeLong(object.getMostSignificantBits());
    output.writeLong(object.getLeastSignificantBits());
  }

  @Override
  public UUID read(Kryo kryo, Input input, Class<UUID> type) {
    return new UUID(input.readLong(), input.readLong());
  }
}
