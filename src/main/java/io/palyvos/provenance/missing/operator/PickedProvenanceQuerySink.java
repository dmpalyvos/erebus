package io.palyvos.provenance.missing.operator;

import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import io.palyvos.provenance.missing.PickedProvenance;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.util.ComponentCategory;
import io.palyvos.provenance.missing.util.KafkaDataUtil;
import io.palyvos.provenance.missing.util.KafkaPredicateUtil;
import io.palyvos.provenance.missing.util.PastCheckerUtil;
import io.palyvos.provenance.util.AvgStat;
import io.palyvos.provenance.util.CountStat;
import io.palyvos.provenance.util.ExperimentSettings;
import io.palyvos.provenance.util.LongExtractor;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.OptionalLong;
import java.util.function.BiFunction;
import org.apache.commons.lang3.Validate;
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.operators.ChainingStrategy;
import org.apache.flink.streaming.api.operators.StreamSink;
import org.apache.flink.streaming.api.transformations.LegacySinkTransformation;
import org.apache.flink.streaming.api.transformations.PhysicalTransformation;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PickedProvenanceQuerySink<T extends ProvenanceTupleContainer> extends
    StreamSink<T> implements
    PredicateUpdatable {

  private static final Logger LOG = LoggerFactory.getLogger(Predicate.class);

  private final ExperimentSettings settings;
  private final KryoSerializer<ProvenanceTupleContainer> kryoSerializer;
  private final String name;
  private volatile Predicate sinkPredicate;
  private transient KafkaDataUtil kafkaDataUtil;
  private transient KafkaPredicateUtil kafkaPredicateUtil;
  private transient PastCheckerUtil<T> pastCheckerUtil;
  private final long predicateSlackMillis;

  private transient AvgStat latencyStatistic;
  private transient CountStat throughputStatistic;
  private final LongExtractor<T> latencyFunction;

  public static <T extends ProvenanceTupleContainer> void connect(DataStream<T> stream,
      LongExtractor<T> latencyFunction,
      Predicate sinkPredicate, ExperimentSettings settings,
      KryoSerializer<ProvenanceTupleContainer> kryoSerializer) {
    connect(stream, new PrintSinkFunction<>(PickedProvenance.QUERY_SINK_NAME, settings),
        latencyFunction, sinkPredicate, settings, kryoSerializer,
        settings.sinkParallelism(), true);
  }

  public static <T extends ProvenanceTupleContainer> void connect(DataStream<T> stream,
      LongExtractor<T> latencyFunction,
      Predicate sinkPredicate, ExperimentSettings settings,
      KryoSerializer<ProvenanceTupleContainer> kryoSerializer, int parallelism, boolean chaining) {
    connect(stream, new PrintSinkFunction<>(PickedProvenance.QUERY_SINK_NAME, settings),
        latencyFunction, sinkPredicate, settings, kryoSerializer,
        parallelism, chaining);
  }

  public static <T extends ProvenanceTupleContainer> void connect(DataStream<T> stream,
      SinkFunction<T> sinkFunction,
      LongExtractor<T> latencyFunction,
      Predicate sinkPredicate, ExperimentSettings settings,
      KryoSerializer<ProvenanceTupleContainer> kryoSerializer, int parallelism,
      boolean chaining) {
    StreamSink<T> sinkOperator = new PickedProvenanceQuerySink<>(PickedProvenance.QUERY_SINK_NAME,
        latencyFunction, sinkPredicate, settings, kryoSerializer,
        sinkFunction);
    PhysicalTransformation transformation = new LegacySinkTransformation<T>(
        stream.getTransformation(),
        PickedProvenance.QUERY_SINK_NAME, sinkOperator, parallelism);
    if (!chaining) {
      transformation.setChainingStrategy(ChainingStrategy.NEVER);
    }
    stream.getExecutionEnvironment().addOperator(transformation);
  }


  protected PickedProvenanceQuerySink(String name, LongExtractor<T> latencyFunction,
      Predicate sinkPredicate, ExperimentSettings settings,
      KryoSerializer<ProvenanceTupleContainer> kryoSerializer, SinkFunction<T> sinkFunction) {
    super(sinkFunction);
    Validate.notNull(settings, "settings");
    Validate.notBlank(name, "blank sink name");
    Validate.notNull(latencyFunction, "latencyFunction");
    Validate.notNull(sinkPredicate, "sinkPredicate");
    Validate.notNull(kryoSerializer, "kryoSerializer");
    this.settings = settings;
    this.name = name;
    this.kryoSerializer = kryoSerializer;
    this.predicateSlackMillis = settings.predicateSlackMillis();
    this.latencyFunction = latencyFunction;
    updatePredicate(sinkPredicate);
  }

  @Override
  public void open() throws Exception {
    super.open();
    final int taskIndex = getRuntimeContext().getIndexOfThisSubtask();
    this.latencyStatistic =
        new AvgStat(settings.latencyFile(taskIndex, name), settings.autoFlush());
    this.throughputStatistic = new CountStat(settings.sinkThroughputFile(taskIndex, name),
        settings.autoFlush());
    this.kafkaDataUtil = new KafkaDataUtil(name, kryoSerializer, settings);
    pastCheckerUtil = new PastCheckerUtil<>(
        name, kafkaDataUtil, tc -> tc, settings);
    pastCheckerUtil.onWatermark(Watermark.UNINITIALIZED.getTimestamp(), sinkPredicate);
    kafkaPredicateUtil = new KafkaPredicateUtil(name, this, settings);
    kafkaPredicateUtil.start();
    if (taskIndex == 0) {
      // If there is a delayed predicate, send it from here to start (only from one subtask)
      // counting the delay from the time query is initialized
      settings.checkSendDelayedPredicate();
    }
  }

  @Override
  public void processElement(StreamRecord<T> element) throws Exception {
    super.processElement(element);
    final long now = System.currentTimeMillis();
    latencyStatistic.add(now - latencyFunction.applyAsLong(element.getValue()));
    throughputStatistic.increase(1);
    pastCheckerUtil.add(element, sinkPredicate);
    kafkaDataUtil.trySendTuple(sinkPredicate.operator(), element.getTimestamp(), element.getValue(),
        sinkPredicate);
  }

  @Override
  public void processWatermark(Watermark mark) throws Exception {
    super.processWatermark(mark);
    onWatermarkUpdate(mark.getTimestamp());
  }

  @Override
  public void onWatermarkUpdate(long watermark) {
    pastCheckerUtil.onWatermark(watermark, this.sinkPredicate);
  }

  @Override
  public void updatePredicate(Predicate newPredicate) {
    final Predicate renamed = newPredicate.renamed(name);
    if (predicateSlackMillis > 0) {
      this.sinkPredicate = renamed.timeShifted(
          (l, r) -> OptionalLong.of(l),
          slackPredicateRightBoundaryTransform());
      LOG.info("Adding {} slack to the predicate. New predicate: {}", predicateSlackMillis,
          sinkPredicate);
    } else {
      this.sinkPredicate = renamed;
    }
  }

  private BiFunction<Long, Long, OptionalLong> slackPredicateRightBoundaryTransform() {
    final BiFunction<Long, Long, OptionalLong> slackPredicateRightBoundaryTransform = (l, r) -> {
      if (r == Long.MAX_VALUE || predicateSlackMillis == Long.MAX_VALUE) {
        return OptionalLong.of(Long.MAX_VALUE);
      }
      return OptionalLong.of(r + predicateSlackMillis);
    };
    return slackPredicateRightBoundaryTransform;
  }

  @Override
  public ComponentCategory category() {
    return ComponentCategory.ALL;
  }

  @Override
  public void close() throws Exception {
    super.close();
    kafkaDataUtil.sendTermination();
    kafkaDataUtil.close();
    kafkaPredicateUtil.close();
    latencyStatistic.close();
    throughputStatistic.close();
  }

  private static class PrintSinkFunction<T extends ProvenanceTupleContainer> extends
      RichSinkFunction<T> {

    private transient PrintWriter pw;
    private final String name;
    private final ExperimentSettings settings;

    private PrintSinkFunction(String name, ExperimentSettings settings) {
      Validate.notBlank(name, "blank name");
      Validate.notNull(settings, "settings");
      this.settings = settings;
      this.name = name;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
      super.open(parameters);
      final int taskIndex = getRuntimeContext().getIndexOfThisSubtask();
      try {
        pw = new PrintWriter(new FileWriter(settings.outputFile(taskIndex, name)),
            settings.autoFlush());
      } catch (IOException e) {
        throw new IllegalArgumentException(e);
      }
    }

    @Override
    public void invoke(T tuple, Context context) throws Exception {
      super.invoke(tuple, context);
      pw.println(context.timestamp() + " ::: " + tuple);
    }

    @Override
    public void close() throws Exception {
      super.close();
      pw.print("--- OUTPUT END ---");
      pw.flush();
      pw.close();
    }
  }

}
