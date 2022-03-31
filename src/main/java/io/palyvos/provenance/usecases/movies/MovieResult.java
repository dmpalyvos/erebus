package io.palyvos.provenance.usecases.movies;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.io.Serializable;
import java.util.Objects;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class MovieResult implements Serializable {

  public final int userId;
  public final int movieId;
  public final int year;
  public final double rating;
  public final double averageRating;
  public final int nRatings;
  public final long stimulus;

  public MovieResult(int movieId, int userId, int year, double rating, double averageRating,
      int nRatings,
      long stimulus) {
    this.movieId = movieId;
    this.userId = userId;
    this.year = year;
    this.rating = rating;
    this.averageRating = averageRating;
    this.nRatings = nRatings;
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
    MovieResult that = (MovieResult) o;
    return userId == that.userId && movieId == that.movieId
        && Double.compare(that.rating, rating) == 0
        && Double.compare(that.averageRating, averageRating) == 0
        && stimulus == that.stimulus;
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, movieId, rating, averageRating, stimulus);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("userId", userId)
        .append("movieId", movieId)
        .append("year", year)
        .append("rating", rating)
        .append("averageRating", averageRating)
        .append("nRatings", nRatings)
        .toString();
  }

  public static class KryoSerializer extends Serializer<MovieResult>
      implements Serializable {

    @Override
    public void write(Kryo kryo, Output output, MovieResult object) {
      output.writeInt(object.movieId);
      output.writeInt(object.userId);
      output.writeInt(object.year);
      output.writeDouble(object.rating);
      output.writeDouble(object.averageRating);
      output.writeInt(object.nRatings);
      output.writeLong(object.stimulus);

    }

    @Override
    public MovieResult read(Kryo kryo, Input input, Class<MovieResult> type) {
      return new MovieResult(input.readInt(), input.readInt(), input.readInt(),
          input.readDouble(), input.readDouble(), input.readInt(), input.readLong());
    }
  }
}
