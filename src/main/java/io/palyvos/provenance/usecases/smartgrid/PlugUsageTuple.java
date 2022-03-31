package io.palyvos.provenance.usecases.smartgrid;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.io.Serializable;
import java.util.Objects;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.flink.api.java.tuple.Tuple2;

public class PlugUsageTuple implements Serializable {

  public final double usage;
  public final int plugId;
  public final int householdId;
  public final int houseId;
  public final long stimulus;


  public PlugUsageTuple(double usage, int plugId, int householdId, int houseId,
      long stimulus) {
    this.usage = usage;
    this.plugId = plugId;
    this.householdId = householdId;
    this.houseId = houseId;
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
    PlugUsageTuple that = (PlugUsageTuple) o;
    return Double.compare(that.usage, usage) == 0 && plugId == that.plugId
        && householdId == that.householdId && houseId == that.houseId && stimulus == that.stimulus;
  }

  @Override
  public int hashCode() {
    return Objects.hash(usage, plugId, householdId, houseId, stimulus);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("usage", usage)
        .append("plugId", plugId)
        .append("householdId", householdId)
        .append("houseId", houseId)
        .toString();
  }


  public static class KryoSerializer extends
      com.esotericsoftware.kryo.Serializer<PlugUsageTuple>
      implements Serializable {

    @Override
    public void write(Kryo kryo, Output output, PlugUsageTuple object) {
      output.writeDouble(object.usage);
      output.writeInt(object.plugId);
      output.writeInt(object.householdId);
      output.writeInt(object.houseId);
      output.writeLong(object.stimulus);
    }

    @Override
    public PlugUsageTuple read(Kryo kryo, Input input, Class<PlugUsageTuple> type) {
      return new PlugUsageTuple(input.readDouble(), input.readInt(), input.readInt(),
          input.readInt(), input.readLong());
    }
  }
}
