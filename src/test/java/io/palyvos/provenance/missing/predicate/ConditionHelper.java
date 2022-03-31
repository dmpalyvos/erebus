package io.palyvos.provenance.missing.predicate;

import java.lang.reflect.InvocationTargetException;

class ConditionHelper {

  public static void load(Condition condition, Object object)
      throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    for (Variable variable : condition.variables()) {
      variable.load(object);
    }
  }

  private ConditionHelper() {

  }
}
