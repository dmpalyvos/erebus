package io.palyvos.provenance.missing.predicate;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

class Operator implements Serializable {

  private final String id;
  private final Map<String, String> renamings;
  private final Map<String, String> transforms;
  private final Collection<Operator> downstream = new HashSet<>();
  private final Collection<Operator> upstream = new HashSet<>();
  private final long windowSize;
  private final long windowAdvance;


  Operator(OperatorInfo operatorInfo) {
    Validate.notNull(operatorInfo, "operatorInfo");
    Validate.notNull(operatorInfo.renamings, "renamings");
    Validate.notNull(operatorInfo.transforms, "transforms");
    Validate.isTrue(operatorInfo.windowSize >= 0, "windowSize < 0");
    Validate.notBlank(operatorInfo.id, "Blank operator id");
    this.id = operatorInfo.id;
    this.renamings = operatorInfo.renamings;
    this.transforms = operatorInfo.transforms;
    this.windowSize = operatorInfo.windowSize;
    this.windowAdvance = operatorInfo.windowAdvance();
    Validate.isTrue(windowSize == 0 || windowAdvance >= 1, "windowSize == 0 && windowAdvance == %d", windowAdvance);
  }

  public Map<String, String> renamings() {
    return renamings;
  }

  public String id() {
    return id;
  }

  public boolean isStateful() {
    return windowSize > 0;
  }

  public long windowSize() {
    return windowSize;
  }

  public long windowAdvance() {
    return windowAdvance;
  }

  public boolean isSink() {
    return downstream.isEmpty();
  }

  public boolean isSource() {
    return upstream.isEmpty();
  }

  public Collection<Operator> downstream() {
    return downstream;
  }

  public Collection<Operator> upstream() {
    return upstream;
  }

  public String renamed(String variable) {
    final String variableMapping = renamings.get(variable);
    if (variableMapping == null) {
      return variable;
    }
    return variableMapping;
  }

  public String transform(String variable) {
    return transforms.get(variable);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Operator operator = (Operator) o;
    return windowSize == operator.windowSize && windowAdvance == operator.windowAdvance
        && Objects.equals(id, operator.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, windowSize, windowAdvance);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("id", id)
        .append("renamings", renamings)
        .append("transforms", transforms)
        .append("windowSize", windowSize)
        .append("windowAdvance", windowAdvance)
        .toString();
  }
}
