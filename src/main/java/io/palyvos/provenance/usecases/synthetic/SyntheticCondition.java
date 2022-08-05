package io.palyvos.provenance.usecases.synthetic;

import io.palyvos.provenance.missing.predicate.Condition;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.predicate.Predicate.PredicateStrategy;
import io.palyvos.provenance.missing.predicate.ReflectionVariable;
import io.palyvos.provenance.missing.predicate.Variable;
import io.palyvos.provenance.missing.predicate.VariableCondition;
import io.palyvos.provenance.missing.predicate.VariableRenaming;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.commons.lang3.Validate;

public class SyntheticCondition implements VariableCondition {

  private final double selectivity;
  private final Random random = new Random(0);
  private final long evaluationTimeMs;
  private final List<Variable> variables = Arrays.asList(
      ReflectionVariable.fromMethod("getTimestamp"),
      ReflectionVariable.fromMethod("getStimulus"),
      ReflectionVariable.fromMethod("getGenealogData"));

  public static Predicate newPredicate(double selectivity, long evaluationTimeMs) {
    return Predicate.of(PredicateStrategy.AND,
        new SyntheticCondition(selectivity, evaluationTimeMs));
  }

  private SyntheticCondition(double selectivity, long evaluationTimeMs) {
    this.evaluationTimeMs = evaluationTimeMs;
    Validate.isTrue(selectivity >= 0 && selectivity <= 1, "Selectivity must be in [0, 1]");
    this.selectivity = selectivity;
  }

  @Override
  public Collection<Variable> variables() {
    return variables;
  }

  @Override
  public boolean evaluate(long timestamp) {
    pretendToEvaluate();
    return random.nextDouble() < selectivity;
  }

  private void pretendToEvaluate() {
    if (evaluationTimeMs > 0) {
      try {
        Thread.sleep(evaluationTimeMs);
      } catch (InterruptedException e) {
      }
    }
  }

  @Override
  public boolean isLoaded() {
    return true;
  }

  @Override
  public Condition renamed(Map<String, ? extends Collection<VariableRenaming>> renamings) {
    return this;
  }
}
