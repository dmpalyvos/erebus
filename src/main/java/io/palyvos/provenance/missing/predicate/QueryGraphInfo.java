package io.palyvos.provenance.missing.predicate;

import io.palyvos.provenance.missing.util.Path;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Serializable;
import java.util.ArrayDeque;
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
import java.util.function.LongBinaryOperator;
import java.util.function.LongUnaryOperator;
import java.util.stream.Collectors;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.yaml.snakeyaml.Yaml;

public class QueryGraphInfo implements Serializable {

  private static final boolean DEBUG = false;

  // Minimum event-time unit of the SPE
  public static final long EVENT_TIME_DELTA = 1;
  // Distance of the output timestamp of a window from the right boundary of that window
  // Must be >= 0 and <= WS
  public static final LongUnaryOperator WINDOW_OUTPUT_TIMESTAMP_DISTANCE_FROM_RIGHT_BOUNDARY = (WS) -> 1;

  @FunctionalInterface
  interface BoundaryFunction {

    OptionalLong apply(long thanValue, long windowSize, long windowAdvance,
        long intervalSize);

  }

  static final BoundaryFunction INTERVAL_START_TRANSFORM = (intervalStart, windowSize, windowAdvance, intervalSize) -> {
    if (windowSize == 0) {
      return OptionalLong.of(intervalStart);
    }
    final long epsilon = WINDOW_OUTPUT_TIMESTAMP_DISTANCE_FROM_RIGHT_BOUNDARY.applyAsLong(
        windowSize);
    if (intervalStart < windowSize - epsilon) {
      // Time starts at 0, so there is no earlier window that satisfies the property
      return OptionalLong.of(0);
    }
    final long windowIdx = (long) Math.ceil(
        (intervalStart - windowSize + epsilon) / (double) windowAdvance);
    final long rightBoundaryGreaterEqual = windowSize + windowAdvance * windowIdx;
    // Verify that the output timestamp of the window whose right boundary we computed
    // actually falls into the specified interval
    // i.e., it is not higher than the end of the interval
    final long previousIntervalEnd = intervalStart + intervalSize;
    final boolean rightBoundaryFallsInsideOldInterval =
        rightBoundaryGreaterEqual - epsilon < previousIntervalEnd;
    if (!rightBoundaryFallsInsideOldInterval) {
      return OptionalLong.empty();
    }
    // All tuples in the first window that falls into the interval can have contributed to that interval
    // so the new boundary becomes the start of that window
    return OptionalLong.of(Math.max(0, rightBoundaryGreaterEqual - windowSize));
  };

  static final BoundaryFunction INTERVAL_END_TRANSFORM = (intervalEnd, windowSize, windowAdvance, intervalSize) -> {
    if (windowSize == 0) {
      return OptionalLong.of(intervalEnd);
    }
    final long epsilon = WINDOW_OUTPUT_TIMESTAMP_DISTANCE_FROM_RIGHT_BOUNDARY.applyAsLong(
        windowSize);
    if (intervalEnd <= windowSize - epsilon) {
      // Time starts at 0, so there is no earlier window that satisfies the property
      return OptionalLong.empty();
    }
    final long windowIdx = (long) Math.floor(
        (intervalEnd - windowSize - EVENT_TIME_DELTA + epsilon) / (double) windowAdvance);
    final long rightBoundaryLessThan = windowSize + windowAdvance * windowIdx;
    final long intervalStart = intervalEnd - intervalSize;
    final boolean rightBoundaryFallsInsideOldInterval =
        rightBoundaryLessThan - epsilon >= intervalStart;
    if (!rightBoundaryFallsInsideOldInterval) {
      return OptionalLong.empty();
    }
    // Since we are transforming the end of the interval, we return the right boundary itself
    // i.e., the highest value a tuple in the last window can have
    return OptionalLong.of(rightBoundaryLessThan);
  };

  private static final String YAML_KEY_TRANSFORMS = "transforms";
  private static final String YAML_KEY_DOWNSTREAM = "downstream";
  private static final String YAML_KEY_RENAMINGS = "renamings";
  private static final String YAM_KEY_WINDOW_SIZE = "WS";
  private static final String YAM_KEY_WINDOW_ADVANCE = "WA";

