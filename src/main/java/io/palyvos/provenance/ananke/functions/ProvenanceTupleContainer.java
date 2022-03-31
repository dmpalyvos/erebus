package io.palyvos.provenance.ananke.functions;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.palyvos.provenance.genealog.GenealogData;
import io.palyvos.provenance.genealog.GenealogTuple;
import io.palyvos.provenance.genealog.GenealogTupleType;
import io.palyvos.provenance.missing.PickedProvenance;
import io.palyvos.provenance.util.TimestampedUIDTuple;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.Validate;

public class ProvenanceTupleContainer<T> implements GenealogTuple {

  private static final String UNDEFINED_OPERATOR_NAME = "UNDEFINED";
  public static final byte MESSAGE_TYPE = (byte) 0;
  public static final byte WATERMARK_TYPE = (byte) 1;
  public static final byte GENERAL_CONTAINER_TYPE = (byte) 2;
  private GenealogData genealogData;
  private long timestamp;
  private long stimulus;
  private final T tuple;

  private int timesMatched;
  private int timesJoined;
  private long watermark = -1;
  private String operator = UNDEFINED_OPERATOR_NAME;
  private byte discardedReason = DiscardedReason.FILTERED_OUT.code;
  private String message = "";

  public static <T> ProvenanceTupleContainer newWatermark(long watermark) {
    Validate.isTrue(watermark > 0, "negative watermark not allowed");
    ProvenanceTupleContainer<T> object = new ProvenanceTupleContainer<>(null);
    object.watermark = watermark;
    return object;
  }

  public static <T> ProvenanceTupleContainer<T> newMessage(String message) {
    Validate.notBlank(message, "blank message");
    ProvenanceTupleContainer<T> object = new ProvenanceTupleContainer<>(null);
    object.setMessage(message);
    return object;
  }

  /**
   * For testing purposes
   */
  @Deprecated
  public static <T extends GenealogTuple> ProvenanceTupleContainer<T> fromGenealogTuple(T tuple) {
    ProvenanceTupleContainer<T> object = new ProvenanceTupleContainer<>(tuple);
    object.setGenealogData(tuple.getGenealogData());
    object.setTimestamp(tuple.getTimestamp());
    object.setStimulus(tuple.getStimulus());
    return object;
  }

  public ProvenanceTupleContainer(T tuple) {
    this.tuple = tuple;
  }


  @Override
  public void initGenealog(GenealogTupleType tupleType) {
    genealogData = new GenealogData();
    genealogData.init(tupleType);
  }

  public T tuple() {
    return tuple;
  }

  public void copyTimes(GenealogTuple other) {
    this.timestamp = other.getTimestamp();
    this.stimulus = other.getStimulus();
  }

  public void copyTimes(GenealogTuple first, GenealogTuple second) {
    this.timestamp = Math.max(first.getTimestamp(), second.getTimestamp());
    this.stimulus = Math.max(first.getStimulus(), second.getStimulus());
  }

  @Override
  public long getStimulus() {
    return stimulus;
  }

  @Override
  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  public void setStimulus(long stimulus) {
    this.stimulus = stimulus;
  }

  @Override
  public GenealogData getGenealogData() {
    return genealogData;
  }

  public boolean isWatermark() {
    return watermark > 0;
  }

  public boolean isMessage() {
    return !message.isEmpty();
  }

  private void setGenealogData(GenealogData genealogData) {
    this.genealogData = genealogData;
  }


  @Override
  public long getUID() {
    return type() == GENERAL_CONTAINER_TYPE ? GenealogTuple.super.getUID() : 0;
  }

  @Override
  public void setUID(long uid) {
    if (type() == GENERAL_CONTAINER_TYPE) {
      GenealogTuple.super.setUID(uid);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProvenanceTupleContainer<?> that = (ProvenanceTupleContainer<?>) o;
    return watermark == that.watermark && Objects.equals(tuple, that.tuple);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tuple, watermark);
  }

  @Override
  public String toString() {
    switch (type()) {
      case MESSAGE_TYPE:
        return message;
      case WATERMARK_TYPE:
        return "Watermark=" + watermark;
      case GENERAL_CONTAINER_TYPE:
        return String.valueOf(tuple);
      default:
        return "ProvenanceTupleContainer of unknown type";
    }
  }

  public void setMatched() {
    this.timesMatched += 1;
  }

  public void setDiscardedReason(DiscardedReason reason) {
    this.discardedReason = reason.code;
  }

  public void setJoined() {
    this.timesJoined += 1;
  }

  public boolean isJoined() {
    return this.timesJoined > 0;
  }

  public boolean isMatched() {
    return this.timesMatched > 0;
  }

  public void clearJoinedMatched() {
    this.timesMatched = 0;
    this.timesJoined = 0;
  }

  public long watermark() {
    return watermark;
  }

  public void setOperator(String name) {
    this.operator = name;
  }

