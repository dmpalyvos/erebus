package io.palyvos.provenance.missing.predicate;

import java.io.Serializable;

@FunctionalInterface
public interface TwoVariableFunction extends Serializable {

  boolean evaluate(Variable var1, Variable var2);

}
