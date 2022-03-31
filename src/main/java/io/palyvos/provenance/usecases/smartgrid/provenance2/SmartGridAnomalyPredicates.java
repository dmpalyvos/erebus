package io.palyvos.provenance.usecases.smartgrid.provenance2;

import io.palyvos.provenance.missing.predicate.OneVariableCondition;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.predicate.Predicate.PredicateStrategy;
import io.palyvos.provenance.missing.predicate.TimestampCondition;
import io.palyvos.provenance.missing.predicate.TupleVariable;
import io.palyvos.provenance.util.PredicateHolder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public enum SmartGridAnomalyPredicates implements PredicateHolder {
  INSTANCE;

  public final long DATASET_START_TIMESTAMP = TimeUnit.SECONDS.toMillis(1377986401);

  private final Predicate PREDICATE_1 = Predicate.of(PredicateStrategy.OR,
      Predicate.of(PredicateStrategy.AND,
          TimestampCondition.between(
              DATASET_START_TIMESTAMP - 1,
              DATASET_START_TIMESTAMP + TimeUnit.MINUTES.toMillis(140)),
          new OneVariableCondition(TupleVariable.fromField("difference"),
              var -> var.asDouble() > 4),
          new OneVariableCondition(TupleVariable.fromField("plugUsage"),
              var -> var.asLong() < 30)));

  private final Predicate PREDICATE_2 = Predicate.of(PredicateStrategy.OR,
      Predicate.of(PredicateStrategy.AND,
          TimestampCondition.between(DATASET_START_TIMESTAMP - 1 + TimeUnit.MINUTES.toMillis(10),
              DATASET_START_TIMESTAMP + TimeUnit.MINUTES.toMillis(280)),
          new OneVariableCondition(TupleVariable.fromField("difference"),
              var -> var.asDouble() > 1),
          new OneVariableCondition(TupleVariable.fromField("householdId"),
              var -> var.asInt() % 2 == 0)),
      Predicate.of(PredicateStrategy.AND,
          TimestampCondition.between(DATASET_START_TIMESTAMP - 1 + TimeUnit.MINUTES.toMillis(5),
              DATASET_START_TIMESTAMP + TimeUnit.MINUTES.toMillis(100)),
          new OneVariableCondition(TupleVariable.fromField("difference"),
              var -> var.asDouble() > 1),
          new OneVariableCondition(TupleVariable.fromField("plugId"),
              var -> var.asInt() > 0 && var.asInt() < 8))
  );

  private final Map<String, Predicate> predicates = new HashMap<>();


  SmartGridAnomalyPredicates() {
    predicates.putAll(basicPredicates());
    predicates.put("Q1", PREDICATE_1);
    predicates.put("Q2", PREDICATE_2);

  }

  @Override
  public Class<?> query() {
    return SmartGridAnomaly.class;
  }

  @Override
  public Map<String, Predicate> predicates() {
    return Collections.unmodifiableMap(predicates);
  }

}
