package io.palyvos.provenance.usecases.smartgrid.noprovenance;


import io.palyvos.provenance.usecases.smartgrid.AnomalyResultTuple;
import io.palyvos.provenance.usecases.smartgrid.AverageHouseholdUsageFunction;
import io.palyvos.provenance.usecases.smartgrid.AveragePlugUsageFunction;
import io.palyvos.provenance.usecases.smartgrid.HouseholdUsageTuple;
import io.palyvos.provenance.usecases.smartgrid.NewAnomalyJoinFunction;
import io.palyvos.provenance.usecases.smartgrid.PlugUsageTuple;
import io.palyvos.provenance.usecases.smartgrid.SmartGridFileSource;
import io.palyvos.provenance.usecases.smartgrid.SmartGridTuple;
import io.palyvos.provenance.util.ExperimentSettings;
import io.palyvos.provenance.util.FlinkSerializerActivator;
import io.palyvos.provenance.util.LatencyLoggingSink;
import java.util.concurrent.TimeUnit;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

public class SmartGridAnomaly {

  public static final int ANOMALY_INTERVAL_MINUTES = 1;
  public static final Time ANOMALY_HOUSEHOLD_AGGR_WINDOW_SIZE = Time.minutes(
      ANOMALY_INTERVAL_MINUTES);
  public static final Time ANOMALY_PLUG_AGGR_WINDOW_SIZE = Time.seconds(60);
  public static final Time ANOMALY_JOIN_WINDOW_SIZE = Time.seconds(15);
  public static final long ANOMALY_LIMIT = 5;

  public static void main(String[] args) throws Exception {

    ExperimentSettings settings = ExperimentSettings.newInstance(args);

    final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
    env.getConfig().enableObjectReuse();

    FlinkSerializerActivator.NOPROVENANCE.activate(env, settings)
        .register(SmartGridTuple.class, new SmartGridTuple.KryoSerializer())
        .register(HouseholdUsageTuple.class, new HouseholdUsageTuple.KryoSerializer())
        .register(PlugUsageTuple.class, new PlugUsageTuple.KryoSerializer())
        .register(AnomalyResultTuple.class, new AnomalyResultTuple.KryoSerializer());

    final SingleOutputStreamOperator<SmartGridTuple> sourceStream =
        env.addSource(new SmartGridFileSource(settings))
            .assignTimestampsAndWatermarks(WatermarkStrategy.forMonotonousTimestamps())
            .filter(t -> t.isLoad);

    final SingleOutputStreamOperator<PlugUsageTuple> intervalEnds =
        sourceStream.filter(plugIntervalFilter())
            .keyBy(new KeySelector<SmartGridTuple, Tuple3<Integer, Integer, Integer>>() {
              @Override
              public Tuple3<Integer, Integer, Integer> getKey(SmartGridTuple t) throws Exception {
                return Tuple3.of(t.houseId, t.householdId, t.plugId);
              }
            })
            .window(TumblingEventTimeWindows.of(ANOMALY_PLUG_AGGR_WINDOW_SIZE))
            .aggregate(new AveragePlugUsageFunction()); // Handle double measurements with same ts

    final DataStream<PlugUsageTuple> cleanedIntervalEnds =
        intervalEnds.filter(intervalEndsFilter());

    sourceStream
        .keyBy(new KeySelector<SmartGridTuple, Tuple2<Integer, Integer>>() {
          @Override
          public Tuple2<Integer, Integer> getKey(SmartGridTuple t) throws Exception {
            return Tuple2.of(t.houseId, t.householdId);
          }
        })
        .window(TumblingEventTimeWindows.of(ANOMALY_HOUSEHOLD_AGGR_WINDOW_SIZE))
        .aggregate(new AverageHouseholdUsageFunction())
        .setParallelism(settings.parallelism())
        .join(cleanedIntervalEnds)
        .where(new KeySelector<HouseholdUsageTuple, Tuple2<Integer, Integer>>() {
          @Override
          public Tuple2<Integer, Integer> getKey(HouseholdUsageTuple t) throws Exception {
            return Tuple2.of(t.houseId, t.householdId);
          }
        })
        .equalTo(new KeySelector<PlugUsageTuple, Tuple2<Integer, Integer>>() {
          @Override
          public Tuple2<Integer, Integer> getKey(PlugUsageTuple t) throws Exception {
            return Tuple2.of(t.houseId, t.householdId);
          }
        }, new TypeHint<Tuple2<Integer, Integer>>() {
        }.getTypeInfo())
        .window(TumblingEventTimeWindows.of(ANOMALY_JOIN_WINDOW_SIZE))
        .with(new NewAnomalyJoinFunction())
        .setParallelism(settings.parallelism())
        .filter(t -> t.difference > ANOMALY_LIMIT)
        .setParallelism(settings.parallelism())
        .addSink(LatencyLoggingSink.newInstance("SINK", t -> t.stimulus, settings))
        .setParallelism(settings.parallelism());

    env.execute(SmartGridAnomaly.class.getSimpleName());

  }

  public static FilterFunction<PlugUsageTuple> intervalEndsFilter() {
    return t -> t.usage > 0.5;
  }

  public static FilterFunction<SmartGridTuple> plugIntervalFilter() {
    return t ->
        TimeUnit.MILLISECONDS.toSeconds(t.timestamp) % TimeUnit.MINUTES.toSeconds(
            ANOMALY_INTERVAL_MINUTES) == 0;
  }

}
