package io.palyvos.provenance.usecases.cars.local.provenance2.queries;

import static io.palyvos.provenance.missing.predicate.Predicate.PredicateStrategy.AND;

import io.palyvos.provenance.missing.predicate.OneVariableCondition;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.predicate.Predicate.PredicateStrategy;
import io.palyvos.provenance.missing.predicate.TimestampCondition;
import io.palyvos.provenance.missing.predicate.TupleVariable;
import io.palyvos.provenance.missing.predicate.TwoVariableCondition;
import io.palyvos.provenance.usecases.cars.local.AnnotationTuple;
import io.palyvos.provenance.util.PredicateHolder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public enum CarLocalPredicates implements PredicateHolder {
  INSTANCE;

  private final Predicate CYCLIST_OR_PEDESTRIAN_PREDICATE = Predicate.of(PredicateStrategy.OR,
      Predicate.of(AND,
          TimestampCondition.between(TimeUnit.MINUTES.toMillis(0), TimeUnit.MINUTES.toMillis(160)),
          new OneVariableCondition(TupleVariable.fromField("label"),
              var -> var.asString().equals("BICYCLE")),
          new OneVariableCondition(TupleVariable.fromField("minZ"),
              var -> var.asDouble() > 1),
          new OneVariableCondition(TupleVariable.fromField("count"),
              var -> var.asInt() > 2 && var.asInt() < 50)
      ),
      Predicate.of(AND,
          TimestampCondition.between(TimeUnit.MINUTES.toMillis(15), TimeUnit.MINUTES.toMillis(140)),
          new OneVariableCondition(TupleVariable.fromField("label"),
              var -> var.asString().equals("PEDESTRIAN")),
          new TwoVariableCondition(TupleVariable.fromField("minX"), TupleVariable.fromField("minY"),
              ((x, y) -> x.asDouble() > 0.25 * y.asDouble()))
      )
  );

  private final Predicate INPUT_CLEAN_PREDICATE = Predicate.of(PredicateStrategy.OR,
      Predicate.of(AND,
          TimestampCondition.between(TimeUnit.MINUTES.toMillis(20), TimeUnit.MINUTES.toMillis(120)),
          new OneVariableCondition(TupleVariable.fromField("target"),
              var -> AnnotationTuple.TARGET_BICYCLE.equals(var.asString())),
          new OneVariableCondition(TupleVariable.fromField("label"),
              var -> var.asString().equals("VEHICLE"))
      ),
      Predicate.of(AND,
          TimestampCondition.between(TimeUnit.MINUTES.toMillis(0), TimeUnit.MINUTES.toMillis(120)),
          new OneVariableCondition(TupleVariable.fromField("label"),
              var -> var.asString().equals("PEDESTRIAN"))
      )
  );


  private final Map<String, Predicate> predicates = new HashMap<>();


  CarLocalPredicates() {
    predicates.putAll(basicPredicates());
    predicates.put("Q1", CYCLIST_OR_PEDESTRIAN_PREDICATE);
    predicates.put("Q2", INPUT_CLEAN_PREDICATE);
  }

  @Override
  public Class<?> query() {
    return CarLocalPredicates.class;
  }

  @Override
  public Map<String, Predicate> predicates() {
    return Collections.unmodifiableMap(predicates);
  }
}
