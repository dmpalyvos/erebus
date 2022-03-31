package io.palyvos.provenance.missing.operator;

import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import org.apache.flink.api.common.functions.JoinFunction;

public class PickedJoinFunctionWrapper<IN1, IN2, OUT> implements JoinFunction<
    ProvenanceTupleContainer<IN1>, ProvenanceTupleContainer<IN2>, ProvenanceTupleContainer<OUT>> {

  private final JoinFunction<ProvenanceTupleContainer<IN1>, ProvenanceTupleContainer<IN2>, ProvenanceTupleContainer<OUT>> delegate;

  public PickedJoinFunctionWrapper(
      JoinFunction<ProvenanceTupleContainer<IN1>, ProvenanceTupleContainer<IN2>, ProvenanceTupleContainer<OUT>> delegate) {
    this.delegate = delegate;
  }

  @Override
  public ProvenanceTupleContainer<OUT> join(ProvenanceTupleContainer<IN1> first,
      ProvenanceTupleContainer<IN2> second) throws Exception {
    first.setMatched();
    second.setMatched();
    first.setJoined();
    second.setJoined();
    return delegate.join(first, second);
  }
}
