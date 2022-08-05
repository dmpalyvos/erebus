package io.palyvos.provenance.missing.predicate;

import io.palyvos.provenance.missing.util.Path;
import java.text.ParseException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.BiFunction;
import org.apache.commons.lang3.Validate;

public class TimestampCondition implements Condition {

  public static final long NO_RIGHT_BOUNDARY = Long.MAX_VALUE;
  public static final long NO_LEFT_BOUNDARY = Long.MIN_VALUE;
  private final long leftBoundary;
  private final long rightBoundary;
  private final String stringRepresentation;

  /**
   * Initialize {@link TimestampCondition} between the (inclusive) left boundary and the (exclusive)
   * right boundary.
   */
  public static TimestampCondition between(long left, long right) {
    return new TimestampCondition(left, right);
  }

  /**
   * Initialize {@link TimestampCondition} between the (inclusive) left boundary and the (exclusive)
   * right boundary. See {@link Instant#parse(CharSequence)}
   */
  public static TimestampCondition between(String left, String right) throws ParseException {
    return new TimestampCondition(1000 * Instant.parse(left).getEpochSecond(),
        1000 * Instant.parse(right).getEpochSecond());
  }

  /**
   * Initialize {@link TimestampCondition} between the (inclusive) left boundary and the (inclusive)
   * right boundary.
   */
  public static TimestampCondition betweenRightInclusive(long left, long right) {
    return new TimestampCondition(left, right + 1);
  }

  /**
   * Initialize {@link TimestampCondition} between the (inclusive) boundary and
   * {@link Long#MAX_VALUE}.
   */
  public static TimestampCondition greaterEqual(long than) {
    return new TimestampCondition(than, NO_RIGHT_BOUNDARY);
  }

  /**
   * Initialize {@link TimestampCondition} between the (exclusive) boundary and
   * {@link Long#MAX_VALUE}.
   */
  public static TimestampCondition greater(long than) {
    return new TimestampCondition(than + 1, NO_RIGHT_BOUNDARY);
  }

  /**
   * Initialize {@link TimestampCondition} between {@link Long#MIN_VALUE} and the (exclusive)
   * boundary.
   */
  public static TimestampCondition less(long than) {
    return new TimestampCondition(NO_LEFT_BOUNDARY, than);
  }

  /**
   * Initialize {@link TimestampCondition} between {@link Long#MIN_VALUE} and the (inclusive)
   * boundary.
   */
  public static TimestampCondition lessEqual(long than) {
    return new TimestampCondition(NO_LEFT_BOUNDARY, than + 1);
  }

  /**
   * Construct.
   *
   * @param leftBoundary  Inclusive left boundary.
   * @param rightBoundary Exclusive right boundary.
   */
  TimestampCondition(long leftBoundary, long rightBoundary) {
    Validate.isTrue(leftBoundary < rightBoundary, "leftBoundary >= rightBoundary!");
    this.leftBoundary = leftBoundary;
    this.rightBoundary = rightBoundary;
    this.stringRepresentation =
        "(" +
            (leftBoundary > NO_LEFT_BOUNDARY ? leftBoundary : "-inf") +
            " <= ts < " +
            (rightBoundary < NO_RIGHT_BOUNDARY ? rightBoundary : "+inf") +
            ")";
  }

  @Override
  public Collection<Variable> variables() {
    return Collections.emptyList();
  }

  @Override
  public boolean evaluate(long timestamp) {
    return leftBoundary <= timestamp && timestamp < rightBoundary;
  }

  @Override
  public boolean isLoaded() {
    return true;
  }

  public Condition manualTimeShifted(
      BiFunction<Long, Long, OptionalLong> leftBoundaryTransform,
      BiFunction<Long, Long, OptionalLong> rightBoundaryTransform) {
    final OptionalLong newLeftBoundary =
        leftBoundary != NO_LEFT_BOUNDARY
            ? leftBoundaryTransform.apply(leftBoundary, rightBoundary)
            : OptionalLong.of(NO_LEFT_BOUNDARY);
    final OptionalLong newRightBoundary =
        rightBoundary != NO_RIGHT_BOUNDARY
            ? rightBoundaryTransform.apply(leftBoundary, rightBoundary)
            : OptionalLong.of(NO_RIGHT_BOUNDARY);
    if (!newLeftBoundary.isPresent() || !newRightBoundary.isPresent()) {
      return new UnsatisfiableCondition(this,
          String.format("No valid time transform exists: (%s <= t < %s)",
              newLeftBoundary, newRightBoundary));
    }
    return new TimestampCondition(newLeftBoundary.getAsLong(), newRightBoundary.getAsLong());
  }

  public Map<Path, Condition> timeShifted(
      BiFunction<Long, Long, Map<Path, OptionalLong>> leftBoundaryTransform,
      BiFunction<Long, Long, Map<Path, OptionalLong>> rightBoundaryTransform) {
    Map<Path, Condition> pathConditions = new HashMap<>();
    final Map<Path, OptionalLong> leftBoundaryTransforms = leftBoundaryTransform.apply(leftBoundary,
        rightBoundary);
    final Map<Path, OptionalLong> rightBoundaryTransforms = rightBoundaryTransform.apply(
        leftBoundary,
        rightBoundary);
    for (Path path : leftBoundaryTransforms.keySet()) {
      OptionalLong pathLeftBoundary =
          leftBoundary != NO_LEFT_BOUNDARY ? leftBoundaryTransforms.get(path)
              : OptionalLong.of(NO_LEFT_BOUNDARY);
      OptionalLong pathRightBoundary =
          rightBoundary != NO_RIGHT_BOUNDARY ? rightBoundaryTransforms.get(path)
              : OptionalLong.of(NO_RIGHT_BOUNDARY);
      Condition pathCondition;
      if (pathLeftBoundary == null || !pathLeftBoundary.isPresent() || pathRightBoundary == null
          || !pathRightBoundary.isPresent()) {
        pathCondition = new UnsatisfiableCondition(this,
            String.format("No valid time transform exists for path %s: (%s <= t < %s)",
                path, pathLeftBoundary, pathRightBoundary));

      } else {
        pathCondition = new TimestampCondition(pathLeftBoundary.getAsLong(), pathRightBoundary.getAsLong());
      }
      pathConditions.put(path, pathCondition);
    }
    return pathConditions;
  }


  @Override
  public OptionalLong minTimeBoundary() {
    return OptionalLong.of(leftBoundary);
  }

  @Override
  public OptionalLong maxTimeBoundary() {
    return OptionalLong.of(rightBoundary);
  }

  @Override
  public String description() {
    return
        (leftBoundary > NO_LEFT_BOUNDARY ? Instant.ofEpochMilli(leftBoundary) : "-inf") +
            " <= ts < " +
            (rightBoundary < NO_RIGHT_BOUNDARY ? Instant.ofEpochMilli(rightBoundary) : "+inf");
  }

  @Override
  public String toString() {
    return stringRepresentation;
  }
}
