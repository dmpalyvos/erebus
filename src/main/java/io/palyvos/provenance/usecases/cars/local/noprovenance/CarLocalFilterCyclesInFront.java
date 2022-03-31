package io.palyvos.provenance.usecases.cars.local.noprovenance;

import static io.palyvos.provenance.usecases.cars.local.CarLocalConstants.CYCLIST_MIN_NUMBER;

import io.palyvos.provenance.usecases.cars.local.CarLocalResult;
import org.apache.flink.api.common.functions.FilterFunction;

public class CarLocalFilterCyclesInFront implements
    FilterFunction<CarLocalResult> {

  @Override
  public boolean filter(CarLocalResult t) throws Exception {
    return t.count > CYCLIST_MIN_NUMBER;
  }
}
