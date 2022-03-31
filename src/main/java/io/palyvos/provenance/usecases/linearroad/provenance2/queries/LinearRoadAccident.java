package io.palyvos.provenance.usecases.linearroad.provenance2.queries;

import static io.palyvos.provenance.usecases.linearroad.LinearRoadConstants.ACCIDENT_WINDOW_SIZE;
import static io.palyvos.provenance.usecases.linearroad.LinearRoadConstants.ACCIDENT_WINDOW_SLIDE;
import static io.palyvos.provenance.usecases.linearroad.LinearRoadConstants.STOPPED_VEHICLE_WINDOW_SIZE;
import static io.palyvos.provenance.usecases.linearroad.LinearRoadConstants.STOPPED_VEHICLE_WINDOW_SLIDE;
import static io.palyvos.provenance.usecases.linearroad.noprovenance.queries.LinearRoadAccident.linearRoadKafkaSource;

import io.palyvos.provenance.ananke.functions.ProvenanceFunctionFactory;
import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import io.palyvos.provenance.missing.PickedProvenance;
import io.palyvos.provenance.missing.operator.PickedProvenanceQuerySink;
import io.palyvos.provenance.missing.operator.PickedStreamFilter;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.predicate.QueryGraphInfo;
import io.palyvos.provenance.usecases.linearroad.noprovenance.AccidentTuple;
import io.palyvos.provenance.usecases.linearroad.noprovenance.LinearRoadAccidentAggregate;
import io.palyvos.provenance.usecases.linearroad.noprovenance.LinearRoadInputTuple;
import io.palyvos.provenance.usecases.linearroad.noprovenance.LinearRoadVehicleAggregate;
import io.palyvos.provenance.usecases.linearroad.noprovenance.VehicleTuple;
import io.palyvos.provenance.util.ExperimentSettings;
import io.palyvos.provenance.util.FlinkSerializerActivator;
import io.palyvos.provenance.util.ThroughputLoggingFilter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;

public class LinearRoadAccident {


  public static final String FILTER_ZERO_SPEED = "FILTER-ZERO-SPEED";
  public static final String FILTER_STOPPED = "FILTER-STOPPED";
  public static final String FILTER_ACCIDENT = "FILTER-ACCIDENT";

  public static final List<String> OPERATORS = Arrays.asList(FILTER_ZERO_SPEED,
      FILTER_STOPPED, FILTER_ACCIDENT);

  public static void main(String[] args) throws Exception {
    ExperimentSettings settings = ExperimentSettings.newInstance(args);
    QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(
            "src/main/resources/LinearRoadAccident-DAG.yaml")
        .registerTransform("TOSET", Collections::singleton);

    final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

    env.setMaxParallelism(settings.maxParallelism());
    env.getConfig().enableObjectReuse();
    PickedProvenance.initKafka();
    FlinkSerializerActivator.PROVENANCE_TRANSPARENT.activate(env, settings)
        .registerOperators(OPERATORS)
        .register(LinearRoadInputTuple.class, new LinearRoadInputTuple.KryoSerializer());

    env.registerType(AccidentTuple.class);
    env.registerType(VehicleTuple.class);

    final ProvenanceFunctionFactory PF = settings.provenanceFunctionFactory(true);
    final Predicate selectedPredicate = settings.initPredicate(
        LinearRoadAccidentPredicates.INSTANCE);

    final KryoSerializer<ProvenanceTupleContainer> serializer = PickedProvenance.newSerializer(env);


    final int sourceTaskParallelism = settings.kafkaPartitions();
    SingleOutputStreamOperator<ProvenanceTupleContainer<LinearRoadInputTuple>> sourceStream =
        env.fromSource(linearRoadKafkaSource(settings),
                WatermarkStrategy.<LinearRoadInputTuple>forMonotonousTimestamps()
                    .withTimestampAssigner((element, recordTimestamp) -> element.getTimestamp()),
                "SOURCE")
            .setParallelism(sourceTaskParallelism)
            .filter(new ThroughputLoggingFilter<>("SOURCE", settings))
            .setParallelism(sourceTaskParallelism)
            .map(PF.initMap(t -> t.getTimestamp(), t -> t.getStimulus()))
            .setParallelism(sourceTaskParallelism)
            .map(settings.provenanceActivator().uidAssigner(0, settings.maxParallelism()))
            .setParallelism(sourceTaskParallelism)
            .returns(new TypeHint<ProvenanceTupleContainer<LinearRoadInputTuple>>() {
            })
            .filter(PF.filter(t -> t.getType() == 0))
            .setParallelism(sourceTaskParallelism);
    final DataStream<ProvenanceTupleContainer<VehicleTuple>> zeroSpeed = PickedStreamFilter.connect(
            sourceStream, PF.filter(t -> t.getSpeed() == 0),
            FILTER_ZERO_SPEED, serializer, selectedPredicate, queryInfo, settings)
        .setParallelism(sourceTaskParallelism)
        .keyBy(
            PF.key(t -> t.vid, Long.class))
        .window(SlidingEventTimeWindows.of(STOPPED_VEHICLE_WINDOW_SIZE,
            STOPPED_VEHICLE_WINDOW_SLIDE))
        .aggregate(PF.aggregate(new LinearRoadVehicleAggregate()));
    final DataStream<ProvenanceTupleContainer<AccidentTuple>> zeroSpeedSamePos =
        PickedStreamFilter.connect(
                zeroSpeed,
                PF.filter(t -> t.getReports() == 4 && t.isUniquePosition()), FILTER_STOPPED, serializer,
                selectedPredicate,
                queryInfo, settings)
            .keyBy(PF.key(t -> t.getLatestPos(), Integer.class))
            .window(SlidingEventTimeWindows.of(ACCIDENT_WINDOW_SIZE,
                ACCIDENT_WINDOW_SLIDE))
            .aggregate(PF.aggregate(new LinearRoadAccidentAggregate()));

    final DataStream<ProvenanceTupleContainer<AccidentTuple>> sinkStream = PickedStreamFilter.connect(
        zeroSpeedSamePos,
        PF.filter(t -> t.count > 1), FILTER_ACCIDENT, serializer, selectedPredicate,
        queryInfo, settings);

    PickedProvenanceQuerySink.connect(sinkStream, t -> t.getStimulus(), selectedPredicate,
        settings, serializer, 1, true);

    PickedProvenance.addLoggerQueryWithPredicate(env, settings, selectedPredicate,
        OPERATORS, queryInfo);
    env.execute(LinearRoadAccident.class.getSimpleName());

  }

}
