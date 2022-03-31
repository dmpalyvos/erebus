package io.palyvos.provenance.missing.predicate;

import io.palyvos.provenance.missing.PickedProvenance;
import java.io.Serializable;
import java.util.UUID;

public class JoinPredicates implements Serializable {

  private final Predicate predicateOne;
  private final Predicate predicateTwo;

  public JoinPredicates(Predicate predicate, String operatorName,
      QueryGraphInfo queryInfo) {
    this.predicateOne = predicate.transformed(one(operatorName),
        PickedProvenance.QUERY_SINK_NAME, queryInfo);
    this.predicateTwo = predicate.transformed(two(operatorName),
        PickedProvenance.QUERY_SINK_NAME, queryInfo);
  }

  public static String one(String name) {
    return name + "-ONE";
  }

  public static String two(String name) {
    return name + "-TWO";
  }

  public Predicate one() {
    return predicateOne;
  }

  public Predicate two() {
    return predicateTwo;
  }

  public Predicate get(boolean isOne) {
    return isOne ? predicateOne : predicateTwo;
  }

  public boolean hasExpired(long watermark) {
    return one().hasExpired(watermark) && two().hasExpired(watermark);
  }

  public void disable() {
    one().disable();
    two().disable();
  }

  public boolean isEnabled() {
    return one().isEnabled() || two().isEnabled();
  }

  public UUID uid() {
    return one().uid(); // UIDs of transformed predicates are identical
  }
}
