package io.palyvos.provenance.missing.integration;

import static io.palyvos.provenance.missing.PickedProvenance.QUERY_SINK_NAME;

import io.palyvos.provenance.missing.predicate.OneVariableCondition;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.predicate.Predicate.PredicateStrategy;
import io.palyvos.provenance.missing.predicate.QueryGraphInfo;
import io.palyvos.provenance.missing.predicate.QueryGraphInfo.Builder;
import io.palyvos.provenance.missing.predicate.ReflectionVariable;
import io.palyvos.provenance.missing.predicate.TimestampCondition;
import io.palyvos.provenance.usecases.cars.local.provenance2.queries.CarLocalMerged;
import io.palyvos.provenance.usecases.cars.local.provenance2.queries.CarLocalPredicates;
import io.palyvos.provenance.usecases.linearroad.provenance2.queries.LinearRoadAccident;
import io.palyvos.provenance.usecases.linearroad.provenance2.queries.LinearRoadAccidentPredicates;
import io.palyvos.provenance.usecases.movies.provenance2.Movies;
import io.palyvos.provenance.usecases.movies.provenance2.MoviesPredicates;
import io.palyvos.provenance.usecases.smartgrid.provenance2.SmartGridAnomaly;
import io.palyvos.provenance.usecases.smartgrid.provenance2.SmartGridAnomalyPredicates;
import io.palyvos.provenance.usecases.synthetic.SyntheticCondition;
import io.palyvos.provenance.usecases.synthetic.provenance.queries.SyntheticPickedQuery;
import io.palyvos.provenance.util.PredicateHolder;
import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.Validate;
import org.testng.Assert;
import org.testng.annotations.Test;

public class PredicateIntegrationTests {

  @Test
  public void linearRoadAccidentTransforms() throws FileNotFoundException {
    QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(
        "src/main/resources/LinearRoadAccident-DAG.yaml").registerTransform("TOSET", v -> v);
    printVariableTransformsAndCheckTimeTransforms(queryInfo, LinearRoadAccident.OPERATORS,
        LinearRoadAccidentPredicates.INSTANCE);
  }

  @Test
  public void moviesTransforms() throws FileNotFoundException {
    final QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(
        "src/main/resources/Movies-DAG.yaml");
    printVariableTransformsAndCheckTimeTransforms(queryInfo, Movies.OPERATORS,
        MoviesPredicates.INSTANCE);
  }

  @Test
  public void smartGridAnomalyTransforms() throws FileNotFoundException {
    final QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(
            "src/main/resources/SmartGridAnomaly-DAG.yaml")
        .registerTransform("ROUND", v -> Math.round((double) v));
    printVariableTransformsAndCheckTimeTransforms(queryInfo, SmartGridAnomaly.OPERATORS,
        SmartGridAnomalyPredicates.INSTANCE);
  }

  private void printVariableTransformsAndCheckTimeTransforms(QueryGraphInfo queryInfo,
      Collection<String> operators, PredicateHolder predicateHolder) {
    for (String operator : operators) {
      System.out.println("\n" + operator);
      System.out.println(queryInfo.sinkToOperatorVariables(operator, QUERY_SINK_NAME));
      for (String predicateId : predicateHolder.predicates().keySet()) {
        Predicate predicate = predicateHolder.get(predicateId);
        final Predicate transformed = predicate.transformed(operator, QUERY_SINK_NAME, queryInfo);
        // Verify user-defined Q{X} predicates are satisfiable, but not the synthetic ones
        Validate.isTrue(transformed.isSatisfiable() || !predicateId.startsWith("Q"),
            "%s: Failed to transform predicate %s for operator %s. Transformed: %s",
            predicateHolder.getClass().getSimpleName(), predicateId,
            operator, transformed);
        System.out.println("Predicate " + predicateId);
        System.out.println(transformed.description());
      }
    }
    System.out.println();
  }

  @Test
  public void carLocalMergedTransforms() throws FileNotFoundException {
    final QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(
        "src/main/resources/CarLocalMerged-DAG.yaml");
    printVariableTransformsAndCheckTimeTransforms(queryInfo, CarLocalMerged.OPERATORS,
        CarLocalPredicates.INSTANCE);
  }


  @Test
  public void syntheticTransforms() {
    final QueryGraphInfo queryInfo = SyntheticPickedQuery.getQueryGraphInfo();
    final Predicate predicate = SyntheticCondition.newPredicate(1, 0);
    final Predicate transformed = predicate.transformed(SyntheticPickedQuery.FILTER_NAME,
        QUERY_SINK_NAME, queryInfo);
    Validate.isTrue(transformed.isSatisfiable(),
        "Failed to transform synthetic predicate for operator %s",
        SyntheticPickedQuery.FILTER_NAME);
  }

  @Test
  public void printPredicates() {
    final PredicateHolder[] holders = {SmartGridAnomalyPredicates.INSTANCE,
        MoviesPredicates.INSTANCE, LinearRoadAccidentPredicates.INSTANCE,
        CarLocalPredicates.INSTANCE};
    for (PredicateHolder predicateHolder : holders) {
      for (String predicateId : predicateHolder.predicates().keySet()) {
        if (!predicateId.startsWith("Q")) {
          continue;
        }
        System.out.format("%s/%s: %s\n", predicateHolder.getClass().getSimpleName(), predicateId,
            predicateHolder.get(predicateId).description());
      }
      System.out.println("------------------------");
    }
  }

