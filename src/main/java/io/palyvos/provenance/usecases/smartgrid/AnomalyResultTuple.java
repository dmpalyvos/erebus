package io.palyvos.provenance.usecases.smartgrid;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.io.Serializable;
import java.util.Objects;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class AnomalyResultTuple implements Serializable {

  public final long plugUsage;
  public final double averageUsage;
  public final double difference;
  public final int plugId;
  public final int householdId;
  public final int houseId;
  public final long stimulus;

  public AnomalyResultTuple(int plugId, int householdId, int houseId, long plugUsage,
      double averageUsage, double difference, long stimulus) {
    this.plugId = plugId;
    this.householdId = householdId;
    this.houseId = houseId;
    this.plugUsage = plugUsage;
    this.averageUsage = averageUsage;
    this.difference = difference;
    this.stimulus = stimulus;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AnomalyResultTuple that = (AnomalyResultTuple) o;
    return plugUsage == that.plugUsage
        && Double.compare(that.averageUsage, averageUsage) == 0
        && Double.compare(that.difference, difference) == 0 && plugId == that.plugId
        && householdId == that.householdId && houseId == that.houseId && stimulus == that.stimulus;
  }

  @Override
  public int hashCode() {
    return Objects.hash(plugUsage, averageUsage, difference, plugId, householdId, houseId,
        stimulus);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("currentValue", plugUsage)
        .append("averageValue", averageUsage)
        .append("difference", difference)
        .append("plugId", plugId)
        .append("householdId", householdId)
        .append("houseId", houseId)
        .toString();
  }

  public static class KryoSerializer extends
      com.esotericsoftware.kryo.Serializer<AnomalyResultTuple>
      implements Serializable {

    @Override
    public void write(Kryo kryo, Output output, AnomalyResultTuple object) {
      output.writeInt(object.plugId);
      output.writeInt(object.householdId);
      output.writeInt(object.houseId);
      output.writeLong(object.plugUsage);
      output.writeDouble(object.averageUsage);
      output.writeDouble(object.difference);
      output.writeLong(object.stimulus);
    }

    @Override
    public AnomalyResultTuple read(Kryo kryo, Input input, Class<AnomalyResultTuple> type) {
      return new AnomalyResultTuple(input.readInt(), input.readInt(), input.readInt(), input.readLong(),
          input.readDouble(), input.readDouble(), input.readLong());
    }
  }
}
