package io.palyvos.provenance.usecases.smartgrid.provenance2;


import static io.palyvos.provenance.usecases.smartgrid.noprovenance.SmartGridAnomaly.ANOMALY_HOUSEHOLD_AGGR_WINDOW_SIZE;
import static io.palyvos.provenance.usecases.smartgrid.noprovenance.SmartGridAnomaly.ANOMALY_JOIN_WINDOW_SIZE;
import static io.palyvos.provenance.usecases.smartgrid.noprovenance.SmartGridAnomaly.ANOMALY_LIMIT;
import static io.palyvos.provenance.usecases.smartgrid.noprovenance.SmartGridAnomaly.ANOMALY_PLUG_AGGR_WINDOW_SIZE;
import static io.palyvos.provenance.usecases.smartgrid.noprovenance.SmartGridAnomaly.intervalEndsFilter;
import static io.palyvos.provenance.usecases.smartgrid.noprovenance.SmartGridAnomaly.plugIntervalFilter;

import io.palyvos.provenance.ananke.functions.ProvenanceFunctionFactory;
import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import io.palyvos.provenance.missing.PickedProvenance;
import io.palyvos.provenance.missing.operator.PickedEvictor;
import io.palyvos.provenance.missing.operator.PickedProvenanceQuerySink;
import io.palyvos.provenance.missing.operator.PickedStreamFilter;
import io.palyvos.provenance.missing.predicate.JoinPredicates;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.predicate.QueryGraphInfo;
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
import java.util.Arrays;
import java.util.List;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;

public class SmartGridAnomaly {

  private static final String QUERY_INFO_YAML = "src/main/resources/SmartGridAnomaly-DAG.yaml";
  private static final int SOURCE_COMPONENT_INDEX = 0;
  private static final String JOIN_NAME = "JOIN";
  private static final String ANOMALY_LIMIT_FILTER_NAME = "ANOMALY-LIMIT-FILTER";
  private static final String INTERVAL_ENDS_FILTER = "INTERVAL-ENDS-FILTER";
  public static final List<String> OPERATORS = Arrays.asList(JoinPredicates.one(JOIN_NAME),
      JoinPredicates.two(JOIN_NAME),
      ANOMALY_LIMIT_FILTER_NAME, INTERVAL_ENDS_FILTER);

  public static void main(String[] args) throws Exception {

    ExperimentSettings settings = ExperimentSettings.newInstance(args);
    QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(QUERY_INFO_YAML);
    queryInfo.registerTransform("ROUND", v -> Math.round((double) v));

    final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

    env.setMaxParallelism(settings.maxParallelism());
    env.getConfig().enableObjectReuse();
    PickedProvenance.initKafka();
    FlinkSerializerActivator.PROVENANCE_TRANSPARENT.activate(env, settings)
        .register(SmartGridTuple.class, new SmartGridTuple.KryoSerializer())
        .register(HouseholdUsageTuple.class, new HouseholdUsageTuple.KryoSerializer())
        .register(PlugUsageTuple.class, new PlugUsageTuple.KryoSerializer())
        .register(AnomalyResultTuple.class, new AnomalyResultTuple.KryoSerializer())
        .registerOperators(OPERATORS);

    final ProvenanceFunctionFactory PF = settings.provenanceFunctionFactory(true);
    final Predicate selectedPredicate = settings.initPredicate(SmartGridAnomalyPredicates.INSTANCE);

    final KryoSerializer<ProvenanceTupleContainer> tupleContainerSerializer =
        PickedProvenance.newSerializer(env);

    final SingleOutputStreamOperator<ProvenanceTupleContainer<SmartGridTuple>> sourceStream =
        env.addSource(new SmartGridFileSource(settings))
            .assignTimestampsAndWatermarks(WatermarkStrategy.forMonotonousTimestamps())
            .filter(t -> t.isLoad)
            .map(PF.initMap(t -> t.timestamp, t -> t.stimulus))
            .map(settings.provenanceActivator()
                .uidAssigner(SOURCE_COMPONENT_INDEX, settings.maxParallelism()))
            .returns(new TypeHint<ProvenanceTupleContainer<SmartGridTuple>>() {
            });

    final SingleOutputStreamOperator<ProvenanceTupleContainer<PlugUsageTuple>> intervalEnds = sourceStream
        .filter(PF.filter(plugIntervalFilter()))
        .keyBy(PF.key(s -> s.plugKey(), new TypeHint<Tuple3<Integer, Integer, Integer>>() {
        }.getTypeInfo()))
        .window(TumblingEventTimeWindows.of(ANOMALY_PLUG_AGGR_WINDOW_SIZE))
        .aggregate(PF.aggregate(new AveragePlugUsageFunction()));

    final DataStream<ProvenanceTupleContainer<PlugUsageTuple>> cleanedIntervalEnds =
        PickedStreamFilter.connect(intervalEnds, PF.filter(intervalEndsFilter()),
            INTERVAL_ENDS_FILTER, tupleContainerSerializer, selectedPredicate, queryInfo, settings);

    final DataStream<ProvenanceTupleContainer<AnomalyResultTuple>> joined = sourceStream
        .keyBy(PF.key(s -> s.householdKey()), new TypeHint<Tuple2<Integer, Integer>>() {
        }.getTypeInfo())
        .window(TumblingEventTimeWindows.of(ANOMALY_HOUSEHOLD_AGGR_WINDOW_SIZE))
        .aggregate(PF.aggregate(new AverageHouseholdUsageFunction()))
        .setParallelism(settings.parallelism())
        .join(cleanedIntervalEnds)
        .where(PF.key(t -> t.householdKey(), new TypeHint<Tuple2<Integer, Integer>>() {
        }.getTypeInfo()))
        .equalTo(PF.key(t -> t.householdKey(), new TypeHint<Tuple2<Integer, Integer>>() {
        }.getTypeInfo()))
        .window(TumblingEventTimeWindows.of(ANOMALY_JOIN_WINDOW_SIZE))
        .evictor(
            PickedEvictor.of(JOIN_NAME, ANOMALY_JOIN_WINDOW_SIZE, tupleContainerSerializer,
                selectedPredicate, settings, queryInfo))
        .with(PF.join(new NewAnomalyJoinFunction()))
        .setParallelism(settings.parallelism());

    final SingleOutputStreamOperator<ProvenanceTupleContainer<AnomalyResultTuple>> sinkStream =
        PickedStreamFilter.connect(joined, PF.filter(t -> t.difference > ANOMALY_LIMIT),
                ANOMALY_LIMIT_FILTER_NAME, tupleContainerSerializer, selectedPredicate, queryInfo,
                settings)
            .setParallelism(settings.parallelism());
    PickedProvenanceQuerySink.connect(sinkStream, t -> t.getStimulus(), selectedPredicate,
        settings, tupleContainerSerializer, settings.parallelism(), true);

    PickedProvenance.addLoggerQueryWithPredicate(env, settings,
        selectedPredicate,
        OPERATORS, queryInfo);
    env.execute(SmartGridAnomaly.class.getSimpleName());

  }

}