  private final Map<String, Operator> operators;
  private final Map<String, TransformFunction> registeredTransforms = new HashMap<>();

  private QueryGraphInfo(Builder builder) {
    this.operators = Collections.unmodifiableMap(builder.operators);
  }

  public static QueryGraphInfo fromYaml(String path) throws FileNotFoundException {
    final Builder builder = new QueryGraphInfo.Builder();
    Yaml yaml = new Yaml();
    Map<String, Map<String, Object>> queryInfo = yaml.load(new FileReader(path));
    for (String operator : queryInfo.keySet()) {
      final Map<String, Object> operatorInfo = queryInfo.get(operator);
      builder.addOperatorInfo(operator);
      List<String> operatorDownstream = (List<String>) operatorInfo.getOrDefault(
          YAML_KEY_DOWNSTREAM, Collections.emptyList());
      operatorDownstream.forEach(d -> builder.addDownstream(operator, d));
      Map<String, String> operatorRenamings = (Map<String, String>) operatorInfo.getOrDefault(
          YAML_KEY_RENAMINGS, Collections.emptyMap());
      operatorRenamings.forEach(
          (var, downstreamVar) -> builder.addRenaming(operator, var, downstreamVar));
      Map<String, String> operatorTransforms = (Map<String, String>) operatorInfo.getOrDefault(
          YAML_KEY_TRANSFORMS, Collections.emptyMap());
      operatorTransforms.forEach(
          (var, transform) -> builder.addTransform(operator, var, transform));
      // TODO: YAML does not support longs, maybe another format more appropriate
      //  if problems arise
      int windowSize = (int) operatorInfo.getOrDefault(YAM_KEY_WINDOW_SIZE, 0);
      builder.setWindowSize(operator, windowSize);
      int windowAdvance = (int) operatorInfo.getOrDefault(YAM_KEY_WINDOW_ADVANCE, windowSize);
      builder.setWindowAdvance(operator, windowAdvance);
    }
    return builder.build();
  }

  public static class Builder {

    private final Map<String, Operator> operators = new HashMap<>();

    private final Map<String, OperatorInfo> operatorInfos = new HashMap<>();

    public Builder addRenaming(String operatorId, String variable, String downstreamVariable) {
      OperatorInfo op = addOperatorInfo(operatorId);
      Validate.isTrue(op.renamings.put(variable, downstreamVariable) == null,
          "Tried to override mapping for variable %s of operator %s!", variable, operatorId);
      return this;
    }

    public Builder addTransform(String operatorId, String variable, String transform) {
      OperatorInfo op = addOperatorInfo(operatorId);
      Validate.isTrue(op.transforms.put(variable, transform) == null,
          "Tried to override transform for variable %s of operator %s!", variable, operatorId);
      return this;
    }

    public Builder addDownstream(String operatorId, String downstreamOperatorId) {
      OperatorInfo op = addOperatorInfo(operatorId);
      addOperatorInfo(downstreamOperatorId);
      op.downstreamOperators.add(downstreamOperatorId);
      return this;
    }

    public Builder setWindowSize(String operatorId, long windowSize) {
      OperatorInfo op = addOperatorInfo(operatorId);
      op.windowSize = windowSize;
      return this;
    }

    public Builder setWindowAdvance(String operatorId, long windowAdvance) {
      OperatorInfo op = addOperatorInfo(operatorId);
      op.windowAdvance = windowAdvance;
      return this;
    }

    private OperatorInfo addOperatorInfo(String operatorId) {
      final OperatorInfo op = operatorInfos.computeIfAbsent(operatorId, k -> new OperatorInfo(k));
      operatorInfos.put(op.id, op);
      return op;
    }

    private void createOperators() {
      operatorInfos.forEach(
          (operatorId, opInfo) -> operators.put(operatorId, new Operator(opInfo)));
      for (String operatorId : operators.keySet()) {
        final Operator operator = operators.get(operatorId);
        final Collection<String> downstream = operatorInfos.get(operatorId).downstreamOperators;
        for (String downstreamOperatorId : downstream) {
          final Operator downstreamOperator = operators.get(downstreamOperatorId);
          operator.downstream().add(downstreamOperator);
          downstreamOperator.upstream().add(operator);
        }
      }
    }

