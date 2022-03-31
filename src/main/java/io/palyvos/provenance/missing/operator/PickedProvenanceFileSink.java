package io.palyvos.provenance.missing.operator;

import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import io.palyvos.provenance.ananke.output.PickedProvenanceFileProvenanceGraphEncoder;
import io.palyvos.provenance.ananke.output.ProvenanceGraphEncoder;
import io.palyvos.provenance.ananke.output.ProvenanceKey;
import io.palyvos.provenance.genealog.GenealogGraphTraverser;
import io.palyvos.provenance.missing.PickedProvenance;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.util.ComponentCategory;
import io.palyvos.provenance.missing.util.KafkaPredicateUtil;
import io.palyvos.provenance.util.AvgStat;
import io.palyvos.provenance.util.CountStat;
import io.palyvos.provenance.util.ExperimentSettings;
import io.palyvos.provenance.util.ProvenanceActivator;
import io.palyvos.provenance.util.TimestampConverter;
import io.palyvos.provenance.util.TimestampedUIDTuple;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.eventtime.Watermark;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.client.JobCancellationException;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

/**
 * Sink that is part of the helper-query processing the discarded tuples from the original query.
 * Applies the Ananke algorithm to deduplicate the provenance of the tuples.
 *
 * @param <T> The type of sink tuples of the original query.
 */
public class PickedProvenanceFileSink<T extends ProvenanceTupleContainer<?>> extends
    RichSinkFunction<T> implements PredicateUpdatable {

  public static final String TERMINATION_MESSAGE = "Original query finished and helper query processed all pending data";
  private static final TimestampConverter DEFAULT_TS_CONVERTER = ts -> ts;
  private transient ProvenanceGraphEncoder encoder;
  private final ExperimentSettings settings;
  private final String name;
  private transient GenealogGraphTraverser genealogGraphTraverser;
  private transient NavigableSet<ProvenanceKey> pendingSourceAcks;
  private final long maxQueryDelay;
  private transient CountStat throughputStatistic;
  private transient AvgStat provenanceSizeStat;
  private volatile Predicate predicate;
  private transient KafkaPredicateUtil kafkaPredicateUtil;

  public PickedProvenanceFileSink(String name, ExperimentSettings settings,
      Predicate sinkPredicate, long maxQueryDelay) {
    Validate.notBlank(name, "blank sink name");
    Validate.notNull(settings, "settings");
    Validate.isTrue(maxQueryDelay >= 0, "maxDelay < 0");
    final boolean recordingAnankeProvenance =
        settings.provenance() && settings.provenanceActivator() == ProvenanceActivator.ANANKE;
    final boolean noProvenance =
        !settings.provenance() && settings.provenanceActivator() == ProvenanceActivator.GENEALOG;
    Validate.validState(recordingAnankeProvenance || noProvenance,
        "%s requires the ANANKE provenance activator to work with provenance (or GENEALOG activator without provenance)",
        getClass().getSimpleName());
    Validate.notNull(sinkPredicate, "sinkPredicate");
    this.name = name;
    this.settings = settings;
    this.maxQueryDelay = maxQueryDelay;
    updatePredicate(sinkPredicate);
  }

  @Override
  public void open(Configuration parameters) throws Exception {
    super.open(parameters);
    final int taskIndex = getRuntimeContext().getIndexOfThisSubtask();
    this.encoder = new PickedProvenanceFileProvenanceGraphEncoder(settings.outputFile(
        taskIndex, name), true);
    this.genealogGraphTraverser = new GenealogGraphTraverser(
        settings.aggregateStrategySupplier().get(), settings.detailedProvenance());
    this.throughputStatistic = new CountStat(settings.sinkThroughputFile(taskIndex, name),
        settings.autoFlush());
    this.provenanceSizeStat = new AvgStat(
        settings.provenanceSizeFile(taskIndex, name),
        settings.autoFlush());
    this.pendingSourceAcks = new TreeSet<>();
    this.kafkaPredicateUtil = new KafkaPredicateUtil(this.name, this, settings);
    kafkaPredicateUtil.start();

  }

  @Override
  public void invoke(T tuple, Context context) {
    Set<TimestampedUIDTuple> provenance = genealogGraphTraverser.getProvenance(tuple);
    processTupleWithProvenance(tuple, context, provenance);
    throughputStatistic.increase(1);
    provenanceSizeStat.add(provenance.size());
  }

  private void processTupleWithProvenance(T tuple, Context context,
      Set<TimestampedUIDTuple> provenance) {
    // All dropped tuples are "sink" tuples in the context of backward provenance
    // but the encoder should take care to print the operator that actually dropped them
    encoder.sinkVertex(tuple, context.timestamp(), tuple.getTimestamp());
    for (TimestampedUIDTuple sourceTuple : provenance) {
      if (sourceTuple == tuple) {
        // If tuple is already a source tuple, avoid adding edges and a duplicate source
        continue;
      }
      final boolean sourceTupleReceivedBefore = !pendingSourceAcks.add(
          ProvenanceKey.ofTuple(sourceTuple, DEFAULT_TS_CONVERTER, tuple.getStimulus()));
      if (!sourceTupleReceivedBefore) {
        encoder.sourceVertex(sourceTuple, context.timestamp(), tuple.getTimestamp());
      }
      encoder.edge(sourceTuple, tuple, context.timestamp());
    }
  }

  @Override
  public void writeWatermark(Watermark watermark) throws Exception {
    super.writeWatermark(watermark);
    onWatermarkUpdate(watermark.getTimestamp());
    if (PickedProvenance.terminationWatermark(watermark.getTimestamp())) {
      encoder.close();
      throw new JobCancellationException(null, TERMINATION_MESSAGE, null);
    }
    // Force to emit statistic value in case no tuples are arriving
    throughputStatistic.increase(0);
  }

  @Override
  public void onWatermarkUpdate(long watermark) {
    checkPredicateExpired(watermark);
    emitPendingSourceAcks(watermark);
  }

  private void checkPredicateExpired(long watermark) {
    // Print ACK if predicate that will never evaluate true in the future
    final Predicate currentPredicate = this.predicate;
    if (currentPredicate.isEnabled() && currentPredicate.hasExpired(watermark)) {
      encoder.debug("PREDICATE-ACK ::: " + currentPredicate.uid());
      currentPredicate.disable();
    }
  }

  private void emitPendingSourceAcks(long watermark) {
    Iterator<ProvenanceKey> it = pendingSourceAcks.iterator();
    while (it.hasNext()) {
      ProvenanceKey key = it.next();
      if (key.timestamp() < watermark - maxQueryDelay) {
        encoder.ack(key.uid(), key.timestamp());
        it.remove();
      }
    }
  }

  @Override
  public void close() throws Exception {
    super.close();
    kafkaPredicateUtil.close();
    encoder.close();
    throughputStatistic.close();
  }

  @Override
  public void updatePredicate(Predicate newPredicate) {
    this.predicate = newPredicate;
  }

  @Override
  public ComponentCategory category() {
    return ComponentCategory.ALL;
  }
}
