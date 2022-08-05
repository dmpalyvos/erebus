package io.palyvos.provenance.missing.predicate;

import io.palyvos.provenance.missing.predicate.QueryGraphInfo.Builder;
import io.palyvos.provenance.missing.util.Path;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;
import org.testng.Assert;
import org.testng.annotations.Test;

public class QueryGraphInfoTest {

  private static final String QUERY_INFO_1_PATH = "src/test/resources/query_info_1.yaml";
  public static final String QUERY_INFO_2_PATH = "src/test/resources/query_info_2.yaml";
  public static final String QUERY_INFO_3_PATH = "src/test/resources/query_info_3.yaml";
  public static final TransformFunction TRANSFORM_F = v -> (int) v * (int) v;
  public static final TransformFunction TRANSFORM_K = TRANSFORM_F;
  public static final TransformFunction TRANSFORM_G = v -> (int) v - 1;
  public static final TransformFunction TRANSFORM_H = v -> (int) v / 2;

  @Test
  public void testOperatorToSinkVariablesChain() {
    QueryGraphInfo.Builder builder = new QueryGraphInfo.Builder();
    builder.addDownstream("O", "SINK");
    builder.addRenaming("O", "V1", "V2");
    builder.addRenaming("O", "V2", "V3");
    Assert.assertEquals(builder.build().operatorToSinkVariables("V1", "O"),
        Arrays.asList(RenamingHelper.testInstance("V2", "O->SINK")));
  }

  @Test
  public void testOperatorToSinkVariablesBranch() {
    QueryGraphInfo.Builder builder = new QueryGraphInfo.Builder();
    builder.addDownstream("O", "LEFT");
    builder.addDownstream("O", "RIGHT");
    builder.addDownstream("LEFT", "SINK_LEFT");
    builder.addDownstream("RIGHT", "SINK_RIGHT");
    builder.addRenaming("O", "V1", "V2");
    builder.addRenaming("LEFT", "V2", "VARSINK_LEFT");
    builder.addRenaming("RIGHT", "V2", "VARSINK_RIGHT");
    final Set<VariableRenaming> operatorToSinkVariables = builder.build()
        .operatorToSinkVariables("V1", "O", "SINK_LEFT");
    Assert.assertEquals(
        operatorToSinkVariables.stream().map(r -> r.name()).collect(Collectors.toSet()),
        new HashSet<>(Arrays.asList("VARSINK_LEFT")));
  }

  @Test
  public void testSinkToOperatorVariablesBranch() {
    QueryGraphInfo.Builder builder = new QueryGraphInfo.Builder();
    builder.addDownstream("O", "LEFT");
    builder.addDownstream("O", "RIGHT");
    builder.addDownstream("LEFT", "SINK_LEFT");
    builder.addDownstream("RIGHT", "SINK_RIGHT");
    builder.addRenaming("O", "V1", "V2");
    builder.addRenaming("LEFT", "V2", "VARSINK_LEFT");
    builder.addRenaming("RIGHT", "V2", "VARSINK_RIGHT");
    final Map<String, Set<VariableRenaming>> sinkToOperatorVariables = builder.build()
        .sinkToOperatorVariables("O", "SINK_LEFT");
    Assert.assertEquals(sinkToOperatorVariables.size(), 1, "Wrong mapping size");
    Assert.assertEquals(sinkToOperatorVariables.get("VARSINK_LEFT"),
        Arrays.asList(RenamingHelper.testInstance("V1", "SINK_LEFT->LEFT->O")));
  }

  @Test
  public void testOperatorToSinkVariablesBranchCommonOperatorVariable() {
    QueryGraphInfo.Builder builder = new QueryGraphInfo.Builder();
    builder.addDownstream("O", "LEFT");
    builder.addDownstream("O", "RIGHT");
    builder.addDownstream("LEFT", "SINK_LEFT");
    builder.addDownstream("RIGHT", "SINK_RIGHT");
    builder.addRenaming("O", "V1", "V2");
    builder.addRenaming("LEFT", "V2", "VARSINK");
    builder.addRenaming("RIGHT", "V2", "VARSINK");
    final Set<VariableRenaming> operatorToSinkVariables = builder.build()
        .operatorToSinkVariables("V1", "O", "SINK_RIGHT");
    Assert.assertEquals(
        operatorToSinkVariables.stream().map(r -> r.name()).collect(Collectors.toSet()),
        new HashSet<>(Arrays.asList("VARSINK")));
  }

