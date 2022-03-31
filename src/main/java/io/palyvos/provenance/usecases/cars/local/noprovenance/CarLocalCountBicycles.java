package io.palyvos.provenance.usecases.cars.local.noprovenance;

import io.palyvos.provenance.usecases.cars.local.AnnotationTuple;
import io.palyvos.provenance.usecases.cars.local.CarLocalResult;
import org.apache.flink.api.common.functions.AggregateFunction;

public class CarLocalCountBicycles implements
    AggregateFunction<AnnotationTuple, CarLocalResult, CarLocalResult> {

  @Override
  public CarLocalResult createAccumulator() {
    return new CarLocalResult();
  }

  @Override
  public CarLocalResult add(AnnotationTuple t, CarLocalResult acc) {
    return acc.accumulateCyclist(t);
  }

  @Override
  public CarLocalResult getResult(CarLocalResult acc) {
    return acc;
  }

  @Override
  public CarLocalResult merge(CarLocalResult acc1, CarLocalResult acc2) {
    throw new UnsupportedOperationException("merge");
  }
}
