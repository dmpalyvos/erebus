package io.palyvos.provenance.missing.predicate;

import java.io.Serializable;

@FunctionalInterface
public interface TimestampFunction extends Serializable {

  boolean evaluate(long timestamp);

}