  @Test
  public void testSinkToOperatorVariablesBranchCommonOperatorVariable() {
    QueryGraphInfo.Builder builder = new QueryGraphInfo.Builder();
    builder.addDownstream("O", "LEFT");
    builder.addDownstream("O", "RIGHT");
    builder.addDownstream("LEFT", "SINK_LEFT");
    builder.addDownstream("RIGHT", "SINK_RIGHT");
    builder.addRenaming("O", "V1", "V2");
    builder.addRenaming("LEFT", "V2", "VARSINK");
    builder.addRenaming("RIGHT", "V2", "VARSINK");
    final Map<String, Set<VariableRenaming>> sinkToOperatorVariables = builder.build()
        .sinkToOperatorVariables("O", "SINK_RIGHT");
    Assert.assertEquals(sinkToOperatorVariables.size(), 1, "Wrong mapping size");
    Assert.assertEquals(sinkToOperatorVariables.get("VARSINK"),
        Arrays.asList(RenamingHelper.testInstance("V1", "SINK_RIGHT->RIGHT->O")));
  }


  @Test
  public void testBranchOperatorsMissingMapping() {
    QueryGraphInfo.Builder builder = new QueryGraphInfo.Builder();
    builder.addDownstream("O", "LEFT");
    builder.addDownstream("O", "RIGHT");
    builder.addDownstream("LEFT", "SINK");
    builder.addDownstream("RIGHT", "SINK");
    builder.addRenaming("O", "V1", "V2");
    builder.addRenaming("LEFT", "V2", "VARSINK1");
    final Set<VariableRenaming> operatorToSinkVariables = builder.build()
        .operatorToSinkVariables("V1", "O");
    Assert.assertEquals(
        operatorToSinkVariables.stream().map(r -> r.name()).collect(Collectors.toSet()),
        new HashSet<>((Arrays.asList("VARSINK1", "V2"))));
  }

  @Test
  public void testFromYaml() throws FileNotFoundException {
    QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(QUERY_INFO_1_PATH);
    registerTransformsForYaml1(queryInfo);
    final Set<VariableRenaming> operatorToSinkVariables = queryInfo.operatorToSinkVariables("V1",
        "O");
    Assert.assertEquals(
        operatorToSinkVariables,
        new HashSet<>(
            (Arrays.asList(RenamingHelper.testInstance("VARSINK1", "f->g", "O->LEFT->SINK"),
                RenamingHelper.testInstance("VARSINK1", "f->g->k", "O->LEFT->LEFTSIDE->SINK"),
                RenamingHelper.testInstance("V2", "f->h", "O->RIGHT->SINK")))));
  }

  @Test
  public void testFromYamlNone() throws FileNotFoundException {
    QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(QUERY_INFO_1_PATH);
    registerTransformsForYaml1(queryInfo);
    final Set<VariableRenaming> operatorToSinkVariables = queryInfo.operatorToSinkVariables("V1",
        "LEFT");
    Assert.assertEquals(
        operatorToSinkVariables,
        new HashSet<>((Arrays.asList(RenamingHelper.testInstance("NONE", "LEFT->LEFTSIDE->SINK"),
            RenamingHelper.testInstance("NONE", "LEFT->SINK")))));
  }


  @Test
  public void testFromYaml2() throws FileNotFoundException {
    QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(QUERY_INFO_2_PATH);
    final Map<String, Set<VariableRenaming>> fcount = queryInfo.sinkToOperatorVariables("FCOUNT",
        "SINK");
    Assert.assertEquals(fcount.get("movieIdentifier"),
        Arrays.asList(RenamingHelper.testInstance("movieIdentifier", "SINK->FCOUNT")),
        "Renaming failed");
  }

