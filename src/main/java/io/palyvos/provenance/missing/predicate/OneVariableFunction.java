package io.palyvos.provenance.missing.predicate;

import java.io.Serializable;

@FunctionalInterface
public interface OneVariableFunction extends Serializable {

  boolean evaluate(Variable var);

}
