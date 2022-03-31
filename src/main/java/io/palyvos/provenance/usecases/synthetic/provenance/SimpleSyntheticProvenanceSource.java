package io.palyvos.provenance.usecases.synthetic.provenance;

import io.palyvos.provenance.genealog.GenealogTuple;
import io.palyvos.provenance.util.CountStat;
import io.palyvos.provenance.util.ExperimentSettings;
import io.palyvos.provenance.util.IncreasingUIDGenerator;
import io.palyvos.provenance.util.TimestampedUIDTuple;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Supplier;
import org.apache.commons.lang3.Validate;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Source that emits sink tuples which already contain (random) provenance.
 *
 * @param <T> The type of sink tuples to be emitted.
 */
public class SimpleSyntheticProvenanceSource<T extends GenealogTuple> extends
    RichParallelSourceFunction<T> {

  private static final String NAME = "SOURCE";
  private static final Logger LOG = LoggerFactory.getLogger(SimpleSyntheticProvenanceSource.class);
  private static final int TUPLE_QUEUE_SIZE = 100_000;
  private static final int N_PRODUCER_THREADS = 1;
  private final ExperimentSettings settings;
  private transient CountStat throughputStatistic;
  private final Supplier<T> tupleSupplier;
  private final Supplier<? extends TimestampedUIDTuple> provenanceSupplier;
  private volatile boolean enabled = true;

  private final ArrayBlockingQueue<T> tuples = new ArrayBlockingQueue<>(TUPLE_QUEUE_SIZE);
  private transient List<TupleProducer<T>> producers;
  private transient List<Thread> producerThreads;

  public <F extends Supplier<T> & Serializable> SimpleSyntheticProvenanceSource(
      F tupleSupplier,
      Supplier<? extends TimestampedUIDTuple> provenanceSupplier,
      ExperimentSettings settings) {
    Validate.validState(settings.syntheticProvenanceOverlap() == 0,
        "This source does not support provenance overlap");
    this.settings = settings;
    this.tupleSupplier = tupleSupplier;
    this.provenanceSupplier = provenanceSupplier;
  }

  @Override
  public void open(Configuration parameters) throws Exception {
    super.open(parameters);
    final int subtaskIndex = getRuntimeContext().getIndexOfThisSubtask();
    this.throughputStatistic =
        new CountStat(settings.throughputFile(subtaskIndex, NAME), settings.autoFlush());
    this.producers = new ArrayList<>();
    this.producerThreads = new ArrayList<>();
  }

  private void startProducers() {
    Validate.validState(N_PRODUCER_THREADS > 0, "No producer thread requested!");
    for (int i = 0; i < N_PRODUCER_THREADS; i++) {
      final int producerIndex =
          i + N_PRODUCER_THREADS * getRuntimeContext().getIndexOfThisSubtask();
      TupleProducer<T> producer = new TupleProducer<>(tuples, tupleSupplier, provenanceSupplier,
          settings, producerIndex);
      Thread producerThread = new Thread(producer);
      producerThread.setName("SourceTupleProducer-" + i);
      producerThread.setDaemon(true);
      producers.add(producer);
      producerThreads.add(producerThread);
      producerThread.start();
    }
  }

  private void stopProducers() {
    for (int i = 0; i < N_PRODUCER_THREADS; i++) {
      producers.get(i).enabled = false;
      producerThreads.get(i).interrupt();
    }
  }

  @Override
  public void run(SourceContext<T> ctx) throws Exception {
    startProducers();
    while (enabled) {
      try {
        final T tuple = tuples.take();
        ctx.collectWithTimestamp(tuple, tuple.getTimestamp());
        throughputStatistic.increase(1);
      } catch (InterruptedException e) {
        stopProducers();
        LOG.info("Source interrupted");
        break;
      }
    }
    stopProducers();
    throughputStatistic.close();
  }

  private static class TupleProducer<T extends GenealogTuple> implements Runnable {

    private final Supplier<T> tupleSupplier;
    private final Supplier<? extends TimestampedUIDTuple> provenanceSupplier;
    private final IncreasingUIDGenerator uidGenerator;
    private final Random random;
    private final ExperimentSettings settings;
    private volatile boolean enabled = true;
    private final ArrayBlockingQueue<T> tuples;

    public TupleProducer(ArrayBlockingQueue<T> tuples, Supplier<T> tupleSupplier,
        Supplier<? extends TimestampedUIDTuple> provenanceSupplier, ExperimentSettings settings,
        int producerIndex) {
      this.tuples = tuples;
      this.tupleSupplier = tupleSupplier;
      this.provenanceSupplier = provenanceSupplier;
      this.uidGenerator = new IncreasingUIDGenerator(producerIndex);
      this.random = new Random(producerIndex);
      this.settings = settings;
    }

    T newSinkTuple(long timestamp) {
      final T sinkTuple = tupleSupplier.get();
      sinkTuple.setTimestamp(timestamp);
      final Collection<TimestampedUIDTuple> provenance = new HashSet<>();
      for (int j = 0; j < settings.syntheticProvenanceSize(); j++) {
        final TimestampedUIDTuple sourceTuple = provenanceSupplier.get();
        sourceTuple.setStimulus(System.currentTimeMillis());
        sourceTuple.setTimestamp(
            sinkTuple.getTimestamp() - random.nextInt(settings.syntheticDelay()));
        sourceTuple.setUID(uidGenerator.newUID());
        provenance.add(sourceTuple);
      }
      sinkTuple.getGenealogData().setProvenance(provenance);
      return sinkTuple;
    }

    @Override
    public void run() {
      while (enabled) {
        final long now = System.currentTimeMillis();
        final T tuple = newSinkTuple(now);
        tuple.setStimulus(now);
        try {
          tuples.put(tuple);
        } catch (InterruptedException e) {
          LOG.info("Producer interrupted");
          break;
        }
      }
    }
  }

  @Override
  public void cancel() {
    enabled = false;
  }
}
