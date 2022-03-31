package io.palyvos.provenance.ananke.functions.noop;

import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import org.apache.flink.api.common.functions.JoinFunction;

public class NoopJoinFunction<IN1, IN2, OUT>
    implements JoinFunction<
    ProvenanceTupleContainer<IN1>, ProvenanceTupleContainer<IN2>, ProvenanceTupleContainer<OUT>> {

  private final JoinFunction<IN1, IN2, OUT> delegate;

  public NoopJoinFunction(JoinFunction<IN1, IN2, OUT> delegate) {
    this.delegate = delegate;
  }

  @Override
  public ProvenanceTupleContainer<OUT> join(
      ProvenanceTupleContainer<IN1> first, ProvenanceTupleContainer<IN2> second) throws Exception {
    OUT result = delegate.join(first.tuple(), second.tuple());
    ProvenanceTupleContainer<OUT> genealogResult = new ProvenanceTupleContainer<>(result);
    genealogResult.initGenealog(NoopProvenanceFunctionFactory.NO_PROVENANCE_TUPLE_TYPE);
    genealogResult.copyTimes(first, second);
    return genealogResult;
  }
}
