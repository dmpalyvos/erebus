package io.palyvos.provenance.usecases.cars.local.noprovenance;

import io.palyvos.provenance.usecases.cars.local.AnnotationTuple;
import io.palyvos.provenance.usecases.cars.local.CarLocalInputTuple;
import io.palyvos.provenance.usecases.cars.local.LidarImageContainer.Annotation3D;
import java.util.Map;
import java.util.UUID;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.util.Collector;

public class CarLocalCyclistsToSingleObject implements
    FlatMapFunction<CarLocalInputTuple, AnnotationTuple> {

  @Override
  public void flatMap(CarLocalInputTuple t, Collector<AnnotationTuple> collector)
      throws Exception {
    Map<UUID, Annotation3D> lidarObjects = t.f1.getAnnotations();
    for (Map.Entry<UUID, Annotation3D> entry : lidarObjects.entrySet()) {
      collector.collect(AnnotationTuple.forCyclistDetection(entry.getKey(), entry.getValue(),
          t.getStimulus()));
    }
  }
}
