package io.palyvos.provenance.genealog;

import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import io.palyvos.provenance.util.ExperimentSettings;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import org.apache.flink.api.common.eventtime.Watermark;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

public class ProvenanceTupleContainerFileSink<T extends ProvenanceTupleContainer> extends
    RichSinkFunction<T> {

  private final Map<String, PrintWriter> writers = new HashMap<>();
  private final ExperimentSettings settings;
  private final String name;

  public ProvenanceTupleContainerFileSink(String name, ExperimentSettings settings) {
    this.name = name;
    this.settings = settings;
  }

  private PrintWriter writerFor(String tupleOperator) {
    PrintWriter writer = writers.get(tupleOperator);
    if (writer == null) {
      writer = newWriter(tupleOperator);
      writers.put(tupleOperator, writer);
    }
    return writer;
  }

  private PrintWriter newWriter(String tupleOperator) {
    try {
      return new PrintWriter(new FileWriter(settings.outputFile(
          getRuntimeContext().getIndexOfThisSubtask(), name + "-" + tupleOperator)),
          settings.autoFlush());
    } catch (
        IOException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Override
  public void invoke(T tuple, Context context) {
    PrintWriter writer = writerFor(tuple.operator());
    if (tuple.isWatermark()) {
      writer.println("* WM: " + tuple.watermark());
    } else {
      writer.format("> [t=%d] %s\n", tuple.getTimestamp(), tuple);
    }
  }

  @Override
  public void writeWatermark(Watermark watermark) throws Exception {
    super.writeWatermark(watermark);
    for (PrintWriter pw : writers.values()) {
      pw.println("## WM: " + watermark.getTimestamp());
    }
  }

  @Override
  public void close() throws Exception {
    super.close();
    for (PrintWriter pw : writers.values()) {
      pw.print("--- OUTPUT END ---");
      pw.flush();
      pw.close();
    }
  }
}
