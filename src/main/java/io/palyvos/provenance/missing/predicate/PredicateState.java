package io.palyvos.provenance.missing.predicate;

import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.apache.commons.lang3.Validate;

public enum PredicateState {
  LLRL("All answers are forgotten") {
    @Override
    protected boolean check(long predicateLeft, long predicateRight, long bufferLeft,
        long bufferRight) {
      return isLeftFrom(bufferLeft, predicateRight);
    }
  },
  LLRI("Some answers forgotten, rest in past buffer") {
    @Override
    protected boolean check(long predicateLeft, long predicateRight, long bufferLeft,
        long bufferRight) {
      return isLeftFrom(bufferLeft, predicateLeft)
          && isInside(bufferLeft, bufferRight, predicateRight);
    }
  },
  LIRI("All answers available in past buffer") {
    @Override
    protected boolean check(long predicateLeft, long predicateRight, long bufferLeft,
        long bufferRight) {
      return isInside(bufferLeft, bufferRight, predicateLeft)
          && isInside(bufferLeft, bufferRight, predicateRight);
    }
  },
  LIRR("Answers available in past buffer and in the future") {
    @Override
    protected boolean check(long predicateLeft, long predicateRight, long bufferLeft,
        long bufferRight) {
      return isInside(bufferLeft, bufferRight, predicateLeft)
          && isRightFrom(bufferRight, predicateRight);
    }
  },
  LRRR("All answers in the future") {
    @Override
    protected boolean check(long predicateLeft, long predicateRight, long bufferLeft,
        long bufferRight) {
      return isRightFrom(bufferRight, predicateLeft);
    }
  },
  LLRR("Some answers forgotten, others in buffer, and more in the future") {
    @Override
    protected boolean check(long predicateLeft, long predicateRight, long bufferLeft,
        long bufferRight) {
      return isLeftFrom(bufferLeft, predicateLeft)
          && isRightFrom(bufferRight, predicateRight);
    }
  };

  private static final String PREDICATE_STATE_FORMAT = "PREDICATE_STATE ::: %s ::: %s ::: %s ::: %s\n";
  private static final String EMPTY_BUFFER_FORMAT = "EMPTY_BUFFER\n";
  private static final String BUFFER_FORMAT = "SELECTED_BUFFER ::: %d tuples ::: %s\n";
  private final String explanation;

  private static boolean isInside(long intervalLeft, long intervalRight, long point) {
    return point >= intervalLeft && point <= intervalRight;
  }

  private static boolean isLeftFrom(long intervalLeft, long point) {
    return point < intervalLeft;
  }

  private static boolean isRightFrom(long intervalRight, long point) {
    return point > intervalRight;
  }

  PredicateState(String explanation) {
    this.explanation = explanation;
  }

  protected abstract boolean check(long predicateLeft, long predicateRight, long bufferLeft,
      long bufferRight);

  private void print(PrintWriter pw, Predicate p, long predicateLeft,
      long predicateRight) {
    pw.format(PREDICATE_STATE_FORMAT, this.name(), p.operator(), this.explanation,
        formattedInterval(predicateLeft, predicateRight));
  }

  public static void findAndPrint(PrintWriter pw, Predicate p,
      int tuplesInBuffer, long bufferLeft, long bufferRight) {
    if (tuplesInBuffer == 0) {
      return;
    }
    PredicateState found = null;
    final long predicateLeft = p.minTimeBoundary().orElse(Long.MIN_VALUE);
    final long predicateRight = p.maxTimeBoundary().orElse(Long.MAX_VALUE);
    Validate.isTrue(predicateLeft <= predicateRight, "predicateLeft > predicateRight");
    for (PredicateState state : PredicateState.values()) {
      if (state.check(predicateLeft, predicateRight, bufferLeft, bufferRight)) {
        Validate.validState(found == null,
            "Bug detected! Both predicate states %s, %s match current conditions for predicate %s",
            state, found, p);
        found = state;
      }
    }
    Validate.validState(found != null, "Bug! No state matched predicate %s", p);
    found.print(pw, p, predicateLeft, predicateRight);
  }

  public static void printEmptyBuffer(PrintWriter pw) {
    pw.format(EMPTY_BUFFER_FORMAT);
  }

  public static void checkAndPrintBuffer(PrintWriter pw, int tuplesInBuffer, long bufferLeft,
      long bufferRight) {
    if (tuplesInBuffer == 0) {
      printEmptyBuffer(pw);
      return;
    }
    Validate.isTrue(bufferLeft <= bufferRight, "bufferLeft > bufferRight");
    pw.format(BUFFER_FORMAT, tuplesInBuffer, formattedInterval(bufferLeft, bufferRight));
  }

  public static String formattedTime(long timestamp) {
    if (timestamp == TimestampCondition.NO_LEFT_BOUNDARY) {
      return "-inf";
    }
    if (timestamp == TimestampCondition.NO_RIGHT_BOUNDARY) {
      return "+inf";
    }
    return Instant
        .ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
  }

  public static String formattedInterval(long leftTimestamp, long rightTimestamp) {
    return String.format("[%s,%s] ::: [%d,%d]", formattedTime(leftTimestamp),
        formattedTime(rightTimestamp), leftTimestamp, rightTimestamp);
  }
}