  @Test
  public void testFromYaml3() throws FileNotFoundException {
    QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(QUERY_INFO_3_PATH);
    final Map<String, Set<VariableRenaming>> left = queryInfo.sinkToOperatorVariables("LEFT");
    final Map<String, Set<VariableRenaming>> right = queryInfo.sinkToOperatorVariables("RIGHT");
    Assert.assertEquals(left.get("Anew"),
        Arrays.asList(RenamingHelper.testInstance("A", "SINK->JOIN->LEFT")),
        "Left renaming failed");
    Assert.assertEquals(right.get("Anew"),
        Arrays.asList(RenamingHelper.testInstance("B", "SINK->JOIN->RIGHT")),
        "Right renaming failed");
  }

  @Test
  public void testTransformIntervalFromSinkWS0() throws FileNotFoundException {
    QueryGraphInfo.Builder builder = new Builder();
    builder.addDownstream("A", "B");
    builder.addDownstream("B", "C");
    // Window sizes are 0 by default so no need to set
    QueryGraphInfo queryInfo = builder.build();
    final long leftBoundary = 10000;
    final long rightBoundary = 15000;
    OptionalLong newLeftBoundary = queryInfo.legacyTransformIntervalStartFromSink("C", "A",
        leftBoundary,
        rightBoundary);
    OptionalLong newRightBoundary = queryInfo.legacyTransformIntervalEndFromSink("C", "A",
        leftBoundary,
        rightBoundary);
    Assert.assertTrue(newLeftBoundary.isPresent(), "Failed to compute new left boundary");
    Assert.assertTrue(newRightBoundary.isPresent(), "Failed to compute new right boundary");
    Assert.assertEquals(newLeftBoundary.getAsLong(), leftBoundary, "Left boundary transform wrong");
    Assert.assertEquals(newRightBoundary.getAsLong(), rightBoundary,
        "Right boundary transform wrong");
  }

  @Test
  public void testTransformIntervalFromSinkOneWS() throws FileNotFoundException {
    QueryGraphInfo.Builder builder = new Builder();
    builder.addDownstream("A", "B");
    builder.addDownstream("B", "C");
    builder.setWindowSize("B", 4000);
    QueryGraphInfo queryInfo = builder.build();
    final long leftBoundary = 10000;
    final long rightBoundary = 15000;
    OptionalLong newLeftBoundary = queryInfo.legacyTransformIntervalStartFromSink("C", "A",
        leftBoundary,
        rightBoundary);
    OptionalLong newRightBoundary = queryInfo.legacyTransformIntervalEndFromSink("C", "A",
        leftBoundary,
        rightBoundary);
    Assert.assertTrue(newLeftBoundary.isPresent(), "Failed to compute new left boundary");
    Assert.assertTrue(newRightBoundary.isPresent(), "Failed to compute new right boundary");
    Assert.assertEquals(newLeftBoundary.getAsLong(), 8000, "Left boundary transform wrong");
    Assert.assertEquals(newRightBoundary.getAsLong(), 12000, "Right boundary transform wrong");
  }

  @Test
  public void testTransformIntervalFromSinkOneWSImpossible() {
    QueryGraphInfo.Builder builder = new Builder();
    builder.addDownstream("A", "B");
    builder.addDownstream("B", "C");
    builder.setWindowSize("B", 4000);
    QueryGraphInfo queryInfo = builder.build();
    final long leftBoundary = 10000;
    final long rightBoundary = 11000;
    OptionalLong newLeftBoundary = queryInfo.legacyTransformIntervalStartFromSink("C", "A",
        leftBoundary,
        rightBoundary);
    OptionalLong newRightBoundary = queryInfo.legacyTransformIntervalEndFromSink("C", "A",
        leftBoundary,
        rightBoundary);
    Assert.assertFalse(newLeftBoundary.isPresent(), "Should have failed to compute boundary");
    Assert.assertFalse(newRightBoundary.isPresent(), "Should have failed to compute boundary");
  }

