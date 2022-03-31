package io.palyvos.provenance.usecases.movies;

import org.apache.flink.api.common.functions.JoinFunction;

public class MoviesJoinFunction implements
    JoinFunction<MovieRating, AverageUserRating, MovieResult> {

  @Override
  public MovieResult join(MovieRating rating, AverageUserRating averageUserRating)
      throws Exception {
    return new MovieResult(rating.movieId, rating.userId, rating.year, rating.rating,
        averageUserRating.averageRating, averageUserRating.nRatings, Math.max(rating.stimulus, averageUserRating.stimulus));
  }
}
