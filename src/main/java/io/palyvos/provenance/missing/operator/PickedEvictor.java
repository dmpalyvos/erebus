package io.palyvos.provenance.missing.operator;

import static io.palyvos.provenance.missing.predicate.JoinPredicates.one;
import static io.palyvos.provenance.missing.predicate.JoinPredicates.two;

import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer.DiscardedReason;
import io.palyvos.provenance.missing.util.ComponentCategory;
import io.palyvos.provenance.missing.predicate.JoinPredicates;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.predicate.QueryGraphInfo;
import io.palyvos.provenance.missing.util.KafkaDataUtil;
import io.palyvos.provenance.missing.util.KafkaPredicateUtil;
import io.palyvos.provenance.missing.util.PastCheckerUtil;
import io.palyvos.provenance.util.ExperimentSettings;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.Validate;
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer;
import org.apache.flink.streaming.api.datastream.CoGroupedStreams.TaggedUnion;
import org.apache.flink.streaming.api.operators.AbstractStreamOperator;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.api.windowing.evictors.Evictor;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.runtime.operators.windowing.TimestampedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PickedEvictor<T1, T2, W extends TimeWindow> implements
    Evictor<TaggedUnion<ProvenanceTupleContainer<T1>, ProvenanceTupleContainer<T2>>, W>,
    PredicateUpdatable {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractStreamOperator.class);
  private final KryoSerializer<ProvenanceTupleContainer> tupleContainerSerializer;

  // Debug toolkit
  private static final boolean DEBUG = false;
  private final Map<ProvenanceTupleContainer<?>, Integer> memory = new HashMap<>();
  private final Map<ProvenanceTupleContainer<?>, Integer> emitted = new HashMap<>();
  // End debug toolkit

  private final String operatorName;
  private final long windowSlide;
  private final boolean checkAllPanes;
  private final ExperimentSettings settings;
  private long previousWatermark = -1;

  private volatile JoinPredicates predicates;
  private final QueryGraphInfo queryInfo;

  private transient KafkaDataUtil kafkaDataUtil;
  private transient KafkaPredicateUtil kafkaPredicateUtil;
  private transient PastCheckerUtil<TaggedUnion<ProvenanceTupleContainer<T1>, ProvenanceTupleContainer<T2>>> pastCheckerUtilOne;
  private transient PastCheckerUtil<TaggedUnion<ProvenanceTupleContainer<T1>, ProvenanceTupleContainer<T2>>> pastCheckerUtilTwo;


  public static <T1, T2, W extends TimeWindow> PickedEvictor<T1, T2, W> of(
      String operatorName,
      Time windowSlide,
      KryoSerializer<ProvenanceTupleContainer> tupleContainerSerializer, Predicate predicate,
      ExperimentSettings settings, QueryGraphInfo queryInfo) {
    return new PickedEvictor<>(operatorName, windowSlide, tupleContainerSerializer, predicate,
        settings, queryInfo, false);
  }

  protected PickedEvictor(
      String operatorName, Time windowSlide,
      KryoSerializer<ProvenanceTupleContainer> tupleContainerSerializer, Predicate predicate,
      ExperimentSettings settings, QueryGraphInfo queryInfo, boolean checkAllPanes) {
    Validate.notBlank(operatorName, "operatorName");
    Validate.notNull(windowSlide, "windowSlide");
    Validate.notNull(tupleContainerSerializer, "tupleContainerSerializer");
    Validate.notNull(predicate, "predicateOne");
    Validate.notNull(queryInfo, "queryInfo");
    Validate.notBlank(operatorName);
    this.operatorName = operatorName;
    this.tupleContainerSerializer = tupleContainerSerializer;
    this.windowSlide = windowSlide.toMilliseconds();
    this.checkAllPanes = checkAllPanes;
    this.predicates = new JoinPredicates(predicate, operatorName, queryInfo);
    this.queryInfo = queryInfo;
    this.settings = settings;
  }

  @Override
  public void evictBefore(
      Iterable<TimestampedValue<TaggedUnion<ProvenanceTupleContainer<T1>, ProvenanceTupleContainer<T2>>>> elements,
      int size, W window, EvictorContext evictorContext) {

  }

  @Override
  public void evictAfter(
      Iterable<TimestampedValue<TaggedUnion<ProvenanceTupleContainer<T1>, ProvenanceTupleContainer<T2>>>> elements,
      int size, W window, EvictorContext evictorContext) {
    initActions();
    for (TimestampedValue<TaggedUnion<ProvenanceTupleContainer<T1>, ProvenanceTupleContainer<T2>>> element : elements) {
      final boolean lastPane = element.getTimestamp() < window.getStart() + windowSlide;
      if (!(lastPane || checkAllPanes)) {
        continue;
      }
      final ProvenanceTupleContainer<?> tupleContainer = getAndStoreElement(element);
      final Predicate predicate = predicates.get(element.getValue().isOne());
      final long timestamp = element.getTimestamp();
      if (DEBUG) {
        countProcessed(tupleContainer, memory);
      }
      final boolean elementJoined = tupleContainer.isMatched();
      if (!elementJoined) {
        setDiscardedReason(tupleContainer);
        kafkaDataUtil.trySendTuple(predicate.operator(), timestamp, tupleContainer, predicate);
        if (DEBUG) {
          countProcessed(tupleContainer, emitted);
        }
      }
      if (checkAllPanes) {
        tupleContainer.clearJoinedMatched();
      }
    }
    checkUpdateWatermark(window);
  }

  private ProvenanceTupleContainer<?> getAndStoreElement(
      TimestampedValue<TaggedUnion<ProvenanceTupleContainer<T1>, ProvenanceTupleContainer<T2>>> element) {
    ProvenanceTupleContainer<?> tupleContainer;
    if (element.getValue().isOne()) {
      tupleContainer = element.getValue().getOne();
      pastCheckerUtilOne.add(element.getStreamRecord(), predicates.one());
    } else {
      tupleContainer = element.getValue().getTwo();
      pastCheckerUtilTwo.add(element.getStreamRecord(), predicates.two());
    }
    return tupleContainer;
  }

  private void setDiscardedReason(ProvenanceTupleContainer<?> tupleContainer) {
    final DiscardedReason discardedReason = tupleContainer.isJoined()
        ? DiscardedReason.JOINED_NOT_MATCHED
        : DiscardedReason.NOT_JOINED;
    tupleContainer.setDiscardedReason(discardedReason);
  }

  private void initActions() {
    final boolean isFirstCall = kafkaDataUtil == null;
    if (isFirstCall) {
      kafkaDataUtil = new KafkaDataUtil(operatorName, tupleContainerSerializer, settings);
      kafkaPredicateUtil = new KafkaPredicateUtil(operatorName, this, settings);
      pastCheckerUtilOne = new PastCheckerUtil<>(one(operatorName), kafkaDataUtil,
          tu -> tu.getOne(), settings);
      pastCheckerUtilTwo = new PastCheckerUtil<>(two(operatorName), kafkaDataUtil,
          tu -> tu.getTwo(), settings);
      pastCheckerUtilOne.onWatermark(Watermark.UNINITIALIZED.getTimestamp(),
          predicates.one());
      pastCheckerUtilTwo.onWatermark(Watermark.UNINITIALIZED.getTimestamp(),
          predicates.two());
      kafkaPredicateUtil.start();
    }
  }

  private void countProcessed(ProvenanceTupleContainer<?> tupleContainer,
      Map<ProvenanceTupleContainer<?>, Integer> emitted) {
    Integer count = emitted.get(tupleContainer);
    if (count == null) {
      count = 0;
    }
    emitted.put(tupleContainer, count + 1);
  }

  private void checkUpdateWatermark(W window) {
    final long watermark = window.getStart() - 1;
    if (watermark > previousWatermark) {
      previousWatermark = watermark;
      onWatermarkUpdate(watermark);
    }
  }

  @Override
  public void updatePredicate(Predicate newPredicate) {
    this.predicates = new JoinPredicates(newPredicate, operatorName, queryInfo);
  }

  @Override
  public ComponentCategory category() {
    return ComponentCategory.QUERY_OPERATORS;
  }

  @Override
  public void onWatermarkUpdate(long watermark) {
    // Keep local ref to make sure we do not disable concurrently altered predicate
    // in case old one had expired
    final JoinPredicates currentPredicates = this.predicates;
    pastCheckerUtilOne.onWatermark(watermark, currentPredicates.one());
    pastCheckerUtilTwo.onWatermark(watermark, currentPredicates.two());
  }
}