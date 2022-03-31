package io.palyvos.provenance.missing.predicate;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;

interface ReflectionLoadStrategy extends Serializable {

  Object load(String name, Object object)
      throws IllegalAccessException, InvocationTargetException, NoSuchFieldException, NoSuchMethodException;

  ReflectionLoadStrategy newInstance();

}
