package io.palyvos.provenance.missing.predicate;

import io.palyvos.provenance.missing.predicate.Predicate.PredicateStrategy;
import java.util.Collection;
import java.util.Collections;
import java.util.OptionalLong;
import org.testng.Assert;
import org.testng.annotations.Test;

public class PredicateTest {

  private static final Condition EXCEPTION_THROWING_CONDITION = new Condition() {
    @Override
    public Collection<Variable> variables() {
      return Collections.emptyList();
    }

    @Override
    public boolean evaluate(long timestamp) {
      throw new IllegalStateException("Invalid condition reached");
    }

    @Override
    public boolean isLoaded() {
      return true;
    }

  };

  @Test
  public void testEvaluateAndOr() {
    Predicate predicate = newAndOrMissingPredicate();
    TestTuple tuple = new TestTuple();
    Assert.assertTrue(predicate.isLoaded(), "Predicate not loaded");
    Assert.assertEquals(predicate.evaluate(tuple, 0), true, "Predicate evaluation wrong");
  }

  @Test
  public void testEvaluateAndOrSecondCondition() {
    Predicate predicate = newAndOrMissingPredicate();
    TestTuple tuple = new TestTuple();
    tuple.timestamp = 151;
    Assert.assertTrue(predicate.isLoaded(), "Predicate not loaded");
    Assert.assertEquals(predicate.evaluate(tuple, 0), true, "Predicate evaluation wrong");
  }

  @Test
  public void testDisabled() {
    Predicate predicate = Predicate.disabled();
    Assert.assertTrue(predicate.isLoaded(), "Predicate not loaded");
    Assert.assertFalse(predicate.isEnabled(), "Disabled predicate seems enabled!");
    Assert.assertEquals(predicate.evaluate(new TestTuple(), 0), false,
        "Disabled predicate wrong evaluation");
  }

  @Test
  public void testEvaluateAndOrTimestampShiftTrue() {
    Predicate predicate = newAndOrMissingPredicateTimestampConditions().manualTimeShifted(
        (l, r) -> OptionalLong.of(l - 52), (l, r) -> OptionalLong.of(r));
    TestTuple tuple = new TestTuple();
    Assert.assertTrue(predicate.isLoaded(), "Predicate not loaded");
    Assert.assertEquals(predicate.evaluate(tuple, tuple.timestamp), true,
        "Predicate evaluation wrong");
  }

  @Test
  public void testEvaluateAndOrTimestampShiftFalse() {
    Predicate predicate = newAndOrMissingPredicateTimestampConditions().manualTimeShifted(
        (l, r) -> OptionalLong.of(l - 30), (l, r) -> OptionalLong.of(r - 30));
    TestTuple tuple = new TestTuple();
    Assert.assertEquals(
        predicate.evaluate(tuple, tuple.timestamp), false, "Predicate evaluation wrong");
  }

  @Test
  public void testEvaluateAndOrFalse() {
    Predicate predicate = newAndOrMissingPredicate();
    TestTuple tuple = new TestTuple();
    tuple.timestamp = -10; // Make predicate evaluate to false
    Assert.assertTrue(predicate.isLoaded(), "Predicate not loaded");
    Assert.assertEquals(predicate.evaluate(tuple, 0), false, "Predicate evaluation wrong");
  }

  private static Predicate newAndOrMissingPredicate() {
    Predicate predicate = Predicate.of(PredicateStrategy.OR, Predicate.of(
            PredicateStrategy.AND,
            new OneVariableCondition(ReflectionVariable.fromField("timestamp"),
                var -> var.asLong() > 15),
            new OneVariableCondition(ReflectionVariable.fromField("timestamp"),
                var -> var.asLong() < 101),
            new OneVariableCondition(ReflectionVariable.fromField("nonexistent"), var -> false)),
        Predicate.of(PredicateStrategy.AND,
            new OneVariableCondition(ReflectionVariable.fromField("timestamp"),
                var -> var.asLong() > 150)));
    return predicate;
  }

  private static Predicate newAndOrMissingPredicateTimestampConditions() {
    Predicate predicate = Predicate.of(PredicateStrategy.OR, Predicate.of(
            PredicateStrategy.AND,
            TimestampCondition.between(15, 101),
            new OneVariableCondition(ReflectionVariable.fromField("nonexistent"), var -> false)),
        Predicate.of(PredicateStrategy.AND,
            TimestampCondition.greater(150)));
    return predicate;
  }


