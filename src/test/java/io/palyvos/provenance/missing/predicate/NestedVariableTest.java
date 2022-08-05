package io.palyvos.provenance.missing.predicate;


import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;

public class NestedVariableTest {

  @Test
  public void testLoad()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    TestTuple tuple = new TestTuple();
    Variable variable = NestedVariable.of(ReflectionVariable.fromMethod("getNested"),
        ReflectionVariable.fromField("f0"));
    variable.load(tuple);
    Assert.assertTrue(variable.isLoaded(), "Variable failed to load");
    Assert.assertEquals(variable.asInt(), tuple.getNested().f0, "Wrong nested value");
  }


  @Test
  public void testSetValue()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    Variable variable = NestedVariable.of(ReflectionVariable.fromMethod("getNested"),
        ReflectionVariable.fromField("f0"));
    variable.setValue(5, true);
    Assert.assertTrue(variable.isLoaded(), "Variable failed to load");
    Assert.assertEquals(variable.asInt(), 5, "Wrong set value");
  }

  @Test
  public void testSetValueFalse()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    Variable variable = NestedVariable.of(ReflectionVariable.fromMethod("getNested"),
        ReflectionVariable.fromField("f0"));
    variable.setValue(5, false);
    Assert.assertTrue(!variable.isLoaded(), "Wrong isLoaded status");
  }


  @Test
  public void testRenameTwoNested()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    TestTuple tuple = new TestTuple();
    Map<String, List<VariableRenaming>> renaming = new HashMap<>();
    renaming.put("first",
        Arrays.asList(RenamingHelper.testInstance("getNested"), RenamingHelper.testInstance("getNested2")));
    renaming.put("second",
        Arrays.asList(RenamingHelper.testInstance("f0"), RenamingHelper.testInstance("f1")));
    Variable variable = NestedVariable.of(ReflectionVariable.fromMethod("first"),
        ReflectionVariable.fromField("second"));
    List<Variable> renamed = variable.renamed(renaming);
    Assert.assertEquals(renamed.size(), 4, "Wrong number of renamed variables");
    Variable r00 = renamed.get(0);
    Variable r01 = renamed.get(1);
    Variable r10 = renamed.get(2);
    Variable r11 = renamed.get(3);
    r00.load(tuple);
    r01.load(tuple);
    r10.load(tuple);
    r11.load(tuple);
    Assert.assertEquals(r00.asInt(), tuple.getNested().f0, "Wrong nested f0 renaming");
    Assert.assertEquals(r01.asLong(), tuple.getNested().f1, "Wrong nested f1 renaming");
    Assert.assertEquals(r10.asInt(), tuple.getNested2().f0, "Wrong nested2 f0 renaming");
    Assert.assertEquals(r11.asLong(), tuple.getNested2().f1, "Wrong nested2 f1 renaming");
  }

  @Test
  public void testRenameThreeNested()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    TestTuple tuple = new TestTuple();
    Map<String, List<VariableRenaming>> renaming = new HashMap<>();
    renaming.put("first", Arrays.asList(RenamingHelper.testInstance("getNested")));
    renaming.put("second", Arrays.asList(RenamingHelper.testInstance("doubleNested")));
    renaming.put("third",
        Arrays.asList(RenamingHelper.testInstance("f0"), RenamingHelper.testInstance("f1")));
    Variable variable = NestedVariable.of(ReflectionVariable.fromMethod("first"),
        ReflectionVariable.fromField("second"), ReflectionVariable.fromField("third"));
    List<Variable> renamed = variable.renamed(renaming);
    Assert.assertEquals(renamed.size(), 2, "Wrong number of renamed variables");
    Variable r00 = renamed.get(0);
    Variable r01 = renamed.get(1);
    r00.load(tuple);
    r01.load(tuple);
    Assert.assertEquals(r00.asInt(), tuple.getNested().doubleNested.f0, "Wrong nested f0 renaming");
    Assert.assertEquals(r01.asLong(), tuple.getNested().doubleNested.f1,
        "Wrong nested f1 renaming");
  }


}