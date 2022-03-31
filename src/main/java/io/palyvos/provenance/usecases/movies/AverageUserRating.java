package io.palyvos.provenance.usecases.movies;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.io.Serializable;
import java.util.Objects;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class AverageUserRating implements Serializable {

  public final int nRatings;
  public final double averageRating;
  public final int userId;
  public final long stimulus;


  public AverageUserRating(int nRatings, double averageRating, int userId, long stimulus) {
    this.nRatings = nRatings;
    this.averageRating = averageRating;
    this.userId = userId;
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
    AverageUserRating that = (AverageUserRating) o;
    return nRatings == that.nRatings && Double.compare(that.averageRating, averageRating) == 0
        && userId == that.userId && stimulus == that.stimulus;
  }

  @Override
  public int hashCode() {
    return Objects.hash(nRatings, averageRating, userId, stimulus);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("nRatings", nRatings)
        .append("rating", averageRating)
        .append("userId", userId)
        .toString();
  }

  public static class KryoSerializer extends Serializer<AverageUserRating>
      implements Serializable {

    @Override
    public void write(Kryo kryo, Output output, AverageUserRating object) {
      output.writeInt(object.nRatings);
      output.writeDouble(object.averageRating);
      output.writeInt(object.userId);
      output.writeLong(object.stimulus);
    }

    @Override
    public AverageUserRating read(Kryo kryo, Input input, Class<AverageUserRating> type) {
      return new AverageUserRating(input.readInt(), input.readDouble(), input.readInt(),
          input.readLong());
    }
  }
}
