package io.palyvos.provenance.usecases.smartgrid;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.io.Serializable;
import java.util.regex.Pattern;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;

public class SmartGridTuple implements Serializable {

  private static final Pattern DELIMITER_PATTERN = Pattern.compile(",");

  public final int measurementId;
  public final long timestamp;
  public final double value;
  public final boolean isLoad;
  public final int plugId;
  public final int householdId;
  public final int houseId;
  public final long stimulus;

  public static SmartGridTuple fromReading(String reading) {
    try {
      String[] tokens = DELIMITER_PATTERN.split(reading.trim());
      final int measurementId = Integer.valueOf(tokens[0]);
      final long timestamp = 1000 * Long.valueOf(tokens[1]);
      final double value = Double.valueOf(tokens[2]);
      final boolean isLoad = Integer.valueOf(tokens[3]) != 0;
      final int plugId = Integer.valueOf(tokens[4]);
      final int householdId = Integer.valueOf(tokens[5]);
      final int houseId = Integer.valueOf(tokens[6]);
      final long stimulus = System.currentTimeMillis();
      return new SmartGridTuple(measurementId, timestamp, value, isLoad, plugId, householdId,
          houseId, stimulus);
    } catch (Exception exception) {
      throw new IllegalArgumentException(String.format("Failed to parse reading: %s", reading),
          exception);
    }
  }

  public boolean isLoad() {
    return isLoad;
  }

  public boolean isWork() {
    return !isLoad;
  }

  public SmartGridTuple(int measurementId, long timestamp, double value, boolean isLoad,
      int plugId, int householdId, int houseId, long stimulus) {
    this.measurementId = measurementId;
    this.timestamp = timestamp;
    this.value = value;
    this.isLoad = isLoad;
    this.plugId = plugId;
    this.householdId = householdId;
    this.houseId = houseId;
    this.stimulus = stimulus;
  }

  public Tuple3<Integer, Integer, Integer> plugKey() {
    return Tuple3.of(houseId, householdId, plugId);
  }

  public Tuple2<Integer, Integer> householdKey() {
    return Tuple2.of(houseId, householdId);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("measurementId", measurementId)
        .append("timestamp", timestamp)
        .append("value", value)
        .append("property", isLoad)
        .append("plugId", plugId)
        .append("householdId", householdId)
        .append("houseId", houseId)
        .toString();
  }

  public static class KryoSerializer extends com.esotericsoftware.kryo.Serializer<SmartGridTuple>
      implements Serializable {

    @Override
    public void write(Kryo kryo, Output output, SmartGridTuple object) {
      output.writeInt(object.measurementId);
      output.writeLong(object.timestamp);
      output.writeDouble(object.value);
      output.writeBoolean(object.isLoad);
      output.writeInt(object.plugId);
      output.writeInt(object.householdId);
      output.writeInt(object.houseId);
      output.writeLong(object.stimulus);
    }

    @Override
    public SmartGridTuple read(Kryo kryo, Input input, Class<SmartGridTuple> type) {
      return new SmartGridTuple(input.readInt(), input.readLong(), input.readDouble(),
          input.readBoolean(), input.readInt(), input.readInt(), input.readInt(), input.readLong());
    }
  }
}
