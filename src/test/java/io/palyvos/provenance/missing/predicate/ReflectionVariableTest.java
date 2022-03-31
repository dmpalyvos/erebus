package io.palyvos.provenance.missing.predicate;


import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ReflectionVariableTest {

  private static final int TIMESTAMP = 100;

  @Test
  public void testFromField()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    Variable var = ReflectionVariable.fromField("timestamp");
    TestTuple tuple = new TestTuple();
    var.load(tuple);
    Assert.assertTrue(var.isLoaded(), "Variable not loaded");
    Assert.assertEquals(var.asLong(), tuple.timestamp, "Wrong timestamp");
  }

  @Test
  public void testFromMethod()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    Variable var = ReflectionVariable.fromMethod("getTimestamp");
    TestTuple tuple = new TestTuple();
    var.load(tuple);
    Assert.assertTrue(var.isLoaded(), "Variable not loaded");
    Assert.assertEquals(var.asLong(), tuple.timestamp, "Wrong timestamp");
  }

  @Test
  public void testRename()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    TestTuple tuple = new TestTuple();
    Map<String, List<VariableRenaming>> renaming = new HashMap<>();
    renaming.put("f0",
        Arrays.asList(VariableRenaming.of("timestamp"), VariableRenaming.of("otherAttribute")));
    Variable var = ReflectionVariable.fromField("f0");
    List<Variable> renamed = var.renamed(renaming);
    Assert.assertEquals(renamed.size(), 2, "Wrong number of renamed variables");
    Variable r0 = renamed.get(0);
    Variable r1 = renamed.get(1);
    r0.load(tuple);
    r1.load(tuple);
    Assert.assertEquals(r0.asLong(), tuple.timestamp, "Wrong timestamp renaming");
    Assert.assertEquals(r1.asLong(), tuple.otherAttribute, "Wrong otherAttribute renaming");
  }

  @Test
  public void testRenameWithTransform()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    TestTuple tuple = new TestTuple();
    Map<String, List<VariableRenaming>> renaming = new HashMap<>();
    final Map<String, TransformFunction> registeredTransforms = new HashMap<>();
    final TransformFunction F = v -> (long) v * (long) v;
    final TransformFunction G = v -> (long) v - 33;
    registeredTransforms.put("f", F);
    registeredTransforms.put("g", G);
    final VariableRenaming otherAttributeRenaming = new VariableRenaming("otherAttribute",
        Arrays.asList("f", "g"));
    otherAttributeRenaming.computeTransform(registeredTransforms);
    renaming.put("f0",
        Arrays.asList(VariableRenaming.of("timestamp"), otherAttributeRenaming));
    Variable var = ReflectionVariable.fromField("f0");
    List<Variable> renamed = var.renamed(renaming);
    Assert.assertEquals(renamed.size(), 2, "Wrong number of renamed variables");
    Variable r0 = renamed.get(0);
    Variable r1 = renamed.get(1);
    r0.load(tuple);
    r1.load(tuple);
    Assert.assertEquals(r0.asLong(), tuple.timestamp, "Wrong timestamp renaming");
    Assert.assertEquals(r1.asLong(), F.andThen(G).apply(tuple.otherAttribute),
        "Wrong otherAttribute transformed renaming");
  }

  public void testFromFieldPrivate()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    Variable var = ReflectionVariable.fromField("stimulus");
    TestTuple tuple = new TestTuple();
    var.load(tuple);
    Assert.assertFalse(var.isLoaded(), "Field is not accesible, loaded should be false");
  }

}