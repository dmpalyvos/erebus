package io.palyvos.provenance.usecases.movies;

import io.palyvos.provenance.util.ExperimentSettings;
import io.palyvos.provenance.util.SimpleTextSource;
import java.io.File;

public class MovieRatingSource extends SimpleTextSource<MovieRating> {

  public MovieRatingSource(ExperimentSettings settings) {
    super(settings, true);
  }

  public MovieRatingSource(String name, ExperimentSettings settings) {
    super(name, settings, true);
  }

  @Override
  protected String inputFile() {
    return settings.inputFolder() + File.separator + settings.inputFile("csv");
  }

  @Override
  protected MovieRating getTuple(String line) {
    return MovieRating.fromReading(line);
  }

  @Override
  public long getTimestamp(MovieRating tuple) {
    return tuple.timestamp;
  }
}
