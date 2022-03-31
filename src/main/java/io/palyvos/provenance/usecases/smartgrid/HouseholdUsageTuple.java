package io.palyvos.provenance.usecases.smartgrid;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.io.Serializable;
import java.util.Objects;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.flink.api.java.tuple.Tuple2;

public class HouseholdUsageTuple implements Serializable {

  public final long timestamp;
  public final int householdId;
  public final int houseId;
  public final double usage;
  public final long stimulus;


  public HouseholdUsageTuple(long timestamp, int householdId, int houseId, double usage,
      long stimulus) {
    this.timestamp = timestamp;
    this.householdId = householdId;
    this.houseId = houseId;
    this.usage = usage;
    this.stimulus = stimulus;
  }

  public Tuple2<Integer, Integer> householdKey() {
    return Tuple2.of(houseId, householdId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    HouseholdUsageTuple that = (HouseholdUsageTuple) o;
    return timestamp == that.timestamp && householdId == that.householdId && houseId == that.houseId
        && Double.compare(that.usage, usage) == 0 && stimulus == that.stimulus;
  }

  @Override
  public int hashCode() {
    return Objects.hash(timestamp, householdId, houseId, usage, stimulus);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("timestamp", timestamp)
        .append("usage", usage)
        .append("householdId", householdId)
        .append("houseId", houseId)
        .toString();
  }

  public static class KryoSerializer extends
      com.esotericsoftware.kryo.Serializer<HouseholdUsageTuple>
      implements Serializable {

    @Override
    public void write(Kryo kryo, Output output, HouseholdUsageTuple object) {
      output.writeLong(object.timestamp);
      output.writeInt(object.householdId);
      output.writeInt(object.houseId);
      output.writeDouble(object.usage);
      output.writeLong(object.stimulus);
    }

    @Override
    public HouseholdUsageTuple read(Kryo kryo, Input input, Class<HouseholdUsageTuple> type) {
      return new HouseholdUsageTuple(input.readLong(), input.readInt(), input.readInt(),
          input.readDouble(), input.readLong());
    }
  }
}
