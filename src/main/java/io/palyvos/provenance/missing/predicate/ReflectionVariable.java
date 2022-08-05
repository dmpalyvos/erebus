package io.palyvos.provenance.missing.predicate;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReflectionVariable implements Variable {

  private static final Logger LOG = LoggerFactory.getLogger(ReflectionVariable.class);
  private final ReflectionLoadStrategy strategy;
  private final String name;
  private final String id;
  private final TransformFunction transform;
  private final String transformAsString;
  private transient Object value;
  private boolean isLoaded;
  private boolean canLoad = true;

  public static Variable fromField(String name) {
    return new ReflectionVariable(name, new FieldLoadStrategy());
  }

  public static Variable fromMethod(String name) {
    return new ReflectionVariable(name, new MethodLoadStrategy());
  }

  private ReflectionVariable(String name, ReflectionLoadStrategy strategy) {
    this(name, strategy, null, "");
  }

  private ReflectionVariable(String name,
      ReflectionLoadStrategy strategy,
      TransformFunction transform, String transformAsString) {
    Validate.notBlank(name, "name");
    Validate.notNull(strategy, "strategy");
    Validate.notNull(transformAsString, "transformAsString");
    this.name = name;
    this.strategy = strategy;
    this.id = strategy + "." + name;
    this.transform = transform;
    this.transformAsString = transformAsString;
  }

  @Override
  public void setValue(Object value, boolean isLoaded) {
    this.value = value;
    this.isLoaded = isLoaded;
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public void clear() {
    this.value = null;
    this.isLoaded = false;
  }

  @Override
  public void load(Object object) {
    isLoaded = false;
    if (!canLoad) {
      // Field/method does not exist, no need to retry
      return;
    }
    try {
      this.value = strategy.load(name, object);
    } catch (NoSuchMethodException | NoSuchFieldException e) {
      // Exception will happen only at first attempt, then variable will be disabled
      LOG.debug("Failed to load field {} of class {}", name, object.getClass().getName());
      canLoad = false;
      isLoaded = false;
      return;
    } catch (IllegalAccessException | InvocationTargetException e) {
      LOG.error("Failed to load variable {}", id);
      throw new RuntimeException(e);
    }

    if (transform != null) {
      this.value = transform.apply(value);
    }
    isLoaded = true;
  }

  @Override
  public List<Variable> renamed(Map<String, ? extends Collection<VariableRenaming>> renamings) {
    Collection<VariableRenaming> renamed = renamings.get(name);
    return renamed == null ?
        Arrays.asList(new ReflectionVariable(this.name, this.strategy.newInstance())) :
        renamed.stream()
            .map(renaming -> new ReflectionVariable(renaming.name(), this.strategy.newInstance(),
                renaming.transform().orElse(null),
                renaming.transformKeys().toString()))
            .collect(Collectors.toList());
  }

  @Override
  public boolean isLoaded() {
    return isLoaded;
  }

  @Override
  public boolean asBoolean() {
    return (boolean) value;
  }

  @Override
  public int asInt() {
    return (int) value;
  }

  @Override
  public long asLong() {
    return (long) value;
  }

  @Override
  public double asDouble() {
    return (double) value;
  }

  @Override
  public String asString() {
    return (String) value;
  }

  @Override
  public Object asObject() {
    return value;
  }

  private static class FieldLoadStrategy implements ReflectionLoadStrategy {

    private transient Field field;

    @Override
    public ReflectionLoadStrategy newInstance() {
      return new FieldLoadStrategy();
    }

    @Override
    public Object load(String name, Object object)
        throws IllegalAccessException, InvocationTargetException, NoSuchFieldException {
      if (field == null) {
        field = object.getClass().getField(name);
        field.setAccessible(true); // Disable security checks for small speed gain
      }
      return field.get(object);
    }

    @Override
    public String toString() {
      return "FIELD";
    }
  }

  private static class MethodLoadStrategy implements ReflectionLoadStrategy {

    private transient Method method;

    @Override
    public ReflectionLoadStrategy newInstance() {
      return new MethodLoadStrategy();
    }

    @Override
    public Object load(String name, Object object)
        throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
      if (method == null) {
        method = object.getClass().getMethod(name);
        method.setAccessible(true); // Disable security checks for small speed gain
      }
      return method.invoke(object);
    }

    @Override
    public String toString() {
      return "METHOD";
    }
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("id", id)
        .append("transform", transformAsString)
        .append("value", value)
        .append("canLoad", canLoad)
        .append("isLoaded", isLoaded)
        .toString();
  }
}
