package io.palyvos.provenance.util;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.util.ComponentCategory;
import io.palyvos.provenance.missing.util.KafkaPredicateUtil;
import io.palyvos.provenance.usecases.cars.local.provenance2.queries.CarLocalPredicates;
import io.palyvos.provenance.usecases.linearroad.provenance2.queries.LinearRoadAccidentPredicates;
import io.palyvos.provenance.usecases.movies.provenance2.MoviesPredicates;
import io.palyvos.provenance.usecases.smartgrid.provenance2.SmartGridAnomalyPredicates;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.commons.lang3.Validate;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PredicateSender {

  private static final Map<String, PredicateHolder> PREDICATES = new HashMap<>();
  private static final Logger LOG = LoggerFactory.getLogger(PredicateSender.class);
  private static final long TIMEOUT_SECONDS = 10;

  static {
    PREDICATES.put(SmartGridAnomalyPredicates.INSTANCE.query().getSimpleName(),
        SmartGridAnomalyPredicates.INSTANCE);
    PREDICATES.put(MoviesPredicates.INSTANCE.query().getSimpleName(),
        MoviesPredicates.INSTANCE);
    PREDICATES.put(CarLocalPredicates.INSTANCE.query().getSimpleName(),
        CarLocalPredicates.INSTANCE);
    PREDICATES.put(LinearRoadAccidentPredicates.INSTANCE.query().getSimpleName(),
        LinearRoadAccidentPredicates.INSTANCE);
  }

  private static class ProgramSettings {

    @Parameter(names = "--query", description = "Query class the predicate refers to", required = true)
    private String query;

    @Parameter(names = "--predicate", description = "Unique name of the predicate used to answer a why-not question")
    private String predicate;

    @Parameter(names = "--predicateDelay", description = "(Artificial) delay before the predicate is activated (in processing time seconds)")
    private long predicateDelay;

    @Parameter(names = "--kafkaHost", description = "Kafka host in format host:port")
    private String kafkaHost;

    static ProgramSettings newInstance(String[] args) {
      ProgramSettings programSettings = new ProgramSettings();
      JCommander.newBuilder().addObject(programSettings).build().parse(args);
      return programSettings;
    }

    Predicate predicate() {
      final PredicateHolder predicateHolder = PREDICATES.get(query);
      Validate.notNull(predicateHolder, "No predicates for query %s. Available predicates: %s",
          query, PREDICATES);
      return predicateHolder.get(this.predicate);
    }

    long predicateDelayMillis() {
      Validate.isTrue(predicateDelay >= 0, "Negative delay not allowed!");
      return TimeUnit.SECONDS.toMillis(predicateDelay);
    }

    String kafkaHost() {
      Validate.notBlank(kafkaHost, "Blank kafka host");
      return kafkaHost;
    }

  }

  public static void main(String[] args)
      throws InterruptedException, ExecutionException, TimeoutException {
    ProgramSettings settings = ProgramSettings.newInstance(args);
    Predicate predicate = settings.predicate();
    LOG.info("Delaying predicate for {} seconds...", settings.predicateDelay);
    Thread.sleep(settings.predicateDelayMillis());
    LOG.info("Sending predicate to Kafka...");
    final Future<RecordMetadata> recordMetadataFuture = KafkaPredicateUtil.sendPredicate(
        ComponentCategory.FUTURE_CHECKER, predicate, settings.kafkaHost);
    final RecordMetadata recordMetadata = recordMetadataFuture.get(TIMEOUT_SECONDS,
        TimeUnit.SECONDS);
    LOG.info("Predicate sent to kafka: {}\n{}", recordMetadata, predicate);
  }

}
