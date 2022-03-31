package io.palyvos.provenance.ananke.functions.noop;

import io.palyvos.provenance.ananke.aggregate.ProvenanceAggregateStrategy;
import io.palyvos.provenance.ananke.functions.ProvenanceAggregateFunction;
import io.palyvos.provenance.ananke.functions.ProvenanceAggregateFunction.GenealogMetadataAccumulator;
import io.palyvos.provenance.ananke.functions.ProvenanceFunctionFactory;
import io.palyvos.provenance.ananke.functions.ProvenanceInitializer;
import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import io.palyvos.provenance.genealog.GenealogTupleType;
import io.palyvos.provenance.util.LongExtractor;
import java.io.Serializable;
import java.util.function.Supplier;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.JoinFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

public class NoopProvenanceFunctionFactory implements
    ProvenanceFunctionFactory {

  static final GenealogTupleType NO_PROVENANCE_TUPLE_TYPE = GenealogTupleType.SOURCE;
  private final Supplier<ProvenanceAggregateStrategy> noopStrategySupplier =
      (Supplier<ProvenanceAggregateStrategy> & Serializable) NoopAggregateStrategy::new;

  @Override
  public <T>  MapFunction<T, ProvenanceTupleContainer<T>> initMap(
      LongExtractor<T> timestampFunction, LongExtractor<T> stimulusFunction) {
    // Can reuse the actual version since it only sets timestamp and stimulus which we use even
    // when no provenance
    return new ProvenanceInitializer<T>(timestampFunction, stimulusFunction);
  }

  @Override
  public <T> FilterFunction<ProvenanceTupleContainer<T>> filter(FilterFunction<T> delegate) {
    return new NoopFilterFunction<>(delegate);
  }

  @Override
  public <T, KEY> KeySelector<ProvenanceTupleContainer<T>, KEY> key(KeySelector<T, KEY> delegate) {
    return new NoopKeySelector<>(delegate);
  }

  @Override
  public <T, KEY> KeySelector<ProvenanceTupleContainer<T>, KEY> key(KeySelector<T, KEY> delegate,
      Class<KEY> clazz) {
    return new NoopKeySelectorWithTypeInfo<>(delegate, clazz);
  }

  @Override
  public <T, KEY> KeySelector<ProvenanceTupleContainer<T>, KEY> key(KeySelector<T, KEY> delegate,
      TypeInformation<KEY> typeInfo) {
    return new NoopKeySelectorWithTypeInfo(delegate, typeInfo);
  }

  @Override
  public <IN, ACC, OUT> AggregateFunction<ProvenanceTupleContainer<IN>, GenealogMetadataAccumulator<ACC>, ProvenanceTupleContainer<OUT>> aggregate(
      AggregateFunction<IN, ACC, OUT> delegate) {
    return new ProvenanceAggregateFunction<>(noopStrategySupplier, delegate);
  }

  @Override
  public <T, O> MapFunction<ProvenanceTupleContainer<T>, ProvenanceTupleContainer<O>> map(
      MapFunction<T, O> delegate) {
    return new NoopMapFunction<>(delegate);
  }

  @Override
  public <T, O> FlatMapFunction<ProvenanceTupleContainer<T>, ProvenanceTupleContainer<O>> flatMap(
      FlatMapFunction<T, O> delegate) {
    return new NoopFlatMapFunction<>(delegate);
  }

  @Override
  public <IN1, IN2, OUT> JoinFunction<ProvenanceTupleContainer<IN1>, ProvenanceTupleContainer<IN2>, ProvenanceTupleContainer<OUT>> join(
      JoinFunction<IN1, IN2, OUT> delegate) {
    return new NoopJoinFunction<>(delegate);
  }

  @Override
  public <T> SinkFunction<ProvenanceTupleContainer<T>> sink(SinkFunction<T> delegate) {
    return new NoopSinkFunction<>(delegate);
  }
}
