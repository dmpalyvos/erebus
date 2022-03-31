package io.palyvos.provenance.usecases.synthetic.provenance;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.palyvos.provenance.genealog.GenealogData;
import io.palyvos.provenance.genealog.GenealogDataSerializer;
import io.palyvos.provenance.genealog.GenealogTuple;
import io.palyvos.provenance.genealog.GenealogTupleType;
import io.palyvos.provenance.util.IncreasingUIDGenerator;
import java.io.Serializable;
import java.util.function.Supplier;

public class SyntheticSinkTuple implements Serializable, GenealogTuple {

  private GenealogData genealogData;
  private long timestamp;
  private long stimulus;

  static class DummySinkTupleSupplier implements Supplier<SyntheticSinkTuple>,
      Serializable {

    @Override
    public SyntheticSinkTuple get() {
      return new SyntheticSinkTuple();
    }
  }

  public static DummySinkTupleSupplier supplier() {
    return new DummySinkTupleSupplier();
  }

  public SyntheticSinkTuple() {
    initGenealog(GenealogTupleType.REMOTE);
  }

  private SyntheticSinkTuple(GenealogData data, long timestamp, long stimulus) {
    this.genealogData = data;
    this.timestamp = timestamp;
    this.stimulus = stimulus;
  }

  @Override
  public void initGenealog(GenealogTupleType tupleType) {
    genealogData = new GenealogData();
    genealogData.init(tupleType);
  }

  @Override
  public GenealogData getGenealogData() {
    return genealogData;
  }


  @Override
  public long getTimestamp() {
    return timestamp;
  }

  @Override
  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  @Override
  public long getStimulus() {
    return stimulus;
  }

  @Override
  public void setStimulus(long stimulus) {
    this.stimulus = stimulus;
  }

  @Override
  public String toString() {
    return String.format("K-%d,%s", timestamp, IncreasingUIDGenerator.asString(getUID()));
  }

  public static class KryoSerializer extends Serializer<SyntheticSinkTuple> implements
      Serializable {

    private final GenealogDataSerializer dataSerializer;

    public KryoSerializer(GenealogDataSerializer dataSerializer) {
      this.dataSerializer = dataSerializer;
    }

    @Override
    public void write(Kryo kryo, Output output, SyntheticSinkTuple tuple) {
      dataSerializer.write(kryo, output, tuple.genealogData);
      output.writeLong(tuple.timestamp);
      output.writeLong(tuple.stimulus);
    }

    @Override
    public SyntheticSinkTuple read(Kryo kryo, Input input, Class<SyntheticSinkTuple> aClass) {
      return new SyntheticSinkTuple(dataSerializer.read(kryo, input, GenealogData.class),
          input.readLong(), input.readLong());
    }
  }

}
