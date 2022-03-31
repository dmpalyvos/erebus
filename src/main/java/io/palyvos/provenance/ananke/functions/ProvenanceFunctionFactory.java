package io.palyvos.provenance.ananke.functions;

import io.palyvos.provenance.ananke.functions.ProvenanceAggregateFunction.GenealogMetadataAccumulator;
import io.palyvos.provenance.util.LongExtractor;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.JoinFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

public interface ProvenanceFunctionFactory {

  <T>  MapFunction<T, ProvenanceTupleContainer<T>> initMap(
      LongExtractor<T> timestampFunction, LongExtractor<T> stimulusFunction);

  <T> FilterFunction<ProvenanceTupleContainer<T>> filter(FilterFunction<T> delegate);

  <T, KEY> KeySelector<ProvenanceTupleContainer<T>, KEY> key(KeySelector<T, KEY> delegate);

  <T, KEY> KeySelector<ProvenanceTupleContainer<T>, KEY> key(KeySelector<T, KEY> delegate,
      Class<KEY> clazz);

  <T, KEY> KeySelector<ProvenanceTupleContainer<T>, KEY> key(KeySelector<T, KEY> delegate,
      TypeInformation<KEY> typeInfo);

  <IN, ACC, OUT>
  AggregateFunction<
      ProvenanceTupleContainer<IN>, GenealogMetadataAccumulator<ACC>, ProvenanceTupleContainer<OUT>>
  aggregate(AggregateFunction<IN, ACC, OUT> delegate);

  <T, O> MapFunction<ProvenanceTupleContainer<T>, ProvenanceTupleContainer<O>> map(
      MapFunction<T, O> delegate);

  <T, O> FlatMapFunction<ProvenanceTupleContainer<T>, ProvenanceTupleContainer<O>> flatMap(
      FlatMapFunction<T, O> delegate);

  <IN1, IN2, OUT>
  JoinFunction<
      ProvenanceTupleContainer<IN1>, ProvenanceTupleContainer<IN2>, ProvenanceTupleContainer<OUT>>
  join(JoinFunction<IN1, IN2, OUT> delegate);

  <T> SinkFunction<ProvenanceTupleContainer<T>> sink(SinkFunction<T> delegate);
}