  @Override
  public String operator() {
    return operator;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    Validate.notNull(message, "message");
    this.message = message;
  }

  public static class KryoSerializer extends Serializer<ProvenanceTupleContainer>
      implements Serializable {

    private final Serializer<GenealogData> genealogDataSerializer;
    private final CustomGenericSerializer customGenericSerializer;
    private Map<String, Byte> operatorToByte = new HashMap<>();
    private Map<Byte, String> byteToOperator = new HashMap<>();
    private int nOperators = Byte.MIN_VALUE;

    public KryoSerializer(
        Serializer<GenealogData> genealogDataSerializer,
        CustomGenericSerializer customGenericSerializer) {
      this.genealogDataSerializer = genealogDataSerializer;
      this.customGenericSerializer = customGenericSerializer;
      // For tuple containers that have no operator defined
      registerOperator(null);
      registerOperator(UNDEFINED_OPERATOR_NAME);
    }

    @Override
    public void write(Kryo kryo, Output output, ProvenanceTupleContainer object) {
      output.writeByte(object.type());
      switch (object.type()) {
        case MESSAGE_TYPE:
          output.writeString(object.message);
          break;
        case WATERMARK_TYPE:
          output.writeLong(object.watermark);
          break;
        case GENERAL_CONTAINER_TYPE:
          writeTupleContainer(kryo, output, object);
          break;
        default:
          throw new IllegalStateException(String.format("Unknown type: %d", object.type()));
      }
    }

    @Override
    public ProvenanceTupleContainer read(Kryo kryo, Input input,
        Class<ProvenanceTupleContainer> type) {
      final byte containerType = input.readByte();
      switch (containerType) {
        case MESSAGE_TYPE:
          return ProvenanceTupleContainer.newMessage(input.readString());
        case WATERMARK_TYPE:
          return ProvenanceTupleContainer.newWatermark(input.readLong());
        case GENERAL_CONTAINER_TYPE:
          return readTupleContainer(kryo, input);
        default:
          throw new IllegalStateException(String.format("Unknown type: %d", containerType));
      }
    }

    private void writeTupleContainer(Kryo kryo, Output output, ProvenanceTupleContainer object) {
      customGenericSerializer.write(kryo, output, object.tuple);
      genealogDataSerializer.write(kryo, output, object.genealogData);
      output.writeLong(object.timestamp);
      output.writeLong(object.stimulus);
      output.writeByte(operatorToByte(object.operator));
      output.writeByte(object.discardedReason);
    }

    private ProvenanceTupleContainer readTupleContainer(Kryo kryo, Input input) {
      Object tuple = customGenericSerializer.read(kryo, input);
      ProvenanceTupleContainer tupleContainer = new ProvenanceTupleContainer(tuple);
      tupleContainer.setGenealogData(genealogDataSerializer.read(kryo, input, GenealogData.class));
      tupleContainer.setTimestamp(input.readLong());
      tupleContainer.setStimulus(input.readLong());
      tupleContainer.setOperator(byteToOperator(input.readByte()));
      tupleContainer.discardedReason = input.readByte();
      return tupleContainer;
    }

    public void registerOperator(String operator) {
      Validate.isTrue(nOperators < Byte.MAX_VALUE, "Up to 255 operators supported!");
      byte currentByte = (byte) nOperators;
      Validate.isTrue(operatorToByte.put(operator, currentByte) == null,
          "Operator %s already registered!", operator);
      Validate.isTrue(byteToOperator.put(currentByte, operator) == null,
          "Byte code already registered");
      nOperators += 1;
    }

    public void registerOperators(String... operators) {
      Validate.notEmpty(operators, "No operator provided");
      for (String operator : operators) {
        Validate.notBlank(operator, "Blank operator name");
        registerOperator(operator);
      }
      registerOperator(PickedProvenance.QUERY_SINK_NAME);
    }


    public byte operatorToByte(String operator) {
      final Byte code = operatorToByte.get(operator);
      Validate.notNull(code,
          "No registration for operator with name: %s\nAvailable registrations: %s", operator,
          operatorToByte);
      return code;
    }

    public String byteToOperator(byte readByte) {
      return byteToOperator.get(readByte);
    }

  }


  private byte type() {
    if (isMessage()) {
      Validate.isTrue(!isWatermark(), "Cannot be both message and watermark!");
      return MESSAGE_TYPE;
    } else if (isWatermark()) {
      return WATERMARK_TYPE;
    } else {
      return GENERAL_CONTAINER_TYPE;
    }
  }

  @Override
  public TimestampedUIDTuple sourceTuple() {
    //Will FAIL except if the stream has the correct type
    //Not validating for performance reasons
    return (TimestampedUIDTuple) tuple;
  }

  public enum DiscardedReason {
    FILTERED_OUT((byte) 0),
    NOT_JOINED((byte) 1),
    JOINED_NOT_MATCHED((byte) 2);
    private final byte code;

    DiscardedReason(byte code) {
      this.code = code;
    }

  }
}

