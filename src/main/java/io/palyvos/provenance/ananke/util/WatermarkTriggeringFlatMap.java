package io.palyvos.provenance.ananke.util;

import io.palyvos.provenance.util.TimestampedTuple;
import java.io.Serializable;
import java.util.function.BiConsumer;
import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.Utils;
import org.apache.flink.api.java.typeutils.TypeExtractor;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.operators.StreamFlatMap;
import org.apache.flink.streaming.api.operators.TimestampedCollector;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.util.Collector;

public class WatermarkTriggeringFlatMap<T, O> extends StreamFlatMap<T, O> {

  private final BiConsumer<Watermark, Collector<O>> watermarkConsumer;
  private final WatermarkTransform watermarkTransform;
  private transient TimestampedCollector<O> watermarkConsumerCollector;

  public WatermarkTriggeringFlatMap(
      FlatMapFunction<T, O> flatMapFunction,
      BiConsumer<Watermark, Collector<O>> watermarkConsumer,
      WatermarkTransform watermarkTransform) {
    super(flatMapFunction);
    Validate.isInstanceOf(Serializable.class, watermarkConsumer,
        "WatermarkConsumer must be serializable!");
    Validate.notNull(watermarkTransform, "watermarkTransform");
    this.watermarkConsumer = watermarkConsumer;
    this.watermarkTransform = watermarkTransform;
  }

  @Override
  public void open() throws Exception {
    super.open();
    watermarkConsumerCollector = new TimestampedCollector<>(output);
  }

  @Override
  public void processWatermark(Watermark mark) throws Exception {
    Watermark newMark = watermarkTransform.apply(mark);
    watermarkConsumerCollector.setAbsoluteTimestamp(newMark.getTimestamp());
    watermarkConsumer.accept(newMark, watermarkConsumerCollector);
    super.processWatermark(newMark);
  }

  public static <T, O> SingleOutputStreamOperator<O> connect(
      DataStream<T> stream, FlatMapFunction<T, O> flatMapFunction,
      BiConsumer<Watermark, Collector<O>> watermarkConsumer) {
    return connect(stream, flatMapFunction, watermarkConsumer, w -> w);
  }

  public static <T, O> SingleOutputStreamOperator<O> connect(
      DataStream<T> stream, FlatMapFunction<T, O> flatMapFunction,
      BiConsumer<Watermark, Collector<O>> watermarkConsumer,
      WatermarkTransform watermarkTransform) {

    Validate.notNull(flatMapFunction);
    Validate.notNull(watermarkConsumer);
    Validate.notNull(stream);

    StreamExecutionEnvironment env = stream.getExecutionEnvironment();

    TypeInformation<O> outputType = TypeExtractor.getFlatMapReturnTypes(env.clean(flatMapFunction),
        stream.getType(), Utils.getCallLocationName(), true);

    return stream.transform("FlatMap", outputType,
        new WatermarkTriggeringFlatMap<T, O>(env.clean(flatMapFunction), watermarkConsumer,
            watermarkTransform));
  }

  public static <IN extends TimestampedTuple> SingleOutputStreamOperator<IN> connectToStream(
      DataStream<IN> stream, Class<IN> clazz) {
    return stream.transform(
        FlinkTimestampCopier.class.getSimpleName(),
        TypeInformation.of(clazz),
        new FlinkTimestampCopier<>());
  }
}
