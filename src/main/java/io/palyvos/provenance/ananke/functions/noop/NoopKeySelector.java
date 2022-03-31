package io.palyvos.provenance.ananke.functions.noop;

import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import org.apache.flink.api.java.functions.KeySelector;

public class NoopKeySelector<IN, KEY> implements KeySelector<ProvenanceTupleContainer<IN>, KEY> {

  private final KeySelector<IN, KEY> delegate;

  public NoopKeySelector(KeySelector<IN, KEY> delegate) {
    this.delegate = delegate;
  }

  @Override
  public KEY getKey(ProvenanceTupleContainer<IN> value) throws Exception {
    return delegate.getKey(value.tuple());
  }
}