  @Test
  public void testTransformIntervalFromSinkOneWSLow() throws FileNotFoundException {
    QueryGraphInfo.Builder builder = new Builder();
    builder.addDownstream("A", "B");
    builder.addDownstream("B", "C");
    builder.setWindowSize("B", 2500);
    builder.setWindowAdvance("B", 500);
    QueryGraphInfo queryInfo = builder.build();
    final long leftBoundary = 0;
    final long rightBoundary = 2000;
    OptionalLong newLeftBoundary = queryInfo.legacyTransformIntervalStartFromSink("C", "A",
        leftBoundary,
        rightBoundary);
    OptionalLong newRightBoundary = queryInfo.legacyTransformIntervalEndFromSink("C", "A",
        leftBoundary,
        rightBoundary);
    Assert.assertTrue(newLeftBoundary.isPresent(), "Failed to compute new left boundary");
    Assert.assertEquals(newLeftBoundary, OptionalLong.of(0), "Wrong left boundary");
    Assert.assertFalse(newRightBoundary.isPresent(), "Failed to compute new right boundary");
  }


  @Test
  public void testTransformIntervalFromSinkOneWSExactDivision() throws FileNotFoundException {
    QueryGraphInfo.Builder builder = new Builder();
    builder.addDownstream("A", "B");
    builder.addDownstream("B", "C");
    builder.setWindowSize("B", 2500);
    builder.setWindowAdvance("B", 500);
    QueryGraphInfo queryInfo = builder.build();
    final long leftBoundary = 10000;
    final long rightBoundary = 15000;
    OptionalLong newLeftBoundary = queryInfo.legacyTransformIntervalStartFromSink("C", "A",
        leftBoundary,
        rightBoundary);
    OptionalLong newRightBoundary = queryInfo.legacyTransformIntervalEndFromSink("C", "A",
        leftBoundary,
        rightBoundary);
    Assert.assertTrue(newLeftBoundary.isPresent(), "Failed to compute new left boundary");
    Assert.assertTrue(newRightBoundary.isPresent(), "Failed to compute new right boundary");
    Assert.assertEquals(newLeftBoundary.getAsLong(), 8000, "Left boundary transform wrong");
    Assert.assertEquals(newRightBoundary.getAsLong(), 15000, "Right boundary transform wrong");
  }

  @Test
  public void testTransformIntervalFromSinkThreeWS() throws FileNotFoundException {
    QueryGraphInfo.Builder builder = new Builder();
    builder.addDownstream("A", "B");
    builder.addDownstream("B", "C");
    builder.setWindowSize("A", 3000);
    builder.setWindowSize("B", 5000);
    builder.setWindowAdvance("B", 4000);
    builder.setWindowSize("C", 7000);
    builder.setWindowAdvance("C", 3000);
    QueryGraphInfo queryInfo = builder.build();
    final long leftBoundary = 20000;
    final long rightBoundary = 40000;
    OptionalLong newLeftBoundary = queryInfo.legacyTransformIntervalStartFromSink("C", "A",
        leftBoundary,
        rightBoundary);
    OptionalLong newRightBoundary = queryInfo.legacyTransformIntervalEndFromSink("C", "A",
        leftBoundary,
        rightBoundary);
    Assert.assertTrue(newLeftBoundary.isPresent(), "Failed to compute new left boundary");
    Assert.assertTrue(newRightBoundary.isPresent(), "Failed to compute new right boundary");
    Assert.assertEquals(newLeftBoundary.getAsLong(), 12000, "Left boundary transform wrong");
    Assert.assertEquals(newRightBoundary.getAsLong(), 36000, "Right boundary transform wrong");
  }

  @Test
  public void testTransformIntervalWithBacktrackingAndSlide() {
    QueryGraphInfo.Builder builder = new Builder();
    builder.addDownstream("A", "B");
    builder.addDownstream("B", "C");
    builder.setWindowSize("A", 70);
    builder.setWindowAdvance("A", 50);
    builder.setWindowSize("C", 20);
    builder.setWindowAdvance("C", 10);
    QueryGraphInfo queryInfo = builder.build();
    final long leftBoundary = 100;
    final long rightBoundary = 500;
    OptionalLong newLeftBoundary = queryInfo.legacyTransformIntervalStartFromSink("C", "A",
        leftBoundary,
        rightBoundary);
    OptionalLong newRightBoundary = queryInfo.legacyTransformIntervalEndFromSink("C", "A",
        leftBoundary,
        rightBoundary);
    Assert.assertTrue(newLeftBoundary.isPresent(), "Failed to compute new left boundary");
    Assert.assertTrue(newRightBoundary.isPresent(), "Failed to compute new right boundary");
    Assert.assertEquals(newLeftBoundary.getAsLong(), 50, "Left boundary transform wrong");
    Assert.assertEquals(newRightBoundary.getAsLong(), 470, "Right boundary transform wrong");
  }

