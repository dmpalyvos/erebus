package io.palyvos.provenance.usecases.cars.local;

import io.palyvos.provenance.usecases.cars.local.LidarImageContainer.Annotation3D;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class AnnotationTuple implements Serializable {

  public static final String TARGET_BICYCLE = "BICYCLE";
  public static final String TARGET_PEDESTRIAN = "PEDESTRIAN";
  public String target;
  public String type = "";
  public UUID key;
  public String label;
  public double x;
  public double y;
  public double z;
  public long stimulus;

  public static AnnotationTuple forPedestrianDetection(String payloadType, UUID key,
      Annotation3D annotation, long stimulus) {
    return new AnnotationTuple(TARGET_PEDESTRIAN, payloadType, key, annotation.labelClass,
        annotation.x, annotation.y, annotation.z, stimulus);
  }

  public static AnnotationTuple forCyclistDetection(UUID key, Annotation3D annotation, long stimulus) {
    return new AnnotationTuple(TARGET_BICYCLE, "", key, annotation.labelClass, annotation.x,
        annotation.y, annotation.z, stimulus);
  }

  public AnnotationTuple(String target, String type, UUID key, String label, double x,
      double y, double z, long stimulus) {
    this.target = target;
    this.type = type;
    this.key = key;
    this.label = label;
    this.x = x;
    this.y = y;
    this.z = z;
    this.stimulus = stimulus;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AnnotationTuple that = (AnnotationTuple) o;
    return Double.compare(that.x, x) == 0 && Double.compare(that.y, y) == 0
        && Double.compare(that.z, z) == 0 && Objects.equals(target, that.target)
        && Objects.equals(type, that.type) && Objects.equals(key,
        that.key) && Objects.equals(label, that.label);
  }

  @Override
  public int hashCode() {
    return Objects.hash(target, type, key, label, x, y, z);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("target", target)
        .append("type", type)
        .append("key", key)
        .append("label", label)
        .append("x", x)
        .append("y", y)
        .append("z", z)
        .toString();
  }
}
