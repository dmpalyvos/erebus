package io.palyvos.provenance.missing.util;

import io.palyvos.provenance.ananke.functions.ProvenanceAggregateFunction.GenealogMetadataAccumulator;
import io.palyvos.provenance.ananke.functions.ProvenanceFunctionFactory;
import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import io.palyvos.provenance.missing.operator.PickedJoinFunctionWrapper;
import io.palyvos.provenance.util.LongExtractor;
import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.JoinFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

public class PickedProvenanceFunctionFactoryWrapper implements
    ProvenanceFunctionFactory {

  private final ProvenanceFunctionFactory delegate;


  public PickedProvenanceFunctionFactoryWrapper(
      ProvenanceFunctionFactory delegate) {
    Validate.notNull(delegate, "delegate");
    this.delegate = delegate;
  }

  @Override
  public <T> MapFunction<T, ProvenanceTupleContainer<T>> initMap(
      LongExtractor<T> timestampFunction, LongExtractor<T> stimulusFunction) {
    return delegate.initMap(timestampFunction, stimulusFunction);
  }

  @Override
  public <T> FilterFunction<ProvenanceTupleContainer<T>> filter(
      FilterFunction<T> delegate) {
    return this.delegate.filter(delegate);
  }

  @Override
  public <T, KEY> KeySelector<ProvenanceTupleContainer<T>, KEY> key(
      KeySelector<T, KEY> delegate) {
    return this.delegate.key(delegate);
  }

  @Override
  public <T, KEY> KeySelector<ProvenanceTupleContainer<T>, KEY> key(
      KeySelector<T, KEY> delegate, Class<KEY> clazz) {
    return this.delegate.key(delegate, clazz);
  }

  @Override
  public <T, KEY> KeySelector<ProvenanceTupleContainer<T>, KEY> key(KeySelector<T, KEY> delegate,
      TypeInformation<KEY> typeInfo) {
    return this.delegate.key(delegate, typeInfo);
  }

  @Override
  public <IN, ACC, OUT> AggregateFunction<ProvenanceTupleContainer<IN>, GenealogMetadataAccumulator<ACC>, ProvenanceTupleContainer<OUT>> aggregate(
      AggregateFunction<IN, ACC, OUT> delegate) {
    return this.delegate.aggregate(delegate);
  }

  @Override
  public <T, O> MapFunction<ProvenanceTupleContainer<T>, ProvenanceTupleContainer<O>> map(
      MapFunction<T, O> delegate) {
    return this.delegate.map(delegate);
  }

  @Override
  public <T, O> FlatMapFunction<ProvenanceTupleContainer<T>, ProvenanceTupleContainer<O>> flatMap(
      FlatMapFunction<T, O> delegate) {
    return this.delegate.flatMap(delegate);
  }

  @Override
  public <IN1, IN2, OUT> JoinFunction<ProvenanceTupleContainer<IN1>, ProvenanceTupleContainer<IN2>, ProvenanceTupleContainer<OUT>> join(
      JoinFunction<IN1, IN2, OUT> delegate) {
    // We wrap this to set ProvenanceTupleContainer#joined()
    return new PickedJoinFunctionWrapper<>(this.delegate.join(delegate));
  }

  @Override
  public <T> SinkFunction<ProvenanceTupleContainer<T>> sink(
      SinkFunction<T> delegate) {
    return this.delegate.sink(delegate);
  }
}
