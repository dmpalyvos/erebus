package io.palyvos.provenance.usecases.cars.local.noprovenance;

import io.palyvos.provenance.usecases.cars.local.AnnotationTuple;
import org.apache.flink.api.common.functions.FilterFunction;

public class CarLocalFilterPedestrians implements
    FilterFunction<AnnotationTuple> {

  @Override
  public boolean filter(AnnotationTuple t) throws Exception {
    return t.label.equals("PEDESTRIAN");
  }
}