    /**
     * Populate identity renamings in case they are missing (starting from the sources).
     */
    private void populateRenamings() {
      Queue<Operator> q = new ArrayDeque<>(sources());
      Set<Operator> visited = new HashSet<>();
      while (!q.isEmpty()) {
        Operator current = q.remove();
        for (Operator downstream : current.downstream()) {
          if (downstream.isSink()) {
            continue;
          }
          current.renamings()
              .forEach((variable, renamedVariable) ->
                  downstream.renamings().putIfAbsent(renamedVariable, renamedVariable));
          if (!visited.contains(downstream)) {
            visited.add(downstream);
            q.add(downstream);
          }
        }
      }
    }

    private Collection<Operator> sources() {
      return operators.values().stream().filter(op -> op.isSource())
          .collect(Collectors.toSet());
    }

    public QueryGraphInfo build() {
      Validate.notEmpty(operatorInfos, "No operators initialized");
      createOperators();
      populateRenamings();
      return new QueryGraphInfo(this);

    }
  }

  public Map<String, Set<VariableRenaming>> sinkToOperatorVariables(String operatorId) {
    return sinkToOperatorVariables(operatorId, onlySink().id());
  }

  public Map<String, Set<VariableRenaming>> sinkToOperatorVariables(
      String operatorId, String sinkId) {
    final Operator operator = operators.get(operatorId);
    final Operator sink = operators.get(sinkId);
    Validate.validState(operator != null,
        "Operator %s not found in query info. Known operators: %s", operatorId, operators.keySet());
    Validate.validState(sink != null,
        "Sink %s not found in query info. Known operators: %s", sink, operators.keySet());
    // Get reverse renamings, from operator to the sink variables
    Map<String, Collection<VariableRenaming>> renamings = operatorToSinkVariables(operator, sink);
    Map<String, Set<VariableRenaming>> reversed = new HashMap<>();
    // Reverse the operator-sink renamings to get sink-operator renamings
    for (Map.Entry<String, Collection<VariableRenaming>> entry : renamings.entrySet()) {
      for (VariableRenaming sinkRenaming : entry.getValue()) {
        reversed.computeIfAbsent(sinkRenaming.name(), k -> new HashSet<>())
            .add(sinkRenaming.withReverseTransforms(entry.getKey()));
      }
    }
    reversed.values().stream().flatMap(set -> set.stream())
        .forEach(r -> r.computeTransform(registeredTransforms));
    return reversed;
  }

  private Map<String, Collection<VariableRenaming>> operatorToSinkVariables(Operator operator,
      Operator sink) {
    Map<String, Collection<VariableRenaming>> result = new HashMap<>();
    final Map<String, String> operatorRenamings = operator.renamings();
    Validate.notNull(operatorRenamings, "No renaming info available for operator %s", operator);
    for (String variable : operatorRenamings.keySet()) {
      result.put(variable, operatorToSinkVariables(variable, operator, sink));
    }
    return result;
  }


  public Set<VariableRenaming> operatorToSinkVariables(String operatorVariable, String operatorId,
      String sinkId) {
    return operatorToSinkVariables(operatorVariable, operators.get(operatorId),
        operators.get(sinkId));
  }

  public Set<VariableRenaming> operatorToSinkVariables(String operatorVariable, String operatorId) {
    return operatorToSinkVariables(operatorVariable, operatorId, onlySink().id());
  }

  private Operator onlySink() {
    List<Operator> sinks = operators.values().stream().filter(op -> op.isSink())
        .collect(Collectors.toList());
    Validate.isTrue(sinks.size() > 0, "No sink in query!");
    Validate.isTrue(sinks.size() == 1,
        "More than one sinks in the query. You need to specify target: %s", sinks);
    return sinks.get(0);
  }

  private Set<VariableRenaming> operatorToSinkVariables(String operatorVariable, Operator operator,
      Operator sink) {
    final Set<VariableRenaming> variableRenamings = operatorToSinkVariables(
        new HashSet<>(Arrays.asList(VariableRenaming.newInstance(operatorVariable))), operator,
        sink);
    variableRenamings.forEach(r -> r.computeTransform(registeredTransforms));
    return variableRenamings;
  }

