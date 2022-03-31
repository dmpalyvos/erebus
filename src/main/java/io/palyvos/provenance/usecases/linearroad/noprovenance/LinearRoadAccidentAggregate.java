package io.palyvos.provenance.usecases.linearroad.noprovenance;

import org.apache.flink.api.common.functions.AggregateFunction;

public class LinearRoadAccidentAggregate implements AggregateFunction<VehicleTuple,
    AccidentTuple, AccidentTuple> {

  @Override
  public AccidentTuple createAccumulator() {
    return new AccidentTuple();
  }

  @Override
  public AccidentTuple add(VehicleTuple value, AccidentTuple accumulator) {
    accumulator.accumulate(value);
    return accumulator;
  }

  @Override
  public AccidentTuple getResult(AccidentTuple accumulator) {
    return accumulator;
  }

  @Override
  public AccidentTuple merge(AccidentTuple a, AccidentTuple b) {
    throw new UnsupportedOperationException("not implemented");
  }


}
