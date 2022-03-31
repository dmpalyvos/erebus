package io.palyvos.provenance.usecases.cars.local.noprovenance;

import io.palyvos.provenance.usecases.cars.local.AnnotationTuple;
import io.palyvos.provenance.usecases.cars.local.CarLocalInputTuple;
import io.palyvos.provenance.usecases.cars.local.LidarImageContainer.Annotation3D;
import java.util.Map;
import java.util.UUID;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.util.Collector;

public class CarLocalPedestriansToSingleObject implements
    FlatMapFunction<CarLocalInputTuple, AnnotationTuple> {

  @Override
  public void flatMap(CarLocalInputTuple t, Collector<AnnotationTuple> collector)
      throws Exception {
    // annotations as such: object_ID : object_name, x, y, z
    Map<UUID, Annotation3D> left_objects = t.f2.getAnnotations();
    String left_payload_type = t.f2.getPayloadType();
    for (Map.Entry<UUID, Annotation3D> entry : left_objects.entrySet()) {
      collector.collect(
          AnnotationTuple.forPedestrianDetection(left_payload_type, entry.getKey(),
              entry.getValue(), t.getStimulus()));
    }
    Map<UUID, Annotation3D> right_objects = t.f3.getAnnotations();
    String right_payload_type = t.f3.getPayloadType();
    for (Map.Entry<UUID, Annotation3D> entry : right_objects.entrySet()) {
      collector.collect(
          AnnotationTuple.forPedestrianDetection(right_payload_type, entry.getKey(),
              entry.getValue(), t.getStimulus()));
    }
  }
}
