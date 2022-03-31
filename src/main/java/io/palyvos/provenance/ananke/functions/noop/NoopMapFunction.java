package io.palyvos.provenance.ananke.functions.noop;

import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import org.apache.flink.api.common.functions.MapFunction;

public class NoopMapFunction<T, O>
    implements MapFunction<ProvenanceTupleContainer<T>, ProvenanceTupleContainer<O>> {

  private final MapFunction<T, O> delegate;

  public NoopMapFunction(MapFunction<T, O> delegate) {
    this.delegate = delegate;
  }

  @Override
  public ProvenanceTupleContainer<O> map(ProvenanceTupleContainer<T> value) throws Exception {
    O result = delegate.map(value.tuple());
    ProvenanceTupleContainer<O> genealogResult = new ProvenanceTupleContainer<>(result);
    genealogResult.initGenealog(NoopProvenanceFunctionFactory.NO_PROVENANCE_TUPLE_TYPE);
    genealogResult.copyTimes(value);
    return genealogResult;
  }
}