  @Test
  public void smartGridAnomalyBoundaryExampleOne()
      throws FileNotFoundException {
    final QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(
            "src/main/resources/SmartGridAnomaly-DAG.yaml")
        .registerTransform("ROUND", v -> Math.round((double) v));
    final Predicate p = SmartGridAnomalyPredicates.INSTANCE.get("Q1");
    final String operator = "PLUG-INTERVAL-FILTER";
    final Predicate transformed = p.transformed(operator, QUERY_SINK_NAME, queryInfo);
    System.out.println(transformed.description());
  }

  @Test
  public void smartGridAnomalyBoundaryExampleTwo()
      throws FileNotFoundException {
    final QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(
            "src/main/resources/SmartGridAnomaly-DAG.yaml")
        .registerTransform("ROUND", v -> Math.round((double) v));
    final long intervalStart = SmartGridAnomalyPredicates.INSTANCE.DATASET_START_TIMESTAMP - 1;
    final long intervalEnd =
        SmartGridAnomalyPredicates.INSTANCE.DATASET_START_TIMESTAMP + TimeUnit.MINUTES.toMillis(
            140);
    final String operator = "INTERVAL-ENDS-FILTER";
    final OptionalLong newStart = queryInfo.legacyTransformIntervalStartFromSink(QUERY_SINK_NAME,
        operator, intervalStart, intervalEnd);
    final OptionalLong newEnd = queryInfo.legacyTransformIntervalEndFromSink(QUERY_SINK_NAME,
        operator, intervalStart, intervalEnd);
    System.out.format("I' = [%d, %d]\n", newStart.getAsLong(), newEnd.getAsLong());
    System.out.format("I_OLD' = [%d, %d]\n", intervalStart - 15_000, intervalEnd);
  }


  @Test
  public void boundariesDifference() {
    long startTime = System.nanoTime();
    final long maxWindow = 2 << 10;
    QueryGraphInfo.Builder builder = new Builder();
    final long intervalStart = (maxWindow << 2) + 1;
    final long intervalEnd = (intervalStart << 1);
    long basicStart = intervalStart;
    final long basicEnd = intervalEnd;
    String sink = "";
    String source = "";
    long windowSum = 0;
    System.out.println("Max window " + maxWindow);
    for (long w = maxWindow; w >= 1; w /= 2) {
      final long WS = w;
      final String current = "OP" + WS;
      if (w == maxWindow) {
        sink = current;
      }
      final String prev = "OP" + WS * 2;
      builder.addDownstream(current, prev);
      builder.setWindowSize(current, WS);
      basicStart -= WS;
      windowSum += WS;
      source = current;
    }
    QueryGraphInfo info = builder.build();
    final OptionalLong start = info.legacyTransformIntervalStartFromSink(sink, source,
        intervalStart,
        intervalEnd);
    final OptionalLong end = info.legacyTransformIntervalEndFromSink(sink, source, intervalStart,
        intervalEnd);
    System.out.format("I = [%d, %d]\n", intervalStart, intervalEnd);
    System.out.println("Window sum: " + windowSum);
    System.out.format("I' = [%d, %d]\n", start.getAsLong(), end.getAsLong());
    System.out.format("Basic I' = [%d, %d]\n", basicStart, basicEnd);
    System.out.format("Differences (relative to sum(WS)) = [%f, %f]\n",
        (start.getAsLong() - basicStart) / (double) windowSum,
        (basicEnd - end.getAsLong()) / (double) windowSum);
    System.out.println();
    System.out.println(1e9 / (double) (System.nanoTime() - startTime));

  }

  @Test
  public void testRenameTwoPaths() {
    QueryGraphInfo queryGraphInfo = new QueryGraphInfo.Builder()
        .addDownstream("O", "M1")
        .addDownstream("O", "M2")
        .addDownstream("M1", "SINK")
        .addDownstream("M2", "SINK")
        .addRenaming("O", "A", "A")
        .addRenaming("O", "B", "B")
        .addRenaming("M1", "A", "C")
        .setWindowSize("M2", 33)
        .addTransform("M1", "A", "DOUBLE")
        .addRenaming("M1", "B", "D")
        .addTransform("M1", "B", "DOUBLE")
        .addRenaming("M2", "A", "C")
        .addTransform("M2", "A", "TRIPLE")
        .addRenaming("M2", "B", "D")
        .addTransform("M2", "B", "TRIPLE")
        .build();
    queryGraphInfo.registerTransform("DOUBLE", v -> (int) v * 2);
    queryGraphInfo.registerTransform("TRIPLE", v -> (int) v * 3);
    Predicate predicate = Predicate.of(PredicateStrategy.AND,
        TimestampCondition.greaterEqual(100),
        new OneVariableCondition(ReflectionVariable.fromField("C"), v -> v.asInt() == 6),
        new OneVariableCondition(ReflectionVariable.fromField("D"), v -> v.asInt() == 12)
    ).transformed("O", "SINK", queryGraphInfo);
    System.out.println(predicate.description());
    TwoVarTestTuple compatible1 = new TwoVarTestTuple(3, 6);
    TwoVarTestTuple compatible2 = new TwoVarTestTuple(2, 4);
    TwoVarTestTuple notCompatible1 = new TwoVarTestTuple(3, 4);
    Assert.assertTrue(predicate.evaluate(compatible1, 123), "Failed to find compatible");
    Assert.assertFalse(predicate.evaluate(compatible1, 99), "Correct attributes but wrong ts");
    Assert.assertTrue(predicate.evaluate(compatible2, 99), "Failed to find compatible");
    Assert.assertFalse(predicate.evaluate(notCompatible1, 123),
        "False positive due to path algorithm");
  }

}
