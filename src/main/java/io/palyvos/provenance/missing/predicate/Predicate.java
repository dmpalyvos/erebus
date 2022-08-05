package io.palyvos.provenance.missing.predicate;

import io.palyvos.provenance.missing.util.Path;
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

  private final Path path;

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
    this(conditions, strategy, operator, uid, enabled, Path.empty());
  }

  private Predicate(Collection<Condition> conditions,
      PredicateStrategy strategy, String operator, UUID uid, boolean enabled, Path path) {
    Validate.notNull(conditions, "conditions");
    Validate.notNull(strategy, "strategy");
    Validate.notEmpty(operator, "operator");
    Validate.notNull(uid, "uid");
    Validate.notNull(path, "path");
    this.conditions.addAll(conditions);
    for (Condition condition : conditions) {
      storeVariables(condition.variables());
    }
    this.strategy = strategy;
    this.operator = operator;
    this.uid = uid;
    this.enabled = enabled;
    this.path = path;
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

  public Predicate transformedNameOnly(String operator) {
    return new Predicate(conditions, strategy, operator, uid, enabled);
  }

  public Predicate transformed(String operator, String sink, QueryGraphInfo queryGraphInfo) {
    final Map<String, Set<VariableRenaming>> renamings = queryGraphInfo.sinkToOperatorVariables(
        operator, sink);
    final Map<Path, Map<String, List<VariableRenaming>>> pathRenamings = groupVariableRenamingsPerPath(
        renamings);
    final Map<Path, Map<TimestampCondition, Condition>> pathTimeTransforms =
        precomputeTimeTransforms(operator, sink, queryGraphInfo);
    final List<Condition> pathPredicates = new ArrayList<>();
    for (Path path : uniquePaths(pathRenamings, pathTimeTransforms)) {
      Map<String, List<VariableRenaming>> pathRenaming = pathRenamings.get(path);
      Map<TimestampCondition, Condition> pathTimeTransform = pathTimeTransforms.get(path);
      final Predicate pathPredicate = pathTransformed(operator,
          path, pathRenaming, pathTimeTransform);
      pathPredicates.add(pathPredicate);
    }
    Validate.isTrue(pathPredicates.size() > 0,
        "Transform failed. No path between %s and %s! Have you declared at least one variable/timestamp rule in %s?",
        sink, operator, QueryGraphInfo.class.getSimpleName());
    // Do not nest if only one path exists
    final Predicate transformed =
        pathPredicates.size() == 1
            ? (Predicate) pathPredicates.get(0)
            : new Predicate(pathPredicates, PredicateStrategy.OR, operator, uid, enabled,
                Path.of(Arrays.asList(sink, "...", operator)));
    LOG.info("Transformed Predicate\n{}", transformed);
    return transformed;
  }

  private Set<Path> uniquePaths(Map<Path, Map<String, List<VariableRenaming>>> pathRenamings,
      Map<Path, Map<TimestampCondition, Condition>> pathTimeTransforms) {
    // Paths in time and renamings should be equal except if the predicate
    // does not have any timestamp / attribute condition
    // Take the union of the paths to handle this edge case
    final Set<Path> timeAndRenamingPaths = new HashSet<>();
    timeAndRenamingPaths.addAll(pathTimeTransforms.keySet());
    timeAndRenamingPaths.addAll(pathRenamings.keySet());
    return timeAndRenamingPaths;
  }

  private Map<Path, Map<TimestampCondition, Condition>> precomputeTimeTransforms(
      String operator, String sink, QueryGraphInfo queryGraphInfo) {
    BiFunction<Long, Long, Map<Path, OptionalLong>> leftBoundaryTransform =
        (leftBoundary, rightBoundary) -> queryGraphInfo.transformIntervalStartFromSink(
            sink, operator, leftBoundary, rightBoundary);
    BiFunction<Long, Long, Map<Path, OptionalLong>> rightBoundaryTransform =
        (leftBoundary, rightBoundary) -> queryGraphInfo.transformIntervalEndFromSink(
            sink, operator, leftBoundary, rightBoundary);
    Map<Path, Map<TimestampCondition, Condition>> pathTransforms = new HashMap<>();
    for (Condition condition : baseConditions()) {
      if (!(condition instanceof TimestampCondition)) {
        continue;
      }
      final TimestampCondition timestampCondition = (TimestampCondition) condition;
      final Map<Path, Condition> conditionPathTransforms =
          (timestampCondition).timeShifted(leftBoundaryTransform, rightBoundaryTransform);
      for (Path path : conditionPathTransforms.keySet()) {
        final Condition previous = pathTransforms.computeIfAbsent(path, k -> new HashMap<>()).put(
            timestampCondition, conditionPathTransforms.get(path));
        Validate.isTrue(previous == null, "Multiple path mappings for same condition!");
      }
    }
    return pathTransforms;
  }

  private Map<Path, Map<String, List<VariableRenaming>>> groupVariableRenamingsPerPath(
      Map<String, ? extends Collection<VariableRenaming>> sinkToOperatorVariables) {
    Map<Path, Map<String, List<VariableRenaming>>> grouping = new HashMap<>();
    for (String sinkVariable : sinkToOperatorVariables.keySet()) {
      for (VariableRenaming renaming : sinkToOperatorVariables.get(sinkVariable)) {
        grouping.computeIfAbsent(renaming.operators(), k -> new HashMap<>())
            .computeIfAbsent(sinkVariable, k -> new ArrayList<>()).add(renaming);
      }
    }
    return grouping;
  }

  private Predicate pathTransformed(String operator,
      Path path, Map<String, List<VariableRenaming>> pathRenaming,
      Map<TimestampCondition, Condition> pathTimeTransform) {
    List<Condition> pathConditions = new ArrayList<>();
    for (Condition condition : conditions) {
      if (condition instanceof Predicate) {
        pathConditions.add(
            ((Predicate) condition).pathTransformed(operator, path, pathRenaming,
                pathTimeTransform));
      } else if (condition instanceof TimestampCondition) {
        pathConditions.add(pathTimeTransform.get(condition));
      } else if (condition instanceof VariableCondition) {
        pathConditions.add(((VariableCondition) condition).renamed(pathRenaming));
      } else if (condition instanceof NonTransformableCondition) {
        pathConditions.add(condition);
      } else {
        throw new IllegalStateException(
            String.format("Transform failed, unknown Condition type: %s", condition));
      }
    }
    return new Predicate(pathConditions, strategy, operator, uid, enabled, path);
  }

  public Predicate manualTimeShifted(
      BiFunction<Long, Long, OptionalLong> leftBoundaryTransform,
      BiFunction<Long, Long, OptionalLong> rightBoundaryTransform) {
    List<Condition> shifted = new ArrayList<>();
    for (Condition condition : conditions) {
      if (condition instanceof Predicate) {
        shifted.add(
            ((Predicate) condition).manualTimeShifted(leftBoundaryTransform,
                rightBoundaryTransform));
      } else if (condition instanceof TimestampCondition) {
        shifted.add(
            ((TimestampCondition) condition).manualTimeShifted(leftBoundaryTransform,
                rightBoundaryTransform));
      }
    }
    return new Predicate(shifted, strategy, operator, uid, enabled);
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

  public Path path() {
    return path;
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
        .append("path", path)
        .toString();
  }

  public UUID uid() {
    return this.uid;
  }

  public String description() {
    final String delimiter = " " + strategy.name() + " ";
    return path + ":" + "(" + conditions.stream().map(Condition::description)
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
