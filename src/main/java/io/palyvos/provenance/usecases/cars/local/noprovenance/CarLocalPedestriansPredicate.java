package io.palyvos.provenance.usecases.cars.local.noprovenance;

import io.palyvos.provenance.usecases.cars.local.AnnotationTuple;
import io.palyvos.provenance.usecases.cars.local.CarLocalResult;
import org.apache.flink.api.common.functions.JoinFunction;

public class CarLocalPedestriansPredicate implements
    JoinFunction<AnnotationTuple, AnnotationTuple, CarLocalResult> {

  @Override
  public CarLocalResult join(AnnotationTuple first, AnnotationTuple second) throws Exception {
    return CarLocalResult.join(first, second);
  }
}
