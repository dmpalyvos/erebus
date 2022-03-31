package io.palyvos.provenance.missing.predicate;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Predicate implements Condition, Serializable {


  private static final Logger LOG = LoggerFactory.getLogger(Predicate.class);
  private static final String NON_TRANSFORMED_PREDICATE_NAME = "UNDEFINED";
  private static final int DEFAULT_ACTIVATION_TIME = 0;
  private static final Condition FALSE_CONDITION = new UnsatisfiableCondition(null,
      "FALSE_CONDITION");

  private final List<Condition> conditions = new ArrayList<>();
  private final Map<String, List<Variable>> variablesById = new HashMap<>();
  private final List<Variable> variables = new ArrayList<>();
  private final PredicateStrategy strategy;
  private final String operator;
  private boolean enabled;
  private final UUID uid;
  // Lazy initialized boundaries because of OptionalLong's non-serializability
  private transient OptionalLong maxTimeBoundary;
  private transient OptionalLong minTimeBoundary;

  public static Predicate of(PredicateStrategy strategy, Condition... conditions) {
    Validate.notEmpty(conditions, "At least one condition required");
    return new Predicate(Arrays.asList(conditions), strategy, NON_TRANSFORMED_PREDICATE_NAME,
        UUID.randomUUID(),
        true);
  }

  public static Predicate disabled() {
    return new Predicate(Collections.singletonList(FALSE_CONDITION), PredicateStrategy.AND,
        NON_TRANSFORMED_PREDICATE_NAME, UUID.randomUUID(), false);
  }

  public static Predicate alwaysFalse() {
    return new Predicate(Collections.singletonList(FALSE_CONDITION), PredicateStrategy.AND,
        NON_TRANSFORMED_PREDICATE_NAME, UUID.randomUUID(), true);
  }


  public static Predicate alwaysTrue() {
    return new Predicate(Collections.emptyList(), PredicateStrategy.AND,
        NON_TRANSFORMED_PREDICATE_NAME, UUID.randomUUID(), true);
  }


  private Predicate(Collection<Condition> conditions,
      PredicateStrategy strategy, String operator, UUID uid, boolean enabled) {
    Validate.notNull(conditions, "conditions");
    Validate.notNull(strategy, "strategy");
    Validate.notEmpty(operator, "operator");
    Validate.notNull(uid, "uid");
    this.conditions.addAll(conditions);
    for (Condition condition : conditions) {
      storeVariables(condition.variables());
    }
    this.strategy = strategy;
    this.operator = operator;
    this.uid = uid;
    this.enabled = enabled;
  }

  private void storeVariables(Collection<Variable> vars) {
    this.variables.addAll(vars);
    for (Variable var : vars) {
      List<Variable> sameIdVars = variablesById.getOrDefault(var.id(), new ArrayList<>());
      sameIdVars.add(var);
      variablesById.put(var.id(), sameIdVars);
    }
  }

  @Override
  public Collection<Variable> variables() {
    return Collections.unmodifiableCollection(variables);
  }

  private void load(Object object, String variableId) {
    List<Variable> sameIdVariables = variablesById.get(variableId);
    // Load only the first variable
    // and set the value for all other vars with the same ID
    final Variable first = sameIdVariables.get(0);
    first.load(object);
    for (Variable v : sameIdVariables) {
      // Set same value and same loaded status
      // (if one failed to load, then all failed to load)
      v.setValue(first.asObject(), first.isLoaded());
    }
  }

  @Override
  public Predicate renamed(Map<String, ? extends Collection<VariableRenaming>> renamings) {
    return renamed(renamings, this.operator);
  }

  public Predicate renamed(String operator, String sink, QueryGraphInfo queryGraphInfo) {
    return renamed(queryGraphInfo.sinkToOperatorVariables(operator, sink), operator);
  }

  public Predicate renamed(String operator) {
    return new Predicate(conditions, strategy, operator, uid, enabled);
  }

  private Predicate renamed(Map<String, ? extends Collection<VariableRenaming>> renaming,
      String operator) {
    List<Condition> renamed = conditions.stream()
        .map(c -> c.renamed(renaming))
        .collect(Collectors.toList());
    return new Predicate(renamed, strategy, operator, uid, enabled);
  }

  public Predicate transformed(String operator, String sink, QueryGraphInfo queryGraphInfo) {
    final Predicate transformed = renamed(operator, sink, queryGraphInfo).timeShifted(operator,
        sink,
        queryGraphInfo);
    LOG.info("Transformed Predicate\n{}", transformed);
    return transformed;
  }

  @Override
  public Predicate timeShifted(
      BiFunction<Long, Long, OptionalLong> leftBoundaryTransform,
      BiFunction<Long, Long, OptionalLong> rightBoundaryTransform) {
    return timeShifted(leftBoundaryTransform, rightBoundaryTransform, this.operator);
  }

  private Predicate timeShifted(
      BiFunction<Long, Long, OptionalLong> leftBoundaryTransform,
      BiFunction<Long, Long, OptionalLong> rightBoundaryTransform, String operator) {
    List<Condition> shifted = conditions.stream()
        .map(c -> c.timeShifted(leftBoundaryTransform, rightBoundaryTransform))
        .collect(Collectors.toList());
    return new Predicate(shifted, strategy, operator, uid, enabled);
  }

  public Predicate timeShifted(String operator, String sink, QueryGraphInfo queryGraphInfo) {
    return timeShifted(
        (leftBoundary, rightBoundary) -> queryGraphInfo.transformIntervalStartFromSink(sink,
            operator, leftBoundary, rightBoundary),
        (leftBoundary, rightBoundary) -> queryGraphInfo.transformIntervalEndFromSink(sink,
            operator, leftBoundary, rightBoundary), operator);
  }

  public Predicate deepCopy() {
    // Create deep copy of predicate when thread safety is needed
    return SerializationUtils.clone(this);
  }

  @Override
  public boolean evaluate(long timestamp) {
    throw new UnsupportedOperationException(
        "This method should not be called for Predicate. Use evaluate(Object, timestamp) instead!");
  }

  public boolean evaluate(Object object, long timestamp) {
    clearAllVariables();
    return doEvaluate(object, timestamp);
  }

  private void clearAllVariables() {
    for (Variable var : variables) {
      var.clear();
    }
  }

  protected boolean doEvaluate(Object object, long timestamp) {
    boolean result = strategy.initialValue;
    for (Condition condition : conditions) {
      boolean conditionResult = computeResult(object, timestamp, condition);
      result = strategy.reduce(result, conditionResult);
      if (strategy.canTerminateEarly(result)) {
        return result;
      }
    }
    return result;
  }

  private boolean computeResult(Object object, long timestamp, Condition condition) {
    if (condition instanceof Predicate) {
      return ((Predicate) condition).doEvaluate(object, timestamp);
    }
    for (Variable variable : condition.variables()) {
      if (!variable.isLoaded()) {
        load(object, variable.id());
      }
      final boolean variableNotExists = !variable.isLoaded();
      if (variableNotExists) {
        // Undefined conditions return true by default
        return true;
      }
    }
    // All variables loaded successfuly, return condition result
    return condition.evaluate(timestamp);
  }

  @Override
  public boolean isSatisfiable() {
    return strategy.isSatisfiable(conditions);
  }

  public Set<Condition> baseConditions() {
    final Set<Condition> result = new HashSet<>();
    final Queue<Condition> queue = new ArrayDeque<>();
    conditions.forEach(c -> queue.add(c));
    while (!queue.isEmpty()) {
      final Condition current = queue.remove();
      if (current instanceof Predicate) {
        ((Predicate) current).baseConditions().stream()
            .filter(c -> !result.contains(c))
            .forEach(c -> queue.add(c));
      } else {
        if (!result.contains(current)) {
          result.add(current);
        }
      }
    }
    return Collections.unmodifiableSet(result);
  }

  @Override
  public OptionalLong minTimeBoundary() {
    if (minTimeBoundary == null) {
      minTimeBoundary = conditions.stream().map(Condition::minTimeBoundary)
          .filter(OptionalLong::isPresent)
          .mapToLong(OptionalLong::getAsLong).min();
    }
    return minTimeBoundary;
  }

  @Override
  public OptionalLong maxTimeBoundary() {
    if (maxTimeBoundary == null) {
      maxTimeBoundary = conditions.stream().map(Condition::maxTimeBoundary)
          .filter(OptionalLong::isPresent)
          .mapToLong(OptionalLong::getAsLong).max();
    }
    return maxTimeBoundary;
  }

  public boolean hasExpired(long watermark) {
    return watermark > maxTimeBoundary().orElse(Long.MAX_VALUE);
  }

  @Override
  public boolean isLoaded() {
    // Predicate is always defined (although sub-conditions might not be)
    return true;
  }

  public String operator() {
    return operator;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void disable() {
    this.enabled = false;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("uid", this.uid)
        .append("operator", operator)
        .append("strategy", strategy)
        .append("conditions", conditions)
        .append("enabled", enabled)
        .toString();
  }

  public UUID uid() {
    return this.uid;
  }

  public String description() {
    final String delimiter = " " + strategy.name() + " ";
    return "(" + conditions.stream().map(Condition::description)
        .collect(Collectors.joining(delimiter)) + ")";
  }

  public enum PredicateStrategy implements Serializable {
    AND(true) {
      @Override
      protected boolean reduce(boolean current, boolean value) {
        return current && value;
      }

      @Override
      protected boolean isSatisfiable(Collection<Condition> conditions) {
        if (conditions.isEmpty()) {
          return this.initialValue;
        }
        return conditions.stream().allMatch(Condition::isSatisfiable);
      }

      @Override
      public boolean canTerminateEarly(boolean result) {
        // false AND anything == false
        return result == false;
      }
    },
    OR(false) {
      @Override
      protected boolean reduce(boolean current, boolean value) {
        return current || value;
      }

      @Override
      protected boolean isSatisfiable(Collection<Condition> conditions) {
        if (conditions.isEmpty()) {
          return this.initialValue;
        }
        return conditions.stream().anyMatch(Condition::isSatisfiable);
      }

      @Override
      public boolean canTerminateEarly(boolean result) {
        // true OR anything == true
        return result == true;
      }
    };


    protected final boolean initialValue;

    PredicateStrategy(boolean initialValue) {
      this.initialValue = initialValue;
    }

    protected abstract boolean reduce(boolean current, boolean value);

    protected abstract boolean isSatisfiable(Collection<Condition> conditions);

    public abstract boolean canTerminateEarly(boolean result);
  }
}
