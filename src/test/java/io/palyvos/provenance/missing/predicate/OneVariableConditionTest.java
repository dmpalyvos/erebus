package io.palyvos.provenance.missing.predicate;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;

public class OneVariableConditionTest {

  @Test
  public void testIsLoaded()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    TestTuple tuple = new TestTuple();
    Condition condition = newCondition("timestamp");
    ConditionHelper.load(condition, tuple);
    Assert.assertTrue(condition.isLoaded(), "Condition not loaded");
  }

  @Test
  public void testEvaluate()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    TestTuple tuple = new TestTuple();
    Condition condition = newCondition("timestamp");
    ConditionHelper.load(condition, tuple);
    Assert.assertTrue(condition.isLoaded(), "Condition not loaded");
    Assert.assertEquals(condition.evaluate(0), true, "Wrong evaluation");
  }


  @Test
  public void testIsLoadedMissingField() {
    TestTuple tuple = new TestTuple();
    Condition condition = newCondition("nonexisting");
    for (Variable variable : condition.variables()) {
      try {
        variable.load(tuple);
      } catch (Exception e) {
      }
    }
    Assert.assertTrue(!condition.isLoaded(), "Condition (wrongly) loaded");
  }


  OneVariableCondition newCondition(String field) {
    return new OneVariableCondition(ReflectionVariable.fromField(field),
        var -> var.asLong() == TestTuple.TIMESTAMP);
  }

  @Test
  public void testRename()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    TestTuple tuple = new TestTuple();
    Map<String, List<VariableRenaming>> renaming = new HashMap<>();
    renaming.put("f0",
        Arrays.asList(VariableRenaming.of("timestamp"), VariableRenaming.of("otherAttribute")));
    Variable var = ReflectionVariable.fromField("f0");
    Condition condition = new OneVariableCondition(var, v -> v.asLong() > 10);
    Condition renamed = condition.renamed(renaming);
    Assert.assertTrue(renamed instanceof Predicate, "Expected predicate class from this renaming");
    Predicate renamedAsPredicate = (Predicate) renamed;
    Assert.assertEquals(renamedAsPredicate.evaluate(tuple, 0), true, "Wrong renaming evaluation");
    tuple.otherAttribute = 0;
    Assert.assertEquals(renamedAsPredicate.evaluate(tuple, 0), false, "Wrong renaming evaluation");
  }

}