  @Test
  public void testMinTimeBoundary() {
    Predicate withoutBoundary = newAndOrMissingPredicate();
    Assert.assertEquals(withoutBoundary.minTimeBoundary(), OptionalLong.empty(),
        "Wrong min boundary for predicate without boundaries");
    Predicate withBoundary = newAndOrMissingPredicateTimestampConditions();
    Assert.assertEquals(withBoundary.minTimeBoundary(), OptionalLong.of(15),
        "Wrong max boundary for predicate with boundaries");
  }

  @Test
  public void testMaxTimeBoundary() {
    Predicate withoutBoundary = newAndOrMissingPredicate();
    Assert.assertEquals(withoutBoundary.maxTimeBoundary(), OptionalLong.empty(),
        "Wrong max boundary for predicate without boundaries");
    Predicate withBoundary = newAndOrMissingPredicateTimestampConditions();
    Assert.assertEquals(withBoundary.maxTimeBoundary(),
        OptionalLong.of(TimestampCondition.NO_RIGHT_BOUNDARY),
        "Wrong max boundary for predicate with boundaries");
  }

  @Test
  public void testConditions() {
    Predicate predicate = newAndOrMissingPredicateTimestampConditions();
    final Collection<Condition> conditions = predicate.baseConditions();
    Assert.assertEquals(conditions.size(), 3);
  }

  @Test
  public void testAlwaysTrue() {
    Predicate predicate = Predicate.alwaysTrue();
    Assert.assertTrue(predicate.isEnabled(), "Always true predicate must be enabled!");
    Assert.assertTrue(predicate.isSatisfiable());
    Assert.assertTrue(predicate.evaluate(new Object(), 123),
        "Always true predicate must always evaluate to true!");
  }

  @Test
  public void testAlwaysFalse() {
    Predicate predicate = Predicate.alwaysFalse();
    Assert.assertTrue(predicate.isEnabled(), "Always false predicate must be enabled!");
    Assert.assertFalse(predicate.isSatisfiable());
    Assert.assertFalse(predicate.evaluate(new Object(), 123),
        "Always false predicate must always evaluate to false!");
  }

  @Test
  public void testSatisfiableFalse() {
    Predicate predicate = Predicate.of(PredicateStrategy.AND, Predicate.alwaysFalse(),
        Predicate.alwaysTrue());
    Assert.assertTrue(predicate.isEnabled());
    Assert.assertFalse(predicate.isSatisfiable());
  }

  @Test
  public void testSatisfiableTrue() {
    Predicate predicate = Predicate.of(PredicateStrategy.OR, Predicate.alwaysFalse(),
        Predicate.alwaysTrue());
    Assert.assertTrue(predicate.isEnabled());
    Assert.assertTrue(predicate.isSatisfiable());
  }

  @Test
  public void testSatisfiableCompositeTrue() {
    Predicate predicate = Predicate.of(PredicateStrategy.AND,
        Predicate.of(PredicateStrategy.OR, Predicate.alwaysFalse(),
            Predicate.alwaysTrue()),
        Predicate.of(PredicateStrategy.AND, Predicate.alwaysTrue(), Predicate.alwaysTrue()));
    Assert.assertTrue(predicate.isEnabled());
    Assert.assertTrue(predicate.isSatisfiable());
  }

  @Test
  public void testSatisfiableCompositeFalse() {
    Predicate predicate = Predicate.of(PredicateStrategy.AND,
        Predicate.of(PredicateStrategy.OR, Predicate.alwaysFalse(),
            Predicate.alwaysTrue()),
        Predicate.of(PredicateStrategy.AND, Predicate.alwaysTrue(), Predicate.alwaysFalse()));
    Assert.assertTrue(predicate.isEnabled(), "Always false predicate must be enabled!");
    Assert.assertFalse(predicate.isSatisfiable());
  }


  @Test(expectedExceptions = {IllegalArgumentException.class})
  public void testOf() {
    Predicate.of(PredicateStrategy.AND); // At least one condition required
  }

  @Test
  public void testORPredicateStrategyEarlyTermination() {
    Predicate predicate = Predicate.of(PredicateStrategy.OR, Predicate.alwaysTrue(),
        EXCEPTION_THROWING_CONDITION);
    Assert.assertTrue(predicate.evaluate(new Object(), 123), "Early termination failed");
  }

  @Test
  public void testANDPredicateStrategyEarlyTermination() {
    Predicate predicate = Predicate.of(PredicateStrategy.AND, Predicate.alwaysFalse(),
        EXCEPTION_THROWING_CONDITION);
    Assert.assertFalse(predicate.evaluate(new Object(), 123), "Early termination failed");
  }
}
