package io.palyvos.provenance.usecases.cars.local.provenance2.queries;

import static io.palyvos.provenance.usecases.cars.local.CarLocalConstants.CYCLIST_COUNTING_WINDOW_ADVANCE;
import static io.palyvos.provenance.usecases.cars.local.CarLocalConstants.CYCLIST_COUNTING_WINDOW_SIZE;
import static io.palyvos.provenance.usecases.cars.local.CarLocalConstants.PEDESTRIAN_PASSING_WINDOW_ADVANCE;
import static io.palyvos.provenance.usecases.cars.local.CarLocalConstants.PEDESTRIAN_PASSING_WINDOW_SIZE;
import static io.palyvos.provenance.usecases.cars.local.noprovenance.queries.CarLocalMerged.AGGREGATE_COUNT;
import static io.palyvos.provenance.usecases.cars.local.noprovenance.queries.CarLocalMerged.CYCLISTS_SINGLE_OBJECT;
import static io.palyvos.provenance.usecases.cars.local.noprovenance.queries.CarLocalMerged.FILTER_BICYCLES;
import static io.palyvos.provenance.usecases.cars.local.noprovenance.queries.CarLocalMerged.FILTER_CYCLES_INFRONT;
import static io.palyvos.provenance.usecases.cars.local.noprovenance.queries.CarLocalMerged.FILTER_PEDESTRIANS;
import static io.palyvos.provenance.usecases.cars.local.noprovenance.queries.CarLocalMerged.PEDESTRIANS_SINGLE_OBJECT;

import io.palyvos.provenance.ananke.functions.ProvenanceFunctionFactory;
import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import io.palyvos.provenance.missing.PickedProvenance;
import io.palyvos.provenance.missing.operator.PickedEvictor;
import io.palyvos.provenance.missing.operator.PickedProvenanceQuerySink;
import io.palyvos.provenance.missing.operator.PickedStreamFilter;
import io.palyvos.provenance.missing.predicate.JoinPredicates;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.predicate.QueryGraphInfo;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;

public class CarLocalMerged {

  private static final String JOIN = "JOIN";
  public static final List<String> OPERATORS = Arrays.asList(FILTER_BICYCLES, FILTER_CYCLES_INFRONT,
      FILTER_PEDESTRIANS,
      JoinPredicates.one(JOIN), JoinPredicates.two(JOIN));

  public static void main(String[] args) throws Exception {
    ExperimentSettings settings = ExperimentSettings.newInstance(args);
    QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(
        "src/main/resources/CarLocalMerged-DAG.yaml");

    final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

    env.setMaxParallelism(settings.maxParallelism());
    env.getConfig().

        enableObjectReuse();
    FlinkSerializerActivator.PROVENANCE_TRANSPARENT.activate(env, settings)
        .register(CarLocalInputTuple.class, new CarLocalInputTuple.KryoSerializer())
        .register(LidarImageContainer.class, new LidarImageContainer.KryoSerializer())
        .register(UUID.class, new UUIDKryoSerializer())
        .registerOperators(OPERATORS);

    PickedProvenance.initKafka();
    final Predicate selectedPredicate = settings.initPredicate(CarLocalPredicates.INSTANCE);
    final ProvenanceFunctionFactory PF = settings.provenanceFunctionFactory(true);
    final KryoSerializer<ProvenanceTupleContainer> serializer = PickedProvenance.newSerializer(env);

    SingleOutputStreamOperator<ProvenanceTupleContainer<CarLocalInputTuple>>
        sourceStream =
        env.addSource(new CarLocalSocketSource(
                settings.sourceIP(), settings.sourcePort(), 10, 500, settings))
            .assignTimestampsAndWatermarks(WatermarkStrategy.forMonotonousTimestamps())
            .map(PF.initMap(t -> t.f0, t -> t.getStimulus()))
            .map(settings.provenanceActivator().uidAssigner(0, settings.maxParallelism()))
            .returns(new TypeHint<ProvenanceTupleContainer<CarLocalInputTuple>>() {
            });

    // Cyclists Sub-query
    final DataStream<ProvenanceTupleContainer<AnnotationTuple>> lidarSingleObject =
        sourceStream
            .flatMap(PF.flatMap(new CarLocalCyclistsToSingleObject()))
            .startNewChain()
            .name(CYCLISTS_SINGLE_OBJECT)
            .setParallelism(settings.parallelism());
    final DataStream<ProvenanceTupleContainer<CarLocalResult>> aggregatedCyclists =
        PickedStreamFilter.connect(lidarSingleObject, PF.filter(new CarLocalFilterBicycles()),
                FILTER_BICYCLES, serializer, selectedPredicate, queryInfo, settings)
            .name(FILTER_BICYCLES)
            .setParallelism(settings.parallelism())
            .keyBy(PF.key(t -> t.key, UUID.class))
            .window(SlidingEventTimeWindows
                .of(CYCLIST_COUNTING_WINDOW_SIZE, CYCLIST_COUNTING_WINDOW_ADVANCE))
            .aggregate(PF.aggregate(new CarLocalCountBicycles()))
            .name(AGGREGATE_COUNT);

    final DataStream<ProvenanceTupleContainer<CarLocalResult>> cyclistsSinkStream =
        PickedStreamFilter.connect(aggregatedCyclists, PF.filter(new CarLocalFilterCyclesInFront()),
                FILTER_CYCLES_INFRONT, serializer, selectedPredicate, queryInfo, settings)
            .name(FILTER_CYCLES_INFRONT);

    // Pedestrians Sub-query
    final DataStream<ProvenanceTupleContainer<AnnotationTuple>> pedestrians =
        PickedStreamFilter.connect(
                sourceStream
                    .flatMap(PF.flatMap(new CarLocalPedestriansToSingleObject()))
                    .startNewChain()
                    .name(PEDESTRIANS_SINGLE_OBJECT)
                    .setParallelism(settings.parallelism()),
                PF.filter(new CarLocalFilterPedestrians()), FILTER_PEDESTRIANS,
                serializer, selectedPredicate, queryInfo, settings)
            .name(FILTER_PEDESTRIANS).setParallelism(settings.parallelism());

    final DataStream<ProvenanceTupleContainer<CarLocalResult>> pedestriansSinkStream =
        pedestrians
            .join(pedestrians)
            .where(PF.key(
                (KeySelector<AnnotationTuple, String>) left -> {
                  if (left.type.equals("ring_front_left")) {
                    return "ring_front_right" + left.key;
                  } else {
                    return "ring_front_left" + left.key;
                  }
                }, String.class))
            .equalTo(PF.key(right -> right.type + right.key, String.class))
            .window(SlidingEventTimeWindows
                .of(PEDESTRIAN_PASSING_WINDOW_SIZE, PEDESTRIAN_PASSING_WINDOW_ADVANCE))
            .evictor(
                PickedEvictor.of(JOIN, PEDESTRIAN_PASSING_WINDOW_ADVANCE, serializer,
                    selectedPredicate,
                    settings, queryInfo))
            .with(PF.join(new CarLocalPedestriansPredicate()))
            .setParallelism(settings.parallelism());
    final DataStream<ProvenanceTupleContainer<CarLocalResult>> sinkStream =
        cyclistsSinkStream.union(pedestriansSinkStream);
    PickedProvenanceQuerySink.connect(sinkStream, t -> t.getStimulus(), selectedPredicate,
        settings, serializer);

    PickedProvenance.addLoggerQueryWithPredicate(env, settings,
        selectedPredicate,
        OPERATORS,
        queryInfo);
    env.execute(CarLocalMerged.class.getSimpleName());
  }
}