  /**
   * Main renaming algorithm. Start from operator variables and move downstream to the sink
   */
  private Set<VariableRenaming> operatorToSinkVariables(Set<VariableRenaming> variables,
      Operator operator,
      Operator sink) {
    Set<VariableRenaming> downstreamVariables = variables.stream()
        .map(renaming -> renaming.transformed(operator.renamed(renaming.name()),
            operator.transform(renaming.name()), operator.id()))
        .collect(Collectors.toSet());
    if (operator.equals(sink)) {
      return downstreamVariables;
    } else if (operator.isSink()) {
      // Unrelated sink, ignore
      return Collections.emptySet();
    }
    return operator.downstream().stream()
        .map(downStreamOperator -> operatorToSinkVariables(downstreamVariables,
            downStreamOperator, sink))
        .flatMap(Collection::stream)
        .collect(Collectors.toSet());
  }

  public OptionalLong legacyTransformIntervalStartFromSink(String sinkId,
      String targetOperatorId,
      long intervalStart, long intervalEnd) {
    final Map<Path, OptionalLong> pathBoundaries = transformIntervalStartFromSink(sinkId,
        targetOperatorId, intervalStart,
        intervalEnd);
    return pathBoundaries.values().stream().filter(OptionalLong::isPresent)
        .mapToLong(OptionalLong::getAsLong).reduce(Long::min);
  }

  public Map<Path, OptionalLong> transformIntervalStartFromSink(String sinkId,
      String targetOperatorId,
      long intervalStart, long intervalEnd) {
    Validate.isTrue(intervalEnd > intervalStart, "intervalEnd <= intervalStart!");
    return transformBoundary(operators.get(sinkId),
        operators.get(targetOperatorId),
        intervalStart, intervalEnd - intervalStart, INTERVAL_START_TRANSFORM, (b, s) -> b + s);
  }

  public OptionalLong legacyTransformIntervalEndFromSink(String sinkId,
      String targetOperatorId,
      long intervalStart, long intervalEnd) {
    final Map<Path, OptionalLong> pathBoundaries = transformIntervalEndFromSink(
        sinkId, targetOperatorId, intervalStart, intervalEnd);
    return pathBoundaries.values().stream().filter(OptionalLong::isPresent)
        .mapToLong(OptionalLong::getAsLong).reduce(Long::max);
  }

  public Map<Path, OptionalLong> transformIntervalEndFromSink(String sinkId,
      String targetOperatorId,
      long intervalStart, long intervalEnd) {
    Validate.isTrue(intervalEnd > intervalStart, "intervalEnd <= intervalStart!");
    final Map<Path, OptionalLong> boundaries = transformBoundary(operators.get(sinkId),
        operators.get(targetOperatorId),
        intervalEnd, intervalEnd - intervalStart,
        INTERVAL_END_TRANSFORM, (b, s) -> b - s);
    return boundaries;
  }


  Set<Path> upstreamPaths(String current, String target) {
    Validate.notEmpty(current, "current");
    Validate.notEmpty(target, "target");
    return upstreamPaths(operators.get(current), operators.get(target), Path.empty());
  }

  Set<Path> upstreamPaths(Operator current, Operator target, Path path) {
    Validate.notNull(current, "current");
    Validate.notNull(target, "target");
    Set<Path> upstreamPaths = new HashSet<>();
    Path currentPath = path.extended(current.id());
    if ((!current.equals(target)) && current.isSource()) {
      return Collections.emptySet();
    }
    if (current.equals(target)) {
      return Collections.singleton(currentPath);
    }
    for (Operator upstream : current.upstream()) {
      upstreamPaths.addAll(upstreamPaths(upstream, target, currentPath));
    }
    return upstreamPaths;
  }

