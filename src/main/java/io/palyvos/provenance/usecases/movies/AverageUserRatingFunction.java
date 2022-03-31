package io.palyvos.provenance.usecases.movies;

import io.palyvos.provenance.usecases.movies.AverageUserRatingFunction.AverageUserRatingAccumulator;
import java.io.Serializable;
import org.apache.flink.api.common.functions.AggregateFunction;

public class AverageUserRatingFunction implements
    AggregateFunction<MovieRating, AverageUserRatingAccumulator, AverageUserRating> {

  @Override
  public AverageUserRatingAccumulator createAccumulator() {
    return new AverageUserRatingAccumulator();
  }

  @Override
  public AverageUserRatingAccumulator add(MovieRating tuple, AverageUserRatingAccumulator acc) {
    acc.ratingSum += tuple.rating;
    acc.count += 1;
    acc.userId = tuple.userId;
    acc.stimulus = Math.max(acc.stimulus, tuple.stimulus);
    return acc;
  }

  @Override
  public AverageUserRating getResult(AverageUserRatingAccumulator acc) {
    return new AverageUserRating(acc.count, acc.average(), acc.userId, acc.stimulus);
  }

  @Override
  public AverageUserRatingAccumulator merge(AverageUserRatingAccumulator a,
      AverageUserRatingAccumulator b) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public static class AverageUserRatingAccumulator implements Serializable {

    private double ratingSum;
    private int count;
    private int userId;
    private long stimulus;

    double average() {
      return count > 0 ? ratingSum / count : 0;
    }

  }
}
