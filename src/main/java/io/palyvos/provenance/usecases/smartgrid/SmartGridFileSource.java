package io.palyvos.provenance.usecases.smartgrid;

import io.palyvos.provenance.util.ExperimentSettings;
import io.palyvos.provenance.util.SimpleTextSource;
import java.io.File;

public class SmartGridFileSource extends SimpleTextSource<SmartGridTuple> {

  public SmartGridFileSource(ExperimentSettings settings) {
    super(settings, true);
  }

  public SmartGridFileSource(String name, ExperimentSettings settings) {
    super(name, settings, true);
  }

  @Override
  protected String inputFile() {
    return settings.inputFolder() + File.separator + settings.inputFile("csv");
  }

  @Override
  protected SmartGridTuple getTuple(String line) {
    return SmartGridTuple.fromReading(line);
  }

  @Override
  public long getTimestamp(SmartGridTuple tuple) {
    return tuple.timestamp;
  }
}