  private Map<Path, OptionalLong> transformBoundary(Operator current, Operator target,
      long boundary,
      long intervalSize,
      BoundaryFunction boundaryFunction,
      LongBinaryOperator boundaryShiftOperator) {
    Set<Path> paths = upstreamPaths(current, target, Path.empty());
    Map<Path, OptionalLong> boundaries = new HashMap<>();
    Validate.notEmpty(paths, "No path between %s and %s", current, target);
    for (Path path : paths) {
      final OptionalLong previous = boundaries.put(path,
          transformBoundaryForPath(current, target, boundary, intervalSize, boundaryFunction,
              boundaryShiftOperator, path, 0));
      Validate.isTrue(previous == null,
          "Duplicate transformed boundary for path %s. This is a bug!", path);
    }
    return boundaries;
  }

  private OptionalLong transformBoundaryForPath(Operator current, Operator target,
      long boundary,
      long intervalSize,
      BoundaryFunction boundaryFunction,
      LongBinaryOperator boundaryShiftOperator, Path path, int pathIndex) {
    Validate.notNull(current, "current");
    Validate.notNull(target, "target");
    Validate.isTrue(path.at(pathIndex).equals(current.id()),
        "Operator '%s' not next in path index %d: '%s'! This is a bug.",
        current.id(), pathIndex, path);
    while (intervalSize > 0) {
      final OptionalLong currentBoundary = boundaryFunction.apply(boundary,
          current.windowSize(), current.windowAdvance(), intervalSize);
      if (DEBUG) {
        System.out.format(
            "> %s [WS=%d,WA=%d] ::: target=%s::: interval=%d ::: boundary=%d ::: currentBoundary=%s\n",
            current.id(), current.windowSize(), current.windowAdvance(), target.id(),
            intervalSize, boundary, currentBoundary);
      }
      if (current.equals(target)) {
        return currentBoundary;
      }
      final OptionalLong upstreamBoundary = upstreamBoundary(current, target,
          boundaryFunction, currentBoundary, intervalSize, boundaryShiftOperator, path,
          pathIndex + 1);
      if (upstreamBoundary.isPresent() || !current.isStateful()) {
        // Return ia the boundary is found or if the operator is stateless
        // it is not possible to retry for stateless operators, WS = 0, we cannot shift boundary
        return upstreamBoundary;
      }
      // Each time boundary computation fails, we shift the boundary
      // (higher for left boundary and lower for right boundary)
      // and reduce the interval length accordingly
      boundary = boundaryShiftOperator.applyAsLong(boundary, current.windowAdvance());
      intervalSize -= current.windowAdvance();
    }
    if (DEBUG) {
      System.out.println(current.id() + " failed to compute new interval");
    }
    return OptionalLong.empty();
  }

  private OptionalLong upstreamBoundary(Operator current, Operator target,
      BoundaryFunction boundaryFunction,
      OptionalLong currentBoundary, long intervalSize,
      LongBinaryOperator boundaryShiftOperator, Path path, int pathIndex) {
    if (!currentBoundary.isPresent()) {
      return currentBoundary;
    }
    // Interval size remains identical if stateless, otherwise it becomes current window size
    final long newIntervalSize = current.isStateful() ? current.windowSize() : intervalSize;
    for (Operator upstream : current.upstream()) {
      if (path.at(pathIndex).equals(upstream.id())) {
        return transformBoundaryForPath(upstream, target,
            currentBoundary.getAsLong(),
            newIntervalSize, boundaryFunction, boundaryShiftOperator, path, pathIndex);
      }
    }
    throw new IllegalStateException(
        String.format("No upstream operator of %s was in path  %s. This is a bug!", current, path));
  }

  public long maxDelay() {
    return operators.values().stream().filter(o -> o.isSink())
        .map(sink -> maxDelayBfsToSource(sink))
        .mapToLong(Long::longValue).max().orElseThrow(() -> new IllegalStateException(
            String.format("Failed to compute maxDelay for query with operators %s", operators)));
  }

  private long maxDelayBfsToSource(Operator operator) {
    return operator.windowSize() + operator.upstream().stream().map(this::maxDelayBfsToSource)
        .mapToLong(Long::longValue).max().orElse(0);
  }

  public QueryGraphInfo registerTransform(String key, TransformFunction function) {
    Validate.isTrue(registeredTransforms.put(key, function) == null,
        "TransformFunction with key '%s' already registered!", key);
    return this;
  }


  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("operators", operators)
        .append("registeredTransforms", registeredTransforms.keySet())
        .toString();
  }
}
