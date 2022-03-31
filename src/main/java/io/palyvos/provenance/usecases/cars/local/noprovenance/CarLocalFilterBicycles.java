package io.palyvos.provenance.usecases.cars.local.noprovenance;

import static io.palyvos.provenance.usecases.cars.local.CarLocalConstants.CYCLIST_Y_AREA;
import static io.palyvos.provenance.usecases.cars.local.CarLocalConstants.CYCLIST_Z_AREA;

import io.palyvos.provenance.usecases.cars.local.AnnotationTuple;
import org.apache.flink.api.common.functions.FilterFunction;

public class CarLocalFilterBicycles implements
    FilterFunction<AnnotationTuple> {

  @Override
  public boolean filter(AnnotationTuple t) throws Exception {
    return (t.label.equals("BICYCLE")) &&
        (-CYCLIST_Y_AREA < t.x) &&
        (t.x < CYCLIST_Y_AREA) &&
        (-CYCLIST_Z_AREA < t.y) &&
        (t.y < CYCLIST_Z_AREA);
  }
}
