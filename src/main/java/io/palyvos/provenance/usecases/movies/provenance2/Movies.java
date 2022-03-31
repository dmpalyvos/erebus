package io.palyvos.provenance.usecases.movies.provenance2;

import static io.palyvos.provenance.usecases.movies.noprovenance.Movies.MOVIES_OLD_WINDOW_SIZE;
import static io.palyvos.provenance.usecases.movies.noprovenance.Movies.ratingsCountFilter;
import static io.palyvos.provenance.usecases.movies.noprovenance.Movies.yearFilter;
import static io.palyvos.provenance.usecases.movies.noprovenance.Movies.ratingValueFilter;

import io.palyvos.provenance.ananke.functions.ProvenanceFunctionFactory;
import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import io.palyvos.provenance.missing.PickedProvenance;
import io.palyvos.provenance.missing.operator.PickedEvictor;
import io.palyvos.provenance.missing.operator.PickedProvenanceQuerySink;
import io.palyvos.provenance.missing.operator.PickedStreamFilter;
import io.palyvos.provenance.missing.predicate.JoinPredicates;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.predicate.QueryGraphInfo;
import io.palyvos.provenance.usecases.movies.AverageUserRating;
import io.palyvos.provenance.usecases.movies.AverageUserRatingFunction;
import io.palyvos.provenance.usecases.movies.MovieRating;
import io.palyvos.provenance.usecases.movies.MovieRatingSource;
import io.palyvos.provenance.usecases.movies.MoviesJoinFunction;
import io.palyvos.provenance.usecases.movies.MovieResult;
import io.palyvos.provenance.util.ExperimentSettings;
import io.palyvos.provenance.util.FlinkSerializerActivator;
import java.util.Arrays;
import java.util.List;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;

public class Movies {

  public static final String QUERY_INFO_YAML = String.format("src/main/resources/%s-DAG.yaml",
      Movies.class.getSimpleName());
  public static final String USER_RATING_JOIN = "USER-RATING-JOIN";
  public static final String YEAR_FILTER = "YEAR-FILTER";
  public static final String RATINGS_COUNT_FILTER = "RATINGS-COUNT-FILTER";
  public static final String RATING_VALUE_FILTER = "RATING-VALUE-FILTER";
  public static final List<String> OPERATORS = Arrays.asList(JoinPredicates.one(USER_RATING_JOIN),
      JoinPredicates.two(USER_RATING_JOIN), RATINGS_COUNT_FILTER, YEAR_FILTER,
      RATING_VALUE_FILTER);

  public static final int SOURCE_COMPONENT_INDEX = 0;

  public static void main(String[] args) throws Exception {

    ExperimentSettings settings = ExperimentSettings.newInstance(args);
    QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(QUERY_INFO_YAML);

    final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

    env.setMaxParallelism(settings.maxParallelism());
    env.getConfig().enableObjectReuse();
    PickedProvenance.initKafka();
    FlinkSerializerActivator.PROVENANCE_TRANSPARENT.activate(env, settings)
        .register(MovieRating.class, new MovieRating.KryoSerializer())
        .register(AverageUserRating.class, new AverageUserRating.KryoSerializer())
        .register(MovieResult.class, new MovieResult.KryoSerializer())
        .registerOperators(OPERATORS);

    final ProvenanceFunctionFactory PF = settings.provenanceFunctionFactory(true);
    final Predicate selectedPredicate = settings.initPredicate(MoviesPredicates.INSTANCE);

    final KryoSerializer<ProvenanceTupleContainer> serializer = PickedProvenance.newSerializer(env);

    final SingleOutputStreamOperator<ProvenanceTupleContainer<MovieRating>> inputStream = env.addSource(
            new MovieRatingSource(settings))
        .assignTimestampsAndWatermarks(WatermarkStrategy.forMonotonousTimestamps())
        .map(PF.initMap(t -> t.timestamp, t -> t.stimulus))
        .map(settings.provenanceActivator()
            .uidAssigner(SOURCE_COMPONENT_INDEX, settings.maxParallelism()))
        .returns(new TypeHint<ProvenanceTupleContainer<MovieRating>>() {
        });
    final SingleOutputStreamOperator<ProvenanceTupleContainer<AverageUserRating>> averageUserRatings = inputStream
        .keyBy(PF.key(r -> r.userId, Integer.class))
        .window(TumblingEventTimeWindows.of(MOVIES_OLD_WINDOW_SIZE))
        .aggregate(PF.aggregate(new AverageUserRatingFunction()))
        .setParallelism(settings.parallelism());
    final SingleOutputStreamOperator<ProvenanceTupleContainer<AverageUserRating>> maxUserRatings =
        PickedStreamFilter.connect(averageUserRatings,
            PF.filter(ratingsCountFilter()), RATINGS_COUNT_FILTER,
            serializer, selectedPredicate, queryInfo, settings)
        .setParallelism(settings.parallelism());

    final DataStream<ProvenanceTupleContainer<MovieResult>> joined = inputStream.join(
            maxUserRatings)
        .where(PF.key(r -> r.userId, Integer.class))
        .equalTo(PF.key(r -> r.userId, Integer.class))
        .window(TumblingEventTimeWindows.of(MOVIES_OLD_WINDOW_SIZE))
        .evictor(
            PickedEvictor.of(USER_RATING_JOIN, MOVIES_OLD_WINDOW_SIZE, serializer,
                selectedPredicate, settings, queryInfo))
        .with(PF.join(new MoviesJoinFunction()))
        .setParallelism(settings.parallelism());
    final SingleOutputStreamOperator<ProvenanceTupleContainer<MovieResult>> maxYear =
        PickedStreamFilter.connect(joined, PF.filter(yearFilter()), YEAR_FILTER,
            serializer, selectedPredicate, queryInfo, settings).setParallelism(
            settings.parallelism());
    final SingleOutputStreamOperator<ProvenanceTupleContainer<MovieResult>> minMovieRating =
        PickedStreamFilter.connect(maxYear, PF.filter(ratingValueFilter()),
            RATING_VALUE_FILTER, serializer, selectedPredicate, queryInfo,
            settings).setParallelism(settings.parallelism());
    PickedProvenanceQuerySink.connect(minMovieRating, t -> t.getStimulus(), selectedPredicate,
        settings, serializer, settings.parallelism(), true);

    PickedProvenance.addLoggerQueryWithPredicate(env, settings, selectedPredicate,
        OPERATORS, queryInfo);
    env.execute(Movies.class.getSimpleName());
  }

}
