package io.palyvos.provenance.usecases.movies.noprovenance;

import io.palyvos.provenance.usecases.movies.AverageUserRating;
import io.palyvos.provenance.usecases.movies.AverageUserRatingFunction;
import io.palyvos.provenance.usecases.movies.MovieRating;
import io.palyvos.provenance.usecases.movies.MovieRatingSource;
import io.palyvos.provenance.usecases.movies.MoviesJoinFunction;
import io.palyvos.provenance.usecases.movies.MovieResult;
import io.palyvos.provenance.util.ExperimentSettings;
import io.palyvos.provenance.util.FlinkSerializerActivator;
import io.palyvos.provenance.util.LatencyLoggingSink;
import java.util.concurrent.TimeUnit;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

public class Movies {

  public static final Time MOVIES_OLD_WINDOW_SIZE = Time.of(1, TimeUnit.DAYS);

  public static void main(String[] args) throws Exception {

    ExperimentSettings settings = ExperimentSettings.newInstance(args);

    final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
    env.getConfig().enableObjectReuse();

    FlinkSerializerActivator.NOPROVENANCE.activate(env, settings)
        .register(MovieRating.class, new MovieRating.KryoSerializer())
        .register(AverageUserRating.class, new AverageUserRating.KryoSerializer())
        .register(MovieResult.class, new MovieResult.KryoSerializer());

    final SingleOutputStreamOperator<MovieRating> inputStream = env.addSource(
            new MovieRatingSource(settings))
        .assignTimestampsAndWatermarks(WatermarkStrategy.forMonotonousTimestamps());
    final SingleOutputStreamOperator<AverageUserRating> averageUserRatings = inputStream
        .keyBy(r -> r.userId)
        .window(TumblingEventTimeWindows.of(MOVIES_OLD_WINDOW_SIZE))
        .aggregate(new AverageUserRatingFunction())
        .setParallelism(settings.parallelism())
        .filter(ratingsCountFilter())
        .setParallelism(settings.parallelism());
    inputStream.join(averageUserRatings)
        .where(r -> r.userId)
        .equalTo(r -> r.userId)
        .window(TumblingEventTimeWindows.of(MOVIES_OLD_WINDOW_SIZE))
        .with(new MoviesJoinFunction())
        .setParallelism(settings.parallelism())
        .filter(yearFilter())
        .setParallelism(settings.parallelism())
        .filter(ratingValueFilter())
        .setParallelism(settings.parallelism())
        .addSink(LatencyLoggingSink.newInstance(t -> t.stimulus, settings))
        .setParallelism(settings.parallelism());

    env.execute(Movies.class.getSimpleName());
  }

  public static FilterFunction<AverageUserRating> ratingsCountFilter() {
    return t -> (t.nRatings > 3 && t.nRatings < 100);
  }

  public static FilterFunction<MovieResult> ratingValueFilter() {
    return t -> t.rating > t.averageRating;
  }

  public static FilterFunction<MovieResult> yearFilter() {
    return t -> t.year > 1940 && t.year < 2005;
  }

}
