package io.palyvos.provenance.missing.operator;

import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import io.palyvos.provenance.missing.util.ComponentCategory;
import io.palyvos.provenance.missing.PickedProvenance;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.predicate.QueryGraphInfo;
import io.palyvos.provenance.missing.util.KafkaDataUtil;
import io.palyvos.provenance.missing.util.KafkaPredicateUtil;
import io.palyvos.provenance.missing.util.PastCheckerUtil;
import io.palyvos.provenance.util.ExperimentSettings;
import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.operators.StreamFilter;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;

public class PickedStreamFilter<IN> extends StreamFilter<ProvenanceTupleContainer<IN>> implements
    PredicateUpdatable {

  private final KryoSerializer<ProvenanceTupleContainer> serializer;
  private final String name;
  private final QueryGraphInfo queryInfo;
  private final ExperimentSettings settings;
  private volatile Predicate predicate;
  private transient KafkaDataUtil kafkaDataUtil;
  private transient KafkaPredicateUtil kafkaPredicateUtil;
  private transient PastCheckerUtil<ProvenanceTupleContainer<IN>> pastCheckerUtil;

  public static <R> SingleOutputStreamOperator<ProvenanceTupleContainer<R>> connect(
      DataStream<ProvenanceTupleContainer<R>> input,
      FilterFunction<ProvenanceTupleContainer<R>> filter,
      String name, KryoSerializer<ProvenanceTupleContainer> serializer, Predicate predicate,
      QueryGraphInfo queryInfo, ExperimentSettings settings) {
    return input.transform(name, input.getType(),
        new PickedStreamFilter<>(name, filter, serializer, predicate, queryInfo, settings));
  }

  protected PickedStreamFilter(
      String name, FilterFunction<ProvenanceTupleContainer<IN>> filterFunction,
      KryoSerializer<ProvenanceTupleContainer> serializer,
      Predicate predicate, QueryGraphInfo queryInfo,
      ExperimentSettings settings) {
    super(filterFunction);
    Validate.notEmpty(name, "name");
    Validate.notNull(serializer, "serializer");
    Validate.notNull(predicate, "predicate");
    Validate.notNull(queryInfo, "queryInfo");
    Validate.notNull(settings, "settings");
    this.name = name;
    this.serializer = serializer;
    this.queryInfo = queryInfo;
    this.predicate = predicate.transformed(name, PickedProvenance.QUERY_SINK_NAME, queryInfo);
    this.settings = settings;
  }

  @Override
  public void open() throws Exception {
    super.open();
    kafkaDataUtil = new KafkaDataUtil(name, serializer, settings);
    kafkaPredicateUtil = new KafkaPredicateUtil(this.name, this, settings);
    pastCheckerUtil = new PastCheckerUtil<>(
        name, kafkaDataUtil, tc -> tc, settings);
    pastCheckerUtil.onWatermark(Watermark.UNINITIALIZED.getTimestamp(), predicate);
    kafkaPredicateUtil.start();
  }

  @Override
  public void processElement(StreamRecord<ProvenanceTupleContainer<IN>> element) throws Exception {
    if (userFunction.filter(element.getValue())) {
      output.collect(element);
    } else {
      pastCheckerUtil.add(element, predicate);
      kafkaDataUtil.trySendTuple(predicate.operator(), element.getTimestamp(),
          element.getValue(), predicate);
    }
  }

  @Override
  public void updatePredicate(Predicate newPredicate) {
    this.predicate = newPredicate.transformed(name, PickedProvenance.QUERY_SINK_NAME, queryInfo);
  }

  @Override
  public ComponentCategory category() {
    return ComponentCategory.QUERY_OPERATORS;
  }

  @Override
  public void processWatermark(Watermark mark) throws Exception {
    super.processWatermark(mark);
    onWatermarkUpdate(mark.getTimestamp());
  }

  @Override
  public void onWatermarkUpdate(long watermark) {
    pastCheckerUtil.onWatermark(watermark, this.predicate);
  }

  @Override
  public void close() throws Exception {
    super.close();
    kafkaPredicateUtil.close();
  }
}
