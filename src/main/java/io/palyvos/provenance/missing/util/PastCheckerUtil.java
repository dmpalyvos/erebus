package io.palyvos.provenance.missing.util;

import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import io.palyvos.provenance.missing.PickedProvenance;
import io.palyvos.provenance.missing.predicate.Condition;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.predicate.PredicateState;
import io.palyvos.provenance.util.AvgStat;
import io.palyvos.provenance.util.CountStat;
import io.palyvos.provenance.util.ExperimentSettings;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.Validate;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PastCheckerUtil<T> {

  private static final Logger LOG = LoggerFactory.getLogger(PastCheckerUtil.class);
  private static final int BUFFER_SIZE_METRIC_PERIOD_INVOCATIONS = 50;
  private final long bucketSizeMillisec;
  private final NavigableMap<Long, List<ProvenanceTupleContainer<?>>> sharedBuffer = new TreeMap<>();
  private final Set<ProvenanceTupleContainer<?>> overlap = new HashSet<>();
  private final long bufferDelayMillis;
  private final KafkaDataUtil kafkaDataUtil;
  private final Function<T, ProvenanceTupleContainer<?>> valueFunction;
  private UUID lastEvaluatedPredicateUID;
  private final ExecutorService executorService = Executors.newSingleThreadExecutor();
  private long lastWatermark = -1;
  private final AvgStat bufferSizeStat;
  private final CountStat outputRateStat;
  private int metricReportCounter;
  private final String uniqueOperatorName;

  public PastCheckerUtil(String operatorName, KafkaDataUtil kafkaDataUtil,
      Function<T, ProvenanceTupleContainer<?>> valueFunction,
      ExperimentSettings settings) {
    Validate.notBlank(operatorName, "Blank operatorName");
    Validate.isTrue(settings.bufferDelayMillis() >= -1, "bufferDelay < -1");
    Validate.notNull(kafkaDataUtil, "kafkaDataUtil");
    this.bufferDelayMillis = settings.bufferDelayMillis();
    this.kafkaDataUtil = kafkaDataUtil;
    this.valueFunction = valueFunction;
    final List<String> statisticFiles = settings.pastCheckerUtilStatistics(operatorName);
    this.bufferSizeStat = new AvgStat(statisticFiles.get(0), settings.autoFlush());
    this.outputRateStat = new CountStat(statisticFiles.get(1), settings.autoFlush());
    this.bucketSizeMillisec = settings.pastBufferBucketSize();
    this.uniqueOperatorName = PickedProvenance.uniqueOperatorName(operatorName);
  }

  public void add(StreamRecord<T> record, Predicate predicate) {
    final long timestamp = record.getTimestamp();
    final ProvenanceTupleContainer<?> tuple = valueFunction.apply(record.getValue());
    tuple.setTimestamp(timestamp); // Make sure ts is consistent with record
    sharedBuffer.computeIfAbsent(bucketIndex(timestamp), (k) -> new ArrayList<>()).add(tuple);
    if (!hasEvaluated(predicate.uid())) {
      // Predicate will be evaluated on next watermark but this tuple already evaluated online
      // so do not re-evaluate later
      overlap.add(tuple);
    }
  }

  private long bucketIndex(long timestamp) {
    return timestamp / bucketSizeMillisec;
  }

  private long firstTimestamp(long bucketIndex) {
    return bucketIndex * bucketSizeMillisec;
  }


  public void onWatermark(long watermark, Predicate predicate) {
    removeOldRecords(sharedBuffer, watermark);
    // Asynchronously evaluate the predicate and update the watermark
    if (!hasEvaluated(predicate.uid())) {
      final Predicate localPredicate = predicate.deepCopy(); // For thread safety during evaluation
      final TreeMap<Long, List<ProvenanceTupleContainer<?>>> currentBuffer = deepCopy(sharedBuffer,
          localPredicate.minTimeBoundary(), localPredicate.maxTimeBoundary());
      final Set<ProvenanceTupleContainer<?>> currentOverlap = new HashSet<>(overlap);
      overlap.clear();
      lastEvaluatedPredicateUID = localPredicate.uid();
      executorService.submit(
          () -> doCheckPredicatePast(watermark, localPredicate, currentBuffer, currentOverlap));
    }
    if (predicate.isEnabled() && predicate.hasExpired(watermark)) {
      LOG.info("{}: Disabling predicate {}", predicate.operator(), predicate.uid());
      predicate.disable();
      executorService.submit(() -> kafkaDataUtil.sendMessage(predicate.operator(),
          String.format("EXPIRED_PREDICATE ::: %s ::: eventTime=%d ::: processingTime=%s ::: %s\n",
              predicate.operator(), watermark,
              PredicateState.formattedTime(System.currentTimeMillis()), predicate.uid()),
          watermark));
    }
    processWatermark(watermark);
  }

  private TreeMap<Long, List<ProvenanceTupleContainer<?>>> deepCopy(
      NavigableMap<Long, List<ProvenanceTupleContainer<?>>> buffer,
      OptionalLong minTimestamp, OptionalLong maxTimestamp) {
    final TreeMap<Long, List<ProvenanceTupleContainer<?>>> copy = new TreeMap<>();
    SortedMap<Long, List<ProvenanceTupleContainer<?>>> timeLimitedBuffer = buffer;
    if (minTimestamp.isPresent()) {
      timeLimitedBuffer = timeLimitedBuffer.tailMap(bucketIndex(minTimestamp.getAsLong()));
    }
    if (maxTimestamp.isPresent()) {
      // We want inclusive headMap(), so use next bucketIndex
      timeLimitedBuffer = timeLimitedBuffer.headMap(bucketIndex(maxTimestamp.getAsLong()) + 1);
    }
    timeLimitedBuffer.forEach((ts, values) -> copy.put(ts, new ArrayList<>(values)));
    return copy;
  }

  private <V> void removeOldRecords(NavigableMap<Long, List<V>> buffer, long watermark) {
    if (!buffer.isEmpty()) {
      // Buffer should be a window of bufferDelayMillis
      // It is possible that right boundary of buffer is larger than watermark
      // in which case, use that and not the watermark for the calculation
      final long bufferRightBoundary = Math.max(rightBoundaryTimestamp(buffer), watermark);
      buffer.headMap(bucketIndex(bufferRightBoundary - bufferDelayMillis)).clear();
    }
    if (++metricReportCounter % BUFFER_SIZE_METRIC_PERIOD_INVOCATIONS == 0) {
      metricReportCounter = 0;
      bufferSizeStat.add(totalNumberElements(buffer));
      // Also force output statistic to emit tuples in case no evaluation is happening
      executorService.submit(() -> outputRateStat.increase(0));
    }
  }

  private void processWatermark(long watermark) {
    if (watermark > 0) { // Prevent possible number underflow
      // If the buffer is empty, the output watermark = watermark-bufferDelay (or 0 if the diff is negative)
      // If the buffer is not empty, the output watermark is the left boundary of the buffer
      // BUT at most equal to the input watermark, never higher (otherwise we might break the wm condition)
      // In the end 0 <= outputWatermark <= inputWatermark
      // and outputWatermark <= bufferLeftBoundary (if exists)
      final long outputWatermark = sharedBuffer.isEmpty()
          ? Math.max(0, watermark - bufferDelayMillis)
          : Math.min(watermark, leftBoundaryTimestamp(sharedBuffer));
      if (outputWatermark > lastWatermark) { // Avoid unnecessary transmissions
        lastWatermark = outputWatermark;
        executorService.submit(
            () -> kafkaDataUtil.sendWatermark(uniqueOperatorName, outputWatermark));
      }
    }
  }

  // WARNING: Needs to be thread safe, not using any global mutable state (except kafkaDataUtil)
  private void doCheckPredicatePast(long watermark, Predicate predicate,
      NavigableMap<Long, List<ProvenanceTupleContainer<?>>> currentBuffer,
      Set<ProvenanceTupleContainer<?>> currentOverlap) {
    try {
      if (checkPredicateBoundaries(predicate, watermark, currentBuffer)) {
        evaluatePredicateOnPastBuffer(predicate, watermark, currentBuffer, currentOverlap);
      }
    } catch (Exception e) {
      LOG.error("Past check failed for predicate {}: {}", predicate, e);
    }
  }

  // WARNING: Needs to be thread safe, not using any global mutable state (except kafkaDataUtil)
  private boolean checkPredicateBoundaries(Predicate predicate, long watermark,
      NavigableMap<Long, List<ProvenanceTupleContainer<?>>> currentBuffer) {
    final StringWriter metaAnswers = new StringWriter();
    final PrintWriter pw = new PrintWriter(metaAnswers);
    pw.format("NEW_PREDICATE ::: eventTime=%s ::: processingTime=%s ::: %s\n",
        watermark, PredicateState.formattedTime(System.currentTimeMillis()), predicate);
    final long bufferLeft =
        currentBuffer.isEmpty() ? -1 : leftBoundaryTimestamp(currentBuffer);
    final long bufferRight =
        currentBuffer.isEmpty() ? -1 : rightBoundaryTimestamp(currentBuffer);
    final int bufferSize = totalNumberElements(currentBuffer);
    PredicateState.checkAndPrintBuffer(pw, bufferSize, bufferLeft, bufferRight);
    final boolean predicateSatisfiable = checkSatisfiable(predicate, pw);
    if (predicateSatisfiable) {
      PredicateState.findAndPrint(pw, predicate, bufferSize, bufferLeft, bufferRight);
    }
    pw.flush();
    kafkaDataUtil.sendMessage(predicate.operator(), metaAnswers.toString(), watermark);
    return predicateSatisfiable;
  }

  // WARNING: Needs to be thread safe, not using any global mutable state (except kafkaDataUtil)
  private void evaluatePredicateOnPastBuffer(Predicate predicate,
      long watermark, NavigableMap<Long, List<ProvenanceTupleContainer<?>>> currentBuffer,
      Set<ProvenanceTupleContainer<?>> currentOverlap) {
    int emitted = 0;
    final long evaluationStartTime = System.currentTimeMillis();
    for (List<ProvenanceTupleContainer<?>> bucket : currentBuffer.values()) {
      for (int i = 0; i < bucket.size(); i++) {
        ProvenanceTupleContainer<?> tuple = bucket.get(i);
        // Here we do not check if predicate is disabled
        // because operator might have (concurrently) disabled expired predicate it to avoid overhead.
        // However, predicate might still evaluate true for tuples in the past
        if (!currentOverlap.contains(tuple) && predicate.evaluate(tuple, tuple.getTimestamp())) {
          // Serialize in this thread
          kafkaDataUtil.sendTupleSync(predicate.operator(), tuple.getTimestamp(), tuple);
          emitted += 1;
          outputRateStat.increase(1);
        }
        bucket.set(i, null); // Help GC
      }
    }
    final long evaluationDuration = System.currentTimeMillis() - evaluationStartTime;
    String message =
        String.format("PREDICATE_PAST_OVERLAP_NR::: %s ::: %d\n", predicate.operator(),
            currentOverlap.size())
            + String.format("PREDICATE_PAST_EMITTED_NR ::: %s ::: %d\n", predicate.operator(),
            emitted)
            + String.format("PREDICATE_PAST_EVALUATION_MS ::: %s ::: %d\n", predicate.operator(),
            evaluationDuration);
    kafkaDataUtil.sendMessage(predicate.operator(), message, watermark);
  }

  private long leftBoundaryTimestamp(NavigableMap<Long, ?> buffer) {
    Validate.validState(!buffer.isEmpty(), "Tried to get left boundary but buffer is empty!");
    return firstTimestamp(buffer.firstKey());
  }

  private long rightBoundaryTimestamp(NavigableMap<Long, ?> buffer) {
    Validate.validState(!buffer.isEmpty(), "Tried to get right boundary but buffer is empty!");
    return firstTimestamp(buffer.lastKey() + 1);
  }

  private <V> int totalNumberElements(NavigableMap<?, List<V>> buffer) {
    return buffer.values().stream().mapToInt(List::size).sum();
  }

  private static boolean checkSatisfiable(Predicate p, PrintWriter pw) {
    final Set<Condition> predicateBaseConditions = p.baseConditions();
    final Set<Condition> unsatisfiableConditions = predicateBaseConditions.stream()
        .filter(c -> !c.isSatisfiable())
        .collect(Collectors.toSet());
    final String predicateSatisfiability =
        p.isSatisfiable() ? "SATISFIABLE_PREDICATE" : "UNSATISFIABLE_PREDICATE";
    pw.format("%s ::: %s ::: %d/%d conditions unsatisfiable\n", predicateSatisfiability,
        p.operator(), unsatisfiableConditions.size(), predicateBaseConditions.size());
    unsatisfiableConditions.forEach(
        condition -> pw.format("CONDITION_UNSATISFIABLE ::: %s\n", condition));
    return p.isSatisfiable();
  }

  private boolean hasEvaluated(UUID predicateUID) {
    return Objects.equals(predicateUID, lastEvaluatedPredicateUID);
  }

}
