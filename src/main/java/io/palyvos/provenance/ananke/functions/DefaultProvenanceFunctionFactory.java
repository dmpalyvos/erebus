package io.palyvos.provenance.ananke.functions;

import io.palyvos.provenance.ananke.aggregate.ProvenanceAggregateStrategy;
import io.palyvos.provenance.ananke.functions.ProvenanceAggregateFunction.GenealogMetadataAccumulator;
import io.palyvos.provenance.util.LongExtractor;
import java.io.Serializable;
import java.util.function.Supplier;
import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.JoinFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

public class DefaultProvenanceFunctionFactory implements ProvenanceFunctionFactory {

  private final Supplier<ProvenanceAggregateStrategy> aggregateStrategy;

  public DefaultProvenanceFunctionFactory(Supplier<ProvenanceAggregateStrategy> aggregateStrategy) {
    Validate.isInstanceOf(Serializable.class, aggregateStrategy,
        "Aggregate strategy must be Serializable");
    this.aggregateStrategy = aggregateStrategy;
  }

  @Override
  public <T>  MapFunction<T, ProvenanceTupleContainer<T>> initMap(
      LongExtractor<T> timestampFunction, LongExtractor<T> stimulusFunction) {
    return new ProvenanceInitializer<T>(timestampFunction, stimulusFunction);
  }

  @Override
  public <T> FilterFunction<ProvenanceTupleContainer<T>> filter(FilterFunction<T> delegate) {
    return new ProvenanceFilterFunction<>(delegate);
  }

  @Override
  public <T, KEY> KeySelector<ProvenanceTupleContainer<T>, KEY> key(KeySelector<T, KEY> delegate) {
    return new ProvenanceKeySelector<>(delegate);
  }


  @Override
  public <T, KEY> KeySelector<ProvenanceTupleContainer<T>, KEY> key(KeySelector<T, KEY> delegate,
      Class<KEY> clazz) {
    return new ProvenanceKeySelectorWithTypeInfo<>(delegate, clazz);
  }

  @Override
  public <T, KEY> KeySelector<ProvenanceTupleContainer<T>, KEY> key(KeySelector<T, KEY> delegate,
      TypeInformation<KEY> typeInfo) {
    return new ProvenanceKeySelectorWithTypeInfo<>(delegate, typeInfo);
  }

  @Override
  public <IN, ACC, OUT>
  AggregateFunction<
      ProvenanceTupleContainer<IN>, GenealogMetadataAccumulator<ACC>, ProvenanceTupleContainer<OUT>>
  aggregate(AggregateFunction<IN, ACC, OUT> delegate) {
    return new ProvenanceAggregateFunction<>(aggregateStrategy, delegate);
  }

  @Override
  public <T, O> MapFunction<ProvenanceTupleContainer<T>, ProvenanceTupleContainer<O>> map(
      MapFunction<T, O> delegate) {
    return new ProvenanceMapFunction<>(delegate);
  }

  @Override
  public <T, O> FlatMapFunction<ProvenanceTupleContainer<T>, ProvenanceTupleContainer<O>> flatMap(
      FlatMapFunction<T, O> delegate) {
    return new ProvenanceFlatMapFunction<>(delegate);
  }


  @Override
  public <IN1, IN2, OUT>
  JoinFunction<
      ProvenanceTupleContainer<IN1>, ProvenanceTupleContainer<IN2>, ProvenanceTupleContainer<OUT>>
  join(JoinFunction<IN1, IN2, OUT> delegate) {
    return new ProvenanceJoinFunction<>(delegate);
  }

  @Override
  public <T> SinkFunction<ProvenanceTupleContainer<T>> sink(SinkFunction<T> delegate) {
    return new ProvenanceSinkFunction<>(delegate);
  }
}
