package io.palyvos.provenance.missing.predicate;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.BiFunction;

public interface Condition extends Serializable {

  Collection<Variable> variables();

  boolean evaluate(long timestamp);

  boolean isLoaded();

  Condition renamed(Map<String, ? extends Collection<VariableRenaming>> renamings);

  default Condition timeShifted(
      BiFunction<Long, Long, OptionalLong> leftBoundaryTransform,
      BiFunction<Long, Long, OptionalLong> rightBoundaryTransform) {
    return this;
  }

  default boolean isSatisfiable() {
    return true;
  }

  default OptionalLong minTimeBoundary() {
    return OptionalLong.empty();
  }

  default OptionalLong maxTimeBoundary() {
    return OptionalLong.empty();
  }

  default String description() {
    return toString();
  }

}
