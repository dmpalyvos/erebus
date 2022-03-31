package io.palyvos.provenance.ananke.functions.noop;

import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import org.apache.flink.api.common.functions.FilterFunction;

public class NoopFilterFunction<T> implements FilterFunction<ProvenanceTupleContainer<T>> {

  private final FilterFunction<T> delegate;

  public NoopFilterFunction(FilterFunction<T> delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean filter(ProvenanceTupleContainer<T> value) throws Exception {
    return delegate.filter(value.tuple());
  }
}
