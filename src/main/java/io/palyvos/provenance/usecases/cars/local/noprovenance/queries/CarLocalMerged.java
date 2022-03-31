package io.palyvos.provenance.usecases.cars.local.noprovenance.queries;

import static io.palyvos.provenance.usecases.cars.local.CarLocalConstants.CYCLIST_COUNTING_WINDOW_ADVANCE;
import static io.palyvos.provenance.usecases.cars.local.CarLocalConstants.CYCLIST_COUNTING_WINDOW_SIZE;
import static io.palyvos.provenance.usecases.cars.local.CarLocalConstants.PEDESTRIAN_PASSING_WINDOW_ADVANCE;
import static io.palyvos.provenance.usecases.cars.local.CarLocalConstants.PEDESTRIAN_PASSING_WINDOW_SIZE;

import io.palyvos.provenance.usecases.cars.local.AnnotationTuple;
import io.palyvos.provenance.usecases.cars.local.CarLocalInputTuple;
import io.palyvos.provenance.usecases.cars.local.CarLocalResult;
import io.palyvos.provenance.usecases.cars.local.CarLocalSocketSource;
import io.palyvos.provenance.usecases.cars.local.LidarImageContainer;
import io.palyvos.provenance.usecases.cars.local.UUIDKryoSerializer;
import io.palyvos.provenance.usecases.cars.local.noprovenance.CarLocalCountBicycles;
import io.palyvos.provenance.usecases.cars.local.noprovenance.CarLocalCyclistsToSingleObject;
import io.palyvos.provenance.usecases.cars.local.noprovenance.CarLocalFilterBicycles;
import io.palyvos.provenance.usecases.cars.local.noprovenance.CarLocalFilterCyclesInFront;
import io.palyvos.provenance.usecases.cars.local.noprovenance.CarLocalFilterPedestrians;
import io.palyvos.provenance.usecases.cars.local.noprovenance.CarLocalPedestriansPredicate;
import io.palyvos.provenance.usecases.cars.local.noprovenance.CarLocalPedestriansToSingleObject;
import io.palyvos.provenance.util.ExperimentSettings;
import io.palyvos.provenance.util.FlinkSerializerActivator;
import io.palyvos.provenance.util.LatencyLoggingSink;
import java.util.UUID;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;

public class CarLocalMerged {

  public static final String CYCLISTS_SINGLE_OBJECT = "CYCLISTS-SINGLE-OBJECT";
  public static final String PEDESTRIANS_SINGLE_OBJECT = "PEDESTRIANS-SINGLE-OBJECT";
  public static final String FILTER_BICYCLES = "FILTER-BICYCLES";
  public static final String AGGREGATE_COUNT = "AGGREGATE-COUNT";
  public static final String FILTER_CYCLES_INFRONT = "FILTER-CYCLES-INFRONT";
  public static final String FILTER_PEDESTRIANS = "FILTER-PEDESTRIANS";

  public static void main(String[] args) throws Exception {

    ExperimentSettings settings = ExperimentSettings.newInstance(args);

    final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
    env.setMaxParallelism(settings.maxParallelism());
    env.getConfig().enableObjectReuse();

    FlinkSerializerActivator.NOPROVENANCE.activate(env, settings);
    env.addDefaultKryoSerializer(CarLocalInputTuple.class, new CarLocalInputTuple.KryoSerializer());
    env.addDefaultKryoSerializer(LidarImageContainer.class,
        new LidarImageContainer.KryoSerializer());
    env.addDefaultKryoSerializer(UUID.class, new UUIDKryoSerializer());

    SingleOutputStreamOperator<CarLocalInputTuple>
        sourceStream =
        env.addSource(new CarLocalSocketSource(
                settings.sourceIP(), settings.sourcePort(), 10, 500, settings))
            .assignTimestampsAndWatermarks(WatermarkStrategy.forMonotonousTimestamps());

    final DataStream<CarLocalResult> cyclistsSinkStream =
        sourceStream
            .flatMap(new CarLocalCyclistsToSingleObject())
            .startNewChain()
            .name(CYCLISTS_SINGLE_OBJECT)
            .setParallelism(settings.parallelism())
            .filter(new CarLocalFilterBicycles())
            .name(FILTER_BICYCLES)
            .setParallelism(settings.parallelism())
            .keyBy(t -> t.key)
            .window(SlidingEventTimeWindows
                .of(CYCLIST_COUNTING_WINDOW_SIZE, CYCLIST_COUNTING_WINDOW_ADVANCE))
            .aggregate(new CarLocalCountBicycles())
            .name(AGGREGATE_COUNT)
            .filter(new CarLocalFilterCyclesInFront())
            .name(FILTER_CYCLES_INFRONT);

    DataStream<AnnotationTuple> pedestrians = sourceStream
        .flatMap(new CarLocalPedestriansToSingleObject())
        .startNewChain()
        .name(PEDESTRIANS_SINGLE_OBJECT)
        .setParallelism(settings.parallelism())
        .filter(new CarLocalFilterPedestrians())
        .name(FILTER_PEDESTRIANS)
        .setParallelism(settings.parallelism());

    final DataStream<CarLocalResult> pedestriansSinkStream = pedestrians
        .join(pedestrians)
        .where(
            (KeySelector<AnnotationTuple, String>) t -> {
              if (t.type.equals("ring_front_left")) {
                return "ring_front_right" + t.key;
              } else {
                return "ring_front_left" + t.key;
              }
            })
        .equalTo(right -> right.type + right.key)
        .window(SlidingEventTimeWindows
            .of(PEDESTRIAN_PASSING_WINDOW_SIZE, PEDESTRIAN_PASSING_WINDOW_ADVANCE))
        .with(new CarLocalPedestriansPredicate())
        .setParallelism(settings.parallelism());

    pedestriansSinkStream.union(cyclistsSinkStream)
        .addSink(LatencyLoggingSink.newInstance(t -> t.stimulus, settings));

    env.execute(CarLocalMerged.class.getSimpleName());

  }
}