  @Test
  public void testTransformIntervalWithDoubleBacktracking() {
    QueryGraphInfo.Builder builder = new Builder();
    builder.addDownstream("A", "B");
    builder.addDownstream("B", "C");
    builder.addDownstream("C", "D");
    builder.setWindowSize("A", 200);
    builder.setWindowSize("B", 20);
    builder.setWindowSize("C", 50);
    builder.setWindowSize("D", 10);
    QueryGraphInfo queryInfo = builder.build();
    final long leftBoundary = 1000;
    final long rightBoundary = 2000;
    OptionalLong newLeftBoundary = queryInfo.legacyTransformIntervalStartFromSink("D", "A",
        leftBoundary,
        rightBoundary);
    OptionalLong newRightBoundary = queryInfo.legacyTransformIntervalEndFromSink("D", "A",
        leftBoundary,
        rightBoundary);
    Assert.assertTrue(newLeftBoundary.isPresent(), "Failed to compute new left boundary");
    Assert.assertTrue(newRightBoundary.isPresent(), "Failed to compute new right boundary");
    Assert.assertEquals(newLeftBoundary.getAsLong(), 1000, "Left boundary transform wrong");
    Assert.assertEquals(newRightBoundary.getAsLong(), 2000, "Right boundary transform wrong");
  }

  @Test
  public void testTransformIntervalFromSinkFromYaml() throws FileNotFoundException {
    QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(QUERY_INFO_1_PATH);
    final long leftBoundary = 10000;
    final long rightBoundary = 15000;
    OptionalLong newLeftBoundary = queryInfo.legacyTransformIntervalStartFromSink("SINK", "O",
        leftBoundary, rightBoundary);
    OptionalLong newRightBoundary = queryInfo.legacyTransformIntervalEndFromSink("SINK", "O",
        leftBoundary, rightBoundary);
    Assert.assertTrue(newLeftBoundary.isPresent(), "Failed to compute new left boundary");
    Assert.assertTrue(newRightBoundary.isPresent(), "Failed to compute new right boundary");
    Assert.assertEquals(newLeftBoundary.getAsLong(), 4662, "Left boundary transform wrong");
    Assert.assertEquals(newRightBoundary.getAsLong(), 14319, "Right boundary transform wrong");
  }

  @Test
  public void testZeroLeftBoundarySmallLargeWS() throws FileNotFoundException {
    QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(
        "src/main/resources/LinearRoadAccident-DAG.yaml");
    final long leftBoundary = 0;
    final long rightBoundary = 1000000;
    OptionalLong newLeftBoundary = queryInfo.legacyTransformIntervalStartFromSink("SINK",
        "FILTER-ZERO-SPEED",
        leftBoundary, rightBoundary);
    Assert.assertTrue(newLeftBoundary.isPresent(), "Failed to compute new left boundary");
    OptionalLong newRightBoundary = queryInfo.legacyTransformIntervalEndFromSink("SINK",
        "FILTER-ZERO-SPEED",
        leftBoundary, rightBoundary);
    Assert.assertTrue(newRightBoundary.isPresent(), "Failed to compute new right boundary");
    Assert.assertEquals(newLeftBoundary.getAsLong(), 0, "Left boundary transform wrong");
    Assert.assertEquals(newRightBoundary.getAsLong(), 990000, "Right boundary transform wrong");
  }

