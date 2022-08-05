package io.palyvos.provenance.missing.predicate;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TwoVariableConditionTest {

  @Test
  public void testLoaded()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    TestTuple tuple = new TestTuple();
    Condition condition = newCondition();
    ConditionHelper.load(condition, tuple);
    Assert.assertTrue(condition.isLoaded(), "Condition not loaded");
  }

  @Test
  public void testEvaluateTrue()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    TestTuple tuple = new TestTuple();
    Condition condition = newCondition();
    ConditionHelper.load(condition, tuple);
    Assert.assertTrue(condition.isLoaded(), "Condition not loaded");
    Assert.assertEquals(condition.evaluate(0), true, "Wrong evaluation");
  }

  @Test
  public void testEvaluateFalse()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    TestTuple tuple = new TestTuple();
    tuple.setStimulus(TestTuple.TIMESTAMP + 1);
    Condition condition = newCondition();
    ConditionHelper.load(condition, tuple);
    Assert.assertTrue(condition.isLoaded(), "Condition not loaded");
    Assert.assertEquals(condition.evaluate(0), false, "Wrong evaluation");
  }

  private static TwoVariableCondition newCondition() {
    return new TwoVariableCondition(
        ReflectionVariable.fromField("timestamp"),
        ReflectionVariable.fromMethod("getStimulus"),
        (var1, var2) -> var1.asLong() > var2.asLong());
  }

  @Test
  public void testRename()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    TestTuple tuple = new TestTuple();
    Map<String, List<VariableRenaming>> renaming = new HashMap<>();
    renaming.put("f0", Arrays.asList(RenamingHelper.testInstance("timestamp", "o1"),
        RenamingHelper.testInstance("otherAttribute", "o2")));
    Variable var1 = ReflectionVariable.fromField("f0");
    Variable var2 = ReflectionVariable.fromMethod("getStimulus");
    VariableCondition condition = new TwoVariableCondition(var1, var2,
        ((v1, v2) -> v1.asLong() > v2.asLong()));
    Condition renamed = condition.renamed(renaming);
    tuple.timestamp = 100; // var1
    tuple.otherAttribute = 10; // var1
    tuple.setStimulus(0); // var2
    Assert.assertTrue(renamed instanceof Predicate, "Expected predicate class from this renaming");
    Predicate renamedAsPredicate = (Predicate) renamed;
    Assert.assertEquals(renamedAsPredicate.evaluate(tuple, 0), true, "Renaming evaluation failed");
    tuple.timestamp = -1; // var1
    tuple.otherAttribute = 10; // var1
    tuple.setStimulus(0); // var2
    Assert.assertEquals(renamedAsPredicate.evaluate(tuple, 0), false,
        "Renaming evaluation 2 failed");
    tuple.timestamp = 100; // var1
    tuple.otherAttribute = -1; // var1
    tuple.setStimulus(0); // var2
    Assert.assertEquals(renamedAsPredicate.evaluate(tuple, 0), false,
        "Renaming evaluation 3 failed");
  }

}
