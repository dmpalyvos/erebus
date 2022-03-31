package io.palyvos.provenance.util;

import org.apache.flink.api.common.functions.RichFilterFunction;
import org.apache.flink.configuration.Configuration;

public class ThroughputLoggingFilter<T> extends RichFilterFunction<T> {

  private final String name;
  private final ExperimentSettings settings;
  private transient CountStat throughputStatistic;

  public ThroughputLoggingFilter(String name, ExperimentSettings settings) {
    this.name = name;
    this.settings = settings;
  }

  @Override
  public void open(Configuration parameters) throws Exception {
    super.open(parameters);
    this.throughputStatistic =
        new CountStat(
            settings.throughputFile(getRuntimeContext().getIndexOfThisSubtask(), name),
            settings.autoFlush());
  }

  @Override
  public boolean filter(T value) throws Exception {
    throughputStatistic.increase(1);
    return true;
  }
}