  @Test
  public void testTransformIntervalFromSinkNonExistingFromYaml() throws FileNotFoundException {
    QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(QUERY_INFO_1_PATH);
    // Testing 1) Multiple paths 2) Dead-end path 3) Path that does not fit in interval
    final long leftBoundary = 10000;
    final long rightBoundary = 10005;
    OptionalLong newLeftBoundary = queryInfo.legacyTransformIntervalStartFromSink("SINK", "O",
        leftBoundary, rightBoundary);
    OptionalLong newRightBoundary = queryInfo.legacyTransformIntervalEndFromSink("SINK", "O",
        leftBoundary, rightBoundary);
    Assert.assertFalse(newLeftBoundary.isPresent(), "Impossible left boundary transform");
    Assert.assertFalse(newRightBoundary.isPresent(), "Impossible right boundary transform");
  }

  @Test
  public void testUpstreamPaths() throws FileNotFoundException {
    QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(QUERY_INFO_1_PATH);
    Set<Path> paths = queryInfo.upstreamPaths("SINK", "O");
    Assert.assertEquals(paths, new HashSet<>(
        Arrays.asList(Path.of("SINK", "LEFT", "O"), Path.of("SINK", "LEFTSIDE", "LEFT", "O"),
            Path.of("SINK", "RIGHT", "O"))));
  }

  @Test(expectedExceptions = {IllegalStateException.class})
  public void wrongOperatorName() throws FileNotFoundException {
    QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(QUERY_INFO_1_PATH);
    queryInfo.sinkToOperatorVariables("UNKNOWN", "SINK");
  }

  @Test(expectedExceptions = {IllegalStateException.class})
  public void wrongSinkName() throws FileNotFoundException {
    QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(QUERY_INFO_1_PATH);
    queryInfo.sinkToOperatorVariables("O", "UNKNOWN_SINK");
  }

  @Test
  public void maxDelayTest() throws FileNotFoundException {
    QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(QUERY_INFO_1_PATH);
    Assert.assertEquals(queryInfo.maxDelay(), 9110, "Wrong maxDelay");
  }

  @Test
  public void testTransform() throws FileNotFoundException {
    QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(QUERY_INFO_1_PATH);
    registerTransformsForYaml1(queryInfo);
    final Set<VariableRenaming> variableRenamings = queryInfo.operatorToSinkVariables("V1", "O");
    final Map<Collection<String>, TransformFunction> variableTransforms = variableRenamings.stream()
        .collect(Collectors.toMap(r -> r.transformKeys().components(), r -> r.transform().get()));
    final int value = 10;
    Assert.assertEquals(variableTransforms.get(Arrays.asList("f", "g", "k")).apply(value),
        TRANSFORM_K.apply(TRANSFORM_G.apply(TRANSFORM_F.apply(value))), "Wrong transform fgk");
    Assert.assertEquals(variableTransforms.get(Arrays.asList("f", "g")).apply(value),
        TRANSFORM_F.andThen(TRANSFORM_G).apply(value), "Wrong transform fg");
    Assert.assertEquals(variableTransforms.get(Arrays.asList("f", "h")).apply(value),
        TRANSFORM_F.andThen(TRANSFORM_H).apply(value), "Wrong transform fh");
  }

  @Test
  public void testTransform2() throws FileNotFoundException {
    QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(QUERY_INFO_1_PATH);
    registerTransformsForYaml1(queryInfo);
    final Map<String, Set<VariableRenaming>> variableRenamings = queryInfo.sinkToOperatorVariables(
        "O", "SINK");
    System.out.println(variableRenamings);
  }

  @Test
  public void testTransformMissing() throws FileNotFoundException {
    QueryGraphInfo queryInfo = QueryGraphInfo.fromYaml(QUERY_INFO_3_PATH);
    final Map<String, Set<VariableRenaming>> left = queryInfo.sinkToOperatorVariables("LEFT");
    Assert.assertFalse(left.get("Anew").stream().findFirst().get().transform().isPresent(),
        "Non-existing transform present");
  }

  public static void registerTransformsForYaml1(QueryGraphInfo info) {
    info.registerTransform("f", TRANSFORM_F)
        .registerTransform("g", TRANSFORM_G)
        .registerTransform("h", TRANSFORM_H)
        .registerTransform("k", TRANSFORM_K);

  }

}