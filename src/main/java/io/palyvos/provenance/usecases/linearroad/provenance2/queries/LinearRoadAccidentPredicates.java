package io.palyvos.provenance.usecases.linearroad.provenance2.queries;

import static io.palyvos.provenance.missing.predicate.Predicate.PredicateStrategy.AND;

import io.palyvos.provenance.missing.predicate.OneVariableCondition;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.predicate.Predicate.PredicateStrategy;
import io.palyvos.provenance.missing.predicate.TimestampCondition;
import io.palyvos.provenance.missing.predicate.TupleVariable;
import io.palyvos.provenance.util.PredicateHolder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public enum LinearRoadAccidentPredicates implements PredicateHolder {
  INSTANCE;

  private final long DATASET_START_TIMESTAMP = 0;
  private final long DATASET_END_TIMESTAMP = 10799000; // 179 min

  private final Predicate PREDICATE_1 = Predicate.of(PredicateStrategy.OR,
      Predicate.of(AND,
          TimestampCondition.between(DATASET_START_TIMESTAMP + TimeUnit.MINUTES.toMillis(10),
              DATASET_START_TIMESTAMP + TimeUnit.MINUTES.toMillis(240)),
          new OneVariableCondition(TupleVariable.fromField("lane"),
              var -> var.asInt() == 0 || var.asInt() == 4),
          new OneVariableCondition(TupleVariable.fromField("seg"),
              var -> var.asInt() > 30),
          new OneVariableCondition(TupleVariable.fromField("count"),
              var -> var.asInt() > 1
          )
      )
  );

  private final Predicate PREDICATE_2 = Predicate.of(PredicateStrategy.OR,
      Predicate.of(AND,
          TimestampCondition.between(DATASET_START_TIMESTAMP + TimeUnit.MINUTES.toMillis(10),
              DATASET_START_TIMESTAMP + TimeUnit.MINUTES.toMillis(100)),
          new OneVariableCondition(TupleVariable.fromField("vids"),
              var -> ((Set<Long>) var.asObject()).stream().allMatch(vid -> vid % 2 == 0))
      )
  );

  private final Map<String, Predicate> predicates = new HashMap<>();


  LinearRoadAccidentPredicates() {
    predicates.putAll(basicPredicates());
    predicates.put("Q1", PREDICATE_1);
    predicates.put("Q2", PREDICATE_2);
  }

  @Override
  public Map<String, Predicate> predicates() {
    return Collections.unmodifiableMap(predicates);
  }

  @Override
  public Class<?> query() {
    return LinearRoadAccidentPredicates.class;
  }

}
