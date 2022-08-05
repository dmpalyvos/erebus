package io.palyvos.provenance.missing.predicate;

import java.lang.reflect.InvocationTargetException;
import java.util.OptionalLong;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TimestampConditionTest {

  @Test
  public void testLess()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    TestTuple tuple = new TestTuple();
    TimestampCondition condition = TimestampCondition.less(30);
    ConditionHelper.load(condition, tuple);
    Assert.assertEquals(condition.evaluate(0), true, "Wrong evaluation for shift=0");
    Assert.assertEquals(condition.evaluate(30), false, "Wrong evaluation for shift=0");
    Assert.assertEquals(condition.evaluate(35), false, "Wrong evaluation for shift=30");
  }

  @Test
  public void testLessEqual()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    TestTuple tuple = new TestTuple();
    TimestampCondition condition = TimestampCondition.lessEqual(30);
    ConditionHelper.load(condition, tuple);
    Assert.assertEquals(condition.evaluate(0), true, "Wrong evaluation for shift=0");
    Assert.assertEquals(condition.evaluate(30), true, "Wrong evaluation for shift=0");
    Assert.assertEquals(condition.evaluate(35), false, "Wrong evaluation for shift=30");
  }

  @Test
  public void testGreater()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    TestTuple tuple = new TestTuple();
    TimestampCondition condition = TimestampCondition.greater(30);
    ConditionHelper.load(condition, tuple);
    Assert.assertEquals(condition.evaluate(0), false, "Wrong evaluation");
    Assert.assertEquals(condition.evaluate(30), false, "Wrong evaluation");
    Assert.assertEquals(condition.evaluate(35), true, "Wrong evaluation");
  }

  @Test
  public void testGreaterEqual()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    TestTuple tuple = new TestTuple();
    TimestampCondition condition = TimestampCondition.greaterEqual(30);
    ConditionHelper.load(condition, tuple);
    Assert.assertEquals(condition.evaluate(0), false, "Wrong evaluation");
    Assert.assertEquals(condition.evaluate(30), true, "Wrong evaluation");
    Assert.assertEquals(condition.evaluate(35), true, "Wrong evaluation");
  }

  @Test
  public void testBetween()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    TestTuple tuple = new TestTuple();
    TimestampCondition condition = TimestampCondition.between(10, 30);
    ConditionHelper.load(condition, tuple);
    Assert.assertEquals(condition.evaluate(15), true, "Wrong evaluation");
    Assert.assertEquals(condition.evaluate(5), false, "Wrong evaluation");
    Assert.assertEquals(condition.evaluate(10), true, "Wrong evaluation");
    Assert.assertEquals(condition.evaluate(30), false, "Wrong evaluation");
    Assert.assertEquals(condition.evaluate(35), false, "Wrong evaluation");
  }

  @Test
  public void testBetweenRightInclusive()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    TestTuple tuple = new TestTuple();
    TimestampCondition condition = TimestampCondition.betweenRightInclusive(10, 30);
    ConditionHelper.load(condition, tuple);
    Assert.assertEquals(condition.evaluate(15), true, "Wrong evaluation");
    Assert.assertEquals(condition.evaluate(5), false, "Wrong evaluation");
    Assert.assertEquals(condition.evaluate(10), true, "Wrong evaluation");
    Assert.assertEquals(condition.evaluate(30), true, "Wrong evaluation");
    Assert.assertEquals(condition.evaluate(35), false, "Wrong evaluation");
  }

  @Test
  public void testTimeShifted()
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    TestTuple tuple = new TestTuple();
    Condition condition = TimestampCondition.betweenRightInclusive(10, 30)
        .manualTimeShifted((l, r) -> OptionalLong.of(l - 5), (l, r) -> OptionalLong.of(r - 3));
    ConditionHelper.load(condition, tuple);
    Assert.assertEquals(condition.evaluate(15), true, "Wrong evaluation");
    Assert.assertEquals(condition.evaluate(0), false, "Wrong evaluation");
    Assert.assertEquals(condition.evaluate(10), true, "Wrong evaluation");
    Assert.assertEquals(condition.evaluate(27), true, "Wrong evaluation");
    Assert.assertEquals(condition.evaluate(35), false, "Wrong evaluation");
  }

  @Test
  public void testTimeShiftedMissing() {
    TestTuple tuple = new TestTuple();
    Condition condition = TimestampCondition.betweenRightInclusive(10, 30)
        .manualTimeShifted((l, r) -> OptionalLong.empty(), (l, r) -> OptionalLong.of(r - 3));
    Assert.assertTrue(condition instanceof UnsatisfiableCondition);
    Assert.assertFalse(condition.isSatisfiable());
  }

}
