package io.palyvos.provenance.ananke.functions.noop;

import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

public class NoopSinkFunction<IN> implements
    SinkFunction<ProvenanceTupleContainer<IN>> {

  private final SinkFunction<IN> delegate;

  public NoopSinkFunction(SinkFunction<IN> delegate) {
    this.delegate = delegate;
  }

  @Override
  public void invoke(ProvenanceTupleContainer<IN> value, Context context) throws Exception {
    delegate.invoke(value.tuple(), context);
  }
}
