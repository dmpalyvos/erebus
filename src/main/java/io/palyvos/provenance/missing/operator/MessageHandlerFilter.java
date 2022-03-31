package io.palyvos.provenance.missing.operator;

import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import io.palyvos.provenance.missing.PickedProvenance;
import io.palyvos.provenance.util.ExperimentSettings;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import org.apache.flink.api.common.functions.RichFilterFunction;
import org.apache.flink.configuration.Configuration;

public class MessageHandlerFilter extends RichFilterFunction<ProvenanceTupleContainer> {

  private final ExperimentSettings settings;
  private transient PrintWriter messageWriter;

  public MessageHandlerFilter(ExperimentSettings settings) {
    this.settings = settings;
  }

  @Override
  public void open(Configuration parameters) throws Exception {
    super.open(parameters);
    try {
      final String messageFile = settings.outputFile(getRuntimeContext().getIndexOfThisSubtask(),
          PickedProvenance.META_ANSWERS_NAME, "txt");
      messageWriter = new PrintWriter(new FileWriter(messageFile), settings.autoFlush());
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Override
  public boolean filter(ProvenanceTupleContainer tuple) throws Exception {
    if (tuple.isMessage()) {
      messageWriter.write(tuple.getMessage());
      messageWriter.println();
      messageWriter.flush();
      return false;
    }
    return true;
  }

  @Override
  public void close() throws Exception {
    super.close();
    messageWriter.flush();
    messageWriter.close();
  }
}
