package io.palyvos.provenance.usecases.cars.local;

import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class CarLocalResult {

  public String target;
  public String type = "";
  public UUID key;
  public String label;
  public double minX = Double.MAX_VALUE;
  public double minY = Double.MAX_VALUE;
  public double minZ = Double.MAX_VALUE;
  public int count;
  public long stimulus = -1;

  public CarLocalResult() {
  }

  public CarLocalResult(String target, String type, UUID key, String label,
      double minX, double minY, double minZ, int count, long stimulus) {
    this.target = target;
    this.type = type;
    this.key = key;
    this.label = label;
    this.minX = minX;
    this.minY = minY;
    this.minZ = minZ;
    this.count = count;
    this.stimulus = stimulus;
  }

  public CarLocalResult accumulateCyclist(AnnotationTuple t) {
    target = t.target;
    type = t.type;
    key = t.key;
    label = t.label;
    minX = Math.min(minX, t.x);
    minY = Math.min(minY, t.y);
    minZ = Math.min(minZ, t.z);
    count += 1;
    stimulus = Math.max(stimulus, t.stimulus);
    return this;
  }

  public static CarLocalResult join(AnnotationTuple first, AnnotationTuple second) {
    return new CarLocalResult(first.target, first.type, first.key, first.label, Math.min(first.x,
        second.x), Math.min(first.y, second.y), Math.min(first.z, second.z), -1, Math.max(first.stimulus,
        second.stimulus));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CarLocalResult that = (CarLocalResult) o;
    return Double.compare(that.minX, minX) == 0
        && Double.compare(that.minY, minY) == 0
        && Double.compare(that.minZ, minZ) == 0 && count == that.count
        && stimulus == that.stimulus && Objects.equals(target, that.target)
        && Objects.equals(type, that.type) && Objects.equals(key,
        that.key) && Objects.equals(label, that.label);
  }

  @Override
  public int hashCode() {
    return Objects.hash(target, type, key, label, minX, minY, minZ, count, stimulus);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("target", target)
        .append("type", type)
        .append("key", key)
        .append("label", label)
        .append("minX", minX)
        .append("minY", minY)
        .append("minZ", minZ)
        .append("count", count)
        .toString();
  }
}
