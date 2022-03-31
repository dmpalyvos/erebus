package io.palyvos.provenance.usecases.movies.provenance2;

import static io.palyvos.provenance.missing.predicate.Predicate.PredicateStrategy.AND;

import io.palyvos.provenance.missing.predicate.OneVariableCondition;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.predicate.Predicate.PredicateStrategy;
import io.palyvos.provenance.missing.predicate.TimestampCondition;
import io.palyvos.provenance.missing.predicate.TupleVariable;
import io.palyvos.provenance.missing.predicate.TwoVariableCondition;
import io.palyvos.provenance.util.PredicateHolder;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public enum MoviesPredicates implements PredicateHolder {
  INSTANCE;

  private final long DATASET_START_TIMESTAMP = TimeUnit.SECONDS.toMillis(789652009);
  private final long DATASET_LENGTH_YEARS = 12;

  private long yearEpochMillisecond(int year) {
    return TimeUnit.SECONDS.toMillis(new GregorianCalendar(year, 1, 1)
        .toInstant().getEpochSecond());
  }

  private long yearMillis(int years) {
    return TimeUnit.DAYS.toMillis(365 * years);
  }

  private final Predicate PREDICATE_1 = Predicate.of(PredicateStrategy.OR,
      Predicate.of(AND,
          TimestampCondition.between(DATASET_START_TIMESTAMP + yearMillis(1),
              DATASET_START_TIMESTAMP + yearMillis(7)),
          new OneVariableCondition(TupleVariable.fromField("year"),
              var -> var.asInt() > 1900 && var.asInt() < 1990),
          new OneVariableCondition(TupleVariable.fromField("rating"), var -> var.asDouble() > 1.5)
      )
  );

  private final Predicate PREDICATE_2 = Predicate.of(PredicateStrategy.OR,
      Predicate.of(AND,
          TimestampCondition.between(DATASET_START_TIMESTAMP + yearMillis(0),
              DATASET_START_TIMESTAMP + yearMillis(17)),
          new OneVariableCondition(TupleVariable.fromField("nRatings"),
              var -> var.asInt() > 12),
          new TwoVariableCondition(TupleVariable.fromField("rating"),
              TupleVariable.fromField("nRatings"),
              (var1, var2) -> var1.asDouble() * var2.asInt() > 33),
          new OneVariableCondition(TupleVariable.fromField("movieId"),
              var -> var.asInt() < 5000)
      )
  );

  private final Map<String, Predicate> predicates = new HashMap<>();


  MoviesPredicates() {
    predicates.putAll(basicPredicates());
    predicates.put("Q1", PREDICATE_1);
    predicates.put("Q2", PREDICATE_2);
    // TODO: Cleanup
    predicates.put("slack", Predicate.of(AND,
        TimestampCondition.between(yearEpochMillisecond(1994), yearEpochMillisecond(1996)),
        Predicate.alwaysTrue()));
  }

  @Override
  public Map<String, Predicate> predicates() {
    return Collections.unmodifiableMap(predicates);
  }

  @Override
  public Class<?> query() {
    return Movies.class;
  }

}
