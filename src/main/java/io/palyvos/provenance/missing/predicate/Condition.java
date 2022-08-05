package io.palyvos.provenance.missing.predicate;

import java.io.Serializable;
import java.util.Collection;
import java.util.OptionalLong;

public interface Condition extends Serializable {

  Collection<Variable> variables();

  boolean evaluate(long timestamp);

  boolean isLoaded();

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
