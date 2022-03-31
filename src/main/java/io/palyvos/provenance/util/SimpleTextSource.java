package io.palyvos.provenance.util;

import java.io.BufferedReader;
import java.io.FileReader;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SimpleTextSource<T> extends RichParallelSourceFunction<T> {

  protected static final Logger LOG = LoggerFactory.getLogger(SimpleTextSource.class);
  private static final String DEFAULT_NAME = "SOURCE";
  protected final ExperimentSettings settings;
  private String inputFile;
  private transient CountStat throughputStatistic;
  private volatile boolean enabled;
  private final String name;
  private boolean ignoreHeader;

  public SimpleTextSource(ExperimentSettings settings, boolean ignoreHeader) {
    this(DEFAULT_NAME, settings, ignoreHeader);
  }

  public SimpleTextSource(String name, ExperimentSettings settings, boolean ignoreHeader) {
    this.settings = settings;
    this.name = name;
    this.ignoreHeader = ignoreHeader;
  }

  @Override
  public void open(Configuration parameters) throws Exception {
    super.open(parameters);
    RuntimeContext ctx = getRuntimeContext();
    this.inputFile = inputFile();
    LOG.info("Source reading from file {}", inputFile);
    this.throughputStatistic =
        new CountStat(
            settings.throughputFile(ctx.getIndexOfThisSubtask(), name), settings.autoFlush());
    enabled = true;
  }

  protected abstract String inputFile();

  @Override
  public void run(SourceContext<T> ctx) throws Exception {
    try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {
      String line = br.readLine();
      while (enabled && line != null) {
        if (ignoreHeader) {
          ignoreHeader = false;
          line = br.readLine();
          continue;
        }
        throughputStatistic.increase(1);
        T tuple = getTuple(line.trim());
        ctx.collectWithTimestamp(tuple, getTimestamp(tuple));
        line = br.readLine();
      }
    } finally {
      LOG.info("Source terminating...");
      throughputStatistic.close();
    }
  }

  public abstract long getTimestamp(T tuple);

  protected abstract T getTuple(String line);

  @Override
  public void cancel() {
    enabled = false;
  }
}
