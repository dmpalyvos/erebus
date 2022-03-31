package io.palyvos.provenance.usecases.synthetic.provenance.queries;

import io.palyvos.provenance.ananke.functions.ProvenanceFunctionFactory;
import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import io.palyvos.provenance.missing.PickedProvenance;
import io.palyvos.provenance.missing.operator.PickedProvenanceQuerySink;
import io.palyvos.provenance.missing.operator.PickedStreamFilter;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.predicate.QueryGraphInfo;
import io.palyvos.provenance.usecases.synthetic.SyntheticCondition;
import io.palyvos.provenance.usecases.synthetic.provenance.SimpleSyntheticProvenanceSource;
import io.palyvos.provenance.usecases.synthetic.provenance.SyntheticSinkTuple;
import io.palyvos.provenance.usecases.synthetic.provenance.SyntheticSourceTuple;
import io.palyvos.provenance.util.ExperimentSettings;
import io.palyvos.provenance.util.FlinkSerializerActivator;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RichFilterFunction;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

public class SyntheticPickedQuery {

  private static final String FILTER_NAME = "FILTER";
  public static final List<String> OPERATORS = Arrays.asList(FILTER_NAME);

  /**
   * Whole query has parallelism equal to {@link ExperimentSettings#parallelism()}
   */
  public static void main(String[] args) throws Exception {
    ExperimentSettings settings = ExperimentSettings.newInstance(args);

    final QueryGraphInfo queryInfo = new QueryGraphInfo.Builder()
        .addDownstream(FILTER_NAME, PickedProvenance.QUERY_SINK_NAME)
        .build();
    final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
    env.getConfig().enableObjectReuse();
    env.setMaxParallelism(settings.maxParallelism());

    env.getConfig().setAutoWatermarkInterval(settings.getWatermarkInterval());

    PickedProvenance.initKafka();
    FlinkSerializerActivator.PROVENANCE_TRANSPARENT.activate(env, settings)
        .register(SyntheticSourceTuple.class, new SyntheticSourceTuple.KryoSerializer())
        .register(SyntheticSinkTuple.class, new SyntheticSinkTuple.KryoSerializer(
            settings.genealogDataSerializer()))
        .registerOperators(OPERATORS);

    final ProvenanceFunctionFactory PF = settings.provenanceFunctionFactory(true);
    final Predicate selectedPredicate = SyntheticCondition.newPredicate(
        settings.syntheticPredicateSelectivity(), settings.syntheticPredicateEvaluationTime());

    final KryoSerializer<ProvenanceTupleContainer> serializer = PickedProvenance.newSerializer(env);
    final double syntheticFilterSelectivity = 1 - settings.syntheticFilterDiscardRate();

    SingleOutputStreamOperator<ProvenanceTupleContainer<SyntheticSinkTuple>> sourceStream = env
        .addSource(new SimpleSyntheticProvenanceSource<>(SyntheticSinkTuple.supplier(),
            SyntheticSourceTuple.supplier(settings.syntheticTupleSize()), settings))
        .setParallelism(settings.syntheticSourceParallelism())
        .returns(SyntheticSinkTuple.class)
        .assignTimestampsAndWatermarks(WatermarkStrategy.forMonotonousTimestamps())
        .setParallelism(settings.syntheticSourceParallelism())
        .map(ProvenanceTupleContainer::fromGenealogTuple)
        .returns(new TypeHint<ProvenanceTupleContainer<SyntheticSinkTuple>>() {
        });
    if (!settings.syntheticUseEncapsulation()) {
      sourceStream = sourceStream.startNewChain().setParallelism(settings.parallelism());
    }

    SingleOutputStreamOperator<ProvenanceTupleContainer<SyntheticSinkTuple>> pickedFilter =
        PickedStreamFilter.connect(sourceStream,
                PF.filter(new SyntheticFilterFunction<>(syntheticFilterSelectivity)),
                FILTER_NAME, serializer, selectedPredicate, queryInfo, settings)
            .setParallelism(settings.parallelism());
    if (settings.syntheticUseEncapsulation()) {
      pickedFilter = pickedFilter.startNewChain();
    }
    final SingleOutputStreamOperator<ProvenanceTupleContainer<SyntheticSinkTuple>> sinkStream =
        pickedFilter
            // Send only 0.1% of the results to the sink regardless of synthetic filter selectivity
            // to remove unrelated performance effects
            .filter(new SyntheticFilterFunction<>(1e-3 / syntheticFilterSelectivity))
            .setParallelism(settings.parallelism());

    // Sink does nothing and has a false predicate to avoid skewing results
    PickedProvenanceQuerySink.connect(sinkStream,
        new SinkFunction<ProvenanceTupleContainer<SyntheticSinkTuple>>() {
        }, ProvenanceTupleContainer::getStimulus,
        Predicate.alwaysFalse(), settings, serializer, settings.parallelism(), true);

    PickedProvenance.addLoggerQueryWithPredicate(env, settings, selectedPredicate,
        OPERATORS, queryInfo);

    env.execute(SyntheticPickedQuery.class.getSimpleName());
  }

  private static class SyntheticFilterFunction<T> extends RichFilterFunction<T> {

    private final double selectivity;
    private final Random random = new Random(0);

    public SyntheticFilterFunction(double selectivity) {
      Validate.isTrue(selectivity > 0 && selectivity < 1, "Selectivity must be in (0, 1)");
      this.selectivity = selectivity;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
      super.open(parameters);
    }

    @Override
    public boolean filter(Object value) throws Exception {
      return random.nextDouble() < selectivity;
    }
  }
}
