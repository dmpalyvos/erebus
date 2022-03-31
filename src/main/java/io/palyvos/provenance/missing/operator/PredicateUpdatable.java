package io.palyvos.provenance.missing.operator;

import io.palyvos.provenance.missing.util.ComponentCategory;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.util.KafkaPredicateUtil;

public interface PredicateUpdatable {

  void updatePredicate(Predicate newPredicate);

  ComponentCategory category();

  /**
   * This method is intended to be called by the thread of the {@link PredicateUpdatable} and not
   * from the thread of {@link KafkaPredicateUtil}. Its main purpose is to disable expired
   * predicates. Since {@link KafkaPredicateUtil} can update
   * predicates asynchronously, this method should keep a local reference to the predicate and check
   * and disable that, to prevent disabling a newly-set predicate.
   */
  void onWatermarkUpdate(long watermark);

}
