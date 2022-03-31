package io.palyvos.provenance.usecases.movies;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class MovieRating implements Serializable {

  private static final Pattern DELIMITER_PATTERN = Pattern.compile(",");
  public final int userId;
  public final int movieId;
  public final double rating;
  public final long timestamp;
  public final long budget;
  public final int year;
  public final long stimulus;

  public static MovieRating fromReading(String reading) {
    try {
      String[] tokens = DELIMITER_PATTERN.split(reading.trim(), -1);
      final int userId = Integer.valueOf(tokens[0]);
      final int movieId = Integer.valueOf(tokens[1]);
      final double rating = Double.valueOf(tokens[2]);
      final long timestamp = 1000 * Long.valueOf(tokens[3]); // File timestamp in seconds
      final long budget = tokens[4].isEmpty() ? -1 : Long.valueOf(tokens[4]);
      final int year = tokens[5].isEmpty() ? -1 : Integer.valueOf(tokens[5]);
      final String title = tokens[6];
      final String language = tokens[7];
      final String genre = tokens[8];
      return new MovieRating(userId, rating, movieId, timestamp, budget, year,
          System.currentTimeMillis());
    } catch (Exception exception) {
      throw new IllegalArgumentException(String.format("Failed to parse reading: %s", reading),
          exception);
    }
  }

  protected MovieRating(int userId, double rating, int movieId, long timestamp, long budget,
      int year, long stimulus) {
    this.userId = userId;
    this.rating = rating;
    this.movieId = movieId;
    this.timestamp = timestamp;
    this.budget = budget;
    this.year = year;
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
    MovieRating that = (MovieRating) o;
    return userId == that.userId && movieId == that.movieId
        && Double.compare(that.rating, rating) == 0 && timestamp == that.timestamp
        && stimulus == that.stimulus;
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, movieId, rating, timestamp, stimulus);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("userId", userId)
        .append("movieId", movieId)
        .append("rating", rating)
        .append("timestamp", timestamp)
        .append("budget", budget)
        .append("year", year)
        .toString();
  }

  public static class KryoSerializer extends Serializer<MovieRating> implements Serializable {

    @Override
    public void write(Kryo kryo, Output output, MovieRating object) {
      output.writeInt(object.userId);
      output.writeDouble(object.rating);
      output.writeInt(object.movieId);
      output.writeLong(object.timestamp);
      output.writeLong(object.budget);
      output.writeInt(object.year);
      output.writeLong(object.stimulus);
    }

    @Override
    public MovieRating read(Kryo kryo, Input input, Class<MovieRating> type) {
      return new MovieRating(input.readInt(), input.readDouble(), input.readInt(), input.readLong(),
          input.readLong(), input.readInt(), input.readLong());
    }
  }
}
