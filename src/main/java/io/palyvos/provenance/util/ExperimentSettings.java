package io.palyvos.provenance.util;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import io.palyvos.provenance.ananke.aggregate.ListAggregateStrategy;
import io.palyvos.provenance.ananke.aggregate.ProvenanceAggregateStrategy;
import io.palyvos.provenance.ananke.aggregate.SortedPointersAggregateStrategy;
import io.palyvos.provenance.ananke.aggregate.UnsortedPointersAggregateStrategy;
import io.palyvos.provenance.ananke.functions.DefaultProvenanceFunctionFactory;
import io.palyvos.provenance.ananke.functions.ProvenanceFunctionFactory;
import io.palyvos.provenance.ananke.functions.noop.NoopProvenanceFunctionFactory;
import io.palyvos.provenance.ananke.output.FileProvenanceGraphEncoder;
import io.palyvos.provenance.ananke.output.GephiProvenanceGraphEncoder;
import io.palyvos.provenance.ananke.output.NoOpProvenanceGraphEncoder;
import io.palyvos.provenance.ananke.output.ProvenanceGraphEncoder;
import io.palyvos.provenance.ananke.output.TimestampedFileProvenanceGraphEncoder;
import io.palyvos.provenance.genealog.DetailedGenealogDataSerializer;
import io.palyvos.provenance.genealog.GenealogDataSerializer;
import io.palyvos.provenance.genealog.SourceSinkGenealogDataSerializer;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.util.ComponentCategory;
import io.palyvos.provenance.missing.util.KafkaPredicateUtil;
import io.palyvos.provenance.missing.util.PickedProvenanceFunctionFactoryWrapper;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class ExperimentSettings implements Serializable {

  public static final String LATENCY_FILE = "latency";
  public static final String THROUGHPUT_FILE = "rate";
  public static final String KAFKA_THROUGHPUT_FILE = "kafka-rate";
  public static final String SINK_THROUGHPUT_FILE = "sink-rate";
  public static final String PREDICATE_IN_FILE = "predicate-in";
  private static final String PREDICATE_OUT_FILE = "predicate-out";
  public static final String TRAVERSAL_FILE = "traversal";
  public static final String DEFAULT_SLOT_SHARING_GROUP = "default";
  public static final String SECOND_SLOT_SHARING_GROUP = "group2";
  private static final String THIRD_SLOT_SHARING_GROUP = "group3";
  private static final String PROVENANCE_READ_TIME = "provreadtime";
  private static final String PROVENANCE_WRITE_TIME = "provwritetime";
  private static final String PROVENANCE_READS = "provreads";
  private static final String PROVENANCE_WRITES = "provwrites";
  private static final String PROVENANCE_SIZE_FILE = "provsize";
  private static final String DELIVERY_LATENCY = "deliverylatency";
  public static final String EXTENSION_CSV = "csv";
  public static final String EXTENSION_OUT = "out";
  private static final String PAST_CHECKER_BUFFER_SIZE_FILE = "past-buffer-size";
  private static final String PAST_CHECKER_RATE_FILE = "past-rate";

  @Parameter(names = "--statisticsFolder", required = true, description = "path where output files will be stored")
  private String statisticsFolder;

  @Parameter(names = "--kafkaHost", description = "Kafka host in the format host:port")
  private String kafkaHost;

  @Parameter(names = "--inputFile", description = "the input file of the streaming query")
  private String inputFile;

  @Parameter(names = "--inputFolder", description = "the input folder of the streaming query")
  private String inputFolder;

  @Parameter(names = "--outputFile", description = "the name of the file to store where the output of the query will be stored")
  private String outputFile = "sink";

  @Parameter(names = "--sourcesNumber", description = "number of sources of the streaming query")
  private int sourcesNumber = 1;

  @Parameter(names = "--autoFlush")
  private boolean autoFlush = true;

  @Parameter(names = "--sinkParallelism")
  private int sinkParallelism = 1;

  @Parameter(names = "--distributed", description = "configure the query for distributed execution")
  private boolean distributed;

  @Parameter(names = "--traversalStatistics", description = "record GeneaLog graph traversal statistics")
  private boolean traversalStatistics;

  @Parameter(names = "--sourceRepetitions", description = "number of times to repeat the source input")
  private int sourceRepetitions = 1;

  @Parameter(names = "--idShift")
  private long idShift = 0;

  @Parameter(names = "--sourceIP", description = "IP address of the remote data source")
  private String sourceIP;

  @Parameter(names = "--sourcePort", description = "port of the remote data source")
  private int sourcePort;

  @Parameter(names = "--maxParallelism", description = "maximum allowed parallelism")
  private int maxParallelism = 32;

  @Parameter(names = "--parallelism", description = "desired parallelism")
  private int parallelism = 1;

  @Parameter(names = "--kafkaPartitions", description = "Number of kakfa partitions that why-not answers will be written to and read from")
  private int kafkaPartitions = 1;

  @Parameter(names = "--provenanceActivator", description = "provenance algorithm, e.g., ANANKE, GENEALOG, etc.")
  private ProvenanceActivator provenanceActivator = ProvenanceActivator.GENEALOG;

  @Parameter(names = "--aggregateStrategy", converter = AggregateStrategyConverter.class, description = "strategy for handling out-of-order aggregate tuples")
  private Supplier<ProvenanceAggregateStrategy> aggregateStrategySupplier =
      (Supplier<ProvenanceAggregateStrategy> & Serializable) SortedPointersAggregateStrategy::new;

  @Parameter(names = "--graphEncoder", description = "output encoder for the forward-provenance graph")
  private String graphEncoder = TimestampedFileProvenanceGraphEncoder.class.getSimpleName();

  @Parameter(names = "--watermarkInterval")
  private long watermarkInterval = 200;

  @Parameter(names = "--syntheticInputLength")
  private int syntheticInputLength = 1000;

  @Parameter(names = "--syntheticDelay")
  private int syntheticDelay = 10;

  @Parameter(names = "--syntheticProvenanceSize")
  private int syntheticProvenanceSize = 100;

  @Parameter(names = "--syntheticTupleSize")
  private int syntheticTupleSize = 32;

  @Parameter(names = "--syntheticSourceParallelism")
  private int syntheticSourceParallelism = 1;

  @Parameter(names = "--syntheticProvenanceOverlap")
  private int syntheticProvenanceOverlap;

  @Parameter(names = "--disableSinkChaining")
  private boolean disableSinkChaining;

  @Parameter(names = "--pollFrequencyMillis", description = "poll frequency for external DB experiments")
  private long pollFrequencyMillis = 1000;

  @Parameter(names = "--uniqueDbKeys", description = "enforce unique key contraints on relational DB experiments")
  private boolean uniqueDbKeys;

  @Parameter(names = "--dbFlowControl", description = "enforce basic flow control in external DB writer")
  private boolean dbFlowControl;

  @Parameter(names = "--detailedProvenance", arity = 1, description = "(Experimental) Maintain provenance of intermediate operators")
  private boolean detailedProvenance;

  @Parameter(names = "--provenance", arity = 1, description = "Record (or not) provenance data")
  private boolean provenance = true;

  @Parameter(names = "--predicate", description = "Unique name of the predicate used to answer a why-not question")
  private String predicate;

  @Parameter(names = "--bufferDelay", description = "Size of the missing answer buffer (in event time seconds). Set to -1 for no delay, or >= 0 for delay.")
  private long bufferDelay = -1;

  @Parameter(names = "--predicateDelay", description = "(Artificial) delay before the predicate is activated (in processing time seconds)")
  private long predicateDelay;

  @Parameter(names = "--pastBufferBucketSize", description = "Size of each bucket in the past buffer, in event-time millisec")
  private long pastBufferBucketSize = 1000;

  @Parameter(names = "--predicateSlack", description = "Slack (in even-time seconds) to consider for predicate matches at query sink")
  private long predicateSlack;

  @Parameter(names = "--syntheticFilterDiscardRate", description = "Discard rate of filter in synthetic query (0-100)")
  private int syntheticFilterDiscardRate;

  @Parameter(names = "--syntheticPredicateSelectivity", description = "Selectivity of predicate in synthetic query (0-100)")
  private int syntheticPredicateSelectivity;

  @Parameter(names = "--syntheticPredicateEvaluationTime", description = "Time it takes for the synthetic predicate to be evaluated, in milliseconds")
  private long syntheticPredicateEvaluationTime;

  @Parameter(names = "--kafkaSenderQueueSize", description = "Size of the kafka sender queue (for each operator). Must be a power of 2")
  private int kafkaSenderQueueSize = 2 << 12;

  @Parameter(names = "--syntheticUseEncapsulation", arity = 1, description = "Choose whether to use ProvenanceTupleContainer in synthetic experiment or use native tuple")
  private boolean syntheticUseEncapsulation;

  private Predicate delayedPredicate;

  public static ExperimentSettings newInstance(String[] args) {
    ExperimentSettings settings = new ExperimentSettings();
    JCommander.newBuilder().addObject(settings).build().parse(args);
    Validate.isTrue(Integer.bitCount(settings.kafkaSenderQueueSize) == 1,
        "kafkaSenderQueueSize must be a power of 2");
    return settings;
  }

  public static String statisticsFile(
      String operator,
      Object taskIndex,
      String statisticsFolder,
      String filename,
      String fileExtension) {
    return new StringBuilder(statisticsFolder)
        .append(File.separator)
        .append(filename)
        .append("_")
        .append(operator)
        .append("_")
        .append(taskIndex)
        .append(".")
        .append(fileExtension)
        .toString();
  }

  public static String hostnameStatisticsFile(
      String operator,
      Object taskId,
      String statisticsFolder,
      String filename,
      String fileExtension) {
    String host = ManagementFactory.getRuntimeMXBean().getName();
    final String path = statisticsFile(
        operator, String.format("%s-%s", host, taskId), statisticsFolder, filename, fileExtension);
    try {
      if (new File(path).createNewFile()) {
        return path;
      }
      throw new IllegalStateException("File already exists!");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


  public static String uniqueStatisticsFile(
      String operator, String statisticsFolder, String filename, String fileExtension) {
    String taskId = RandomStringUtils.randomAlphanumeric(5);
    final int retries = 1;
    for (int i = 0; i < retries; i++) {
      try {
        return hostnameStatisticsFile(operator, taskId, statisticsFolder, filename, fileExtension);
      } catch (IllegalStateException e) {
      }
    }
    throw new IllegalStateException(
        String.format("Failed to create statistics file '%s' for operator '%s' after %d attempts",
            filename, operator, retries));
  }

  public static List<String> uniqueStatisticsFiles(
      String operator, String statisticsFolder, List<String> filenames, String fileExtension) {
    String taskId = RandomStringUtils.randomAlphanumeric(5);
    final int retries = 1;
    for (int i = 0; i < retries; i++) {
      try {
        final List<String> results = new ArrayList<>();
        for (String filename : filenames) {
          results.add(hostnameStatisticsFile(operator, taskId, statisticsFolder, filename,
              fileExtension));
        }
        return results;
      } catch (IllegalStateException e) {
      }
    }
    throw new IllegalStateException(
        String.format("Failed to create statistics files %s for operator '%s' after %d attempts",
            filenames, operator, retries));
  }

  public GenealogDataSerializer genealogDataSerializer() {
    return detailedProvenance ?
        DetailedGenealogDataSerializer.newInstance(
            aggregateStrategySupplier().get(),
            statisticsFolder(),
            graphTraversalStatistics(), detailedProvenance)
        : SourceSinkGenealogDataSerializer.newInstance(
            aggregateStrategySupplier().get(),
            statisticsFolder(),
            graphTraversalStatistics(), detailedProvenance);
  }

  public String secondSlotSharingGroup() {
    // If distributeHeavyOperators == false, assign all ops
    // to Flink's "default" co-location group (i.e., don't distribute to different slots)
    return distributed ? SECOND_SLOT_SHARING_GROUP : DEFAULT_SLOT_SHARING_GROUP;
  }

  public String thirdSlotSharingGroup() {
    // If distributeHeavyOperators == false, assign all ops
    // to Flink's "default" co-location group (i.e., don't distribute to different slots)
    return distributed ? THIRD_SLOT_SHARING_GROUP : DEFAULT_SLOT_SHARING_GROUP;
  }

  public boolean autoFlush() {
    return autoFlush;
  }

  public Supplier<ProvenanceAggregateStrategy> aggregateStrategySupplier() {
    return aggregateStrategySupplier;
  }

  public boolean dbFlowControl() {
    return dbFlowControl;
  }

  public String inputFile(String inputExtension) {
    Validate.notEmpty(inputFile, "inputFile");
    return String.format("%s.%s", inputFile, inputExtension);
  }

  public String inputFolder() {
    Validate.notEmpty(inputFolder, "inputFolder");
    return inputFolder;
  }

  public String statisticsFolder() {
    return statisticsFolder;
  }

  public int sinkParallelism() {
    return sinkParallelism;
  }

  public String latencyFile(int taskIndex, String operator) {
    return statisticsFile(operator, taskIndex, statisticsFolder(), LATENCY_FILE, EXTENSION_CSV);
  }

  public String throughputFile(int taskIndex, String operator) {
    return statisticsFile(operator, taskIndex, statisticsFolder(), THROUGHPUT_FILE, EXTENSION_CSV);
  }

  public String kafkaThroughputFile(int taskIndex, String operator) {
    return statisticsFile(operator, taskIndex, statisticsFolder(), KAFKA_THROUGHPUT_FILE,
        EXTENSION_CSV);
  }

  public String sinkThroughputFile(int taskIndex, String operator) {
    return statisticsFile(operator, taskIndex, statisticsFolder(), SINK_THROUGHPUT_FILE,
        EXTENSION_CSV);
  }

  public List<String> predicateInOutFiles(String operator) {
    return uniqueStatisticsFiles(operator, statisticsFolder(),
        Arrays.asList(PREDICATE_IN_FILE, PREDICATE_OUT_FILE),
        EXTENSION_CSV);
  }

  public List<String> pastCheckerUtilStatistics(String operator) {
    return uniqueStatisticsFiles(operator, statisticsFolder(),
        Arrays.asList(PAST_CHECKER_BUFFER_SIZE_FILE, PAST_CHECKER_RATE_FILE),
        EXTENSION_CSV);
  }

  public String outputFile(int taskIndex, String operator) {
    return statisticsFile(operator, taskIndex, statisticsFolder(), outputFile, EXTENSION_OUT);
  }

  public String outputFile(int taskIndex, String operator, String extension) {
    return statisticsFile(operator, taskIndex, statisticsFolder(), outputFile, extension);
  }

  public String provenanceReadsFile(int taskIndex, String operator) {
    return statisticsFile(operator, taskIndex, statisticsFolder(), PROVENANCE_READS, EXTENSION_CSV);
  }

  public String provenanceWritesFile(int taskIndex, String operator) {
    return statisticsFile(operator, taskIndex, statisticsFolder(), PROVENANCE_WRITES,
        EXTENSION_CSV);
  }

  public String provenanceReadTimeFile(int taskIndex, String operator) {
    return statisticsFile(operator, taskIndex, statisticsFolder(), PROVENANCE_READ_TIME,
        EXTENSION_CSV);
  }

  public String provenanceWriteTimeFile(int taskIndex, String operator) {
    return statisticsFile(operator, taskIndex, statisticsFolder(), PROVENANCE_WRITE_TIME,
        EXTENSION_CSV);
  }

  public String provenanceSizeFile(int taskIndex, String operator) {
    return statisticsFile(operator, taskIndex, statisticsFolder(), PROVENANCE_SIZE_FILE,
        EXTENSION_CSV);
  }

  public String deliveryLatencyFile(int taskIndex, String operator) {
    return statisticsFile(operator, taskIndex, statisticsFolder(), DELIVERY_LATENCY, EXTENSION_CSV);
  }


  public String kafkaHost() {
    Validate.notBlank(kafkaHost, "No kafka host provided");
    return kafkaHost;
  }

  public long idShift() {
    return idShift;
  }

  public String sourceIP() {
    return sourceIP;
  }

  public int sourcePort() {
    return sourcePort;
  }

  public int sourcesNumber() {
    return sourcesNumber;
  }

  public int maxParallelism() {
    return maxParallelism;
  }

  public int sourceRepetitions() {
    return sourceRepetitions;
  }

  public long getWatermarkInterval() {
    return watermarkInterval;
  }

  public int syntheticInputSize() {
    return syntheticInputLength;
  }

  public int syntheticDelay() {
    return syntheticDelay;
  }

  public int syntheticProvenanceSize() {
    return syntheticProvenanceSize;
  }

  public int syntheticSourceParallelism() {
    return syntheticSourceParallelism;
  }

  public int syntheticProvenanceOverlap() {
    Validate.isTrue(syntheticProvenanceOverlap >= 0);
    Validate.isTrue(
        syntheticProvenanceOverlap < syntheticProvenanceSize ||
            ((syntheticProvenanceOverlap == 0) && (syntheticProvenanceSize == 0)));
    return syntheticProvenanceOverlap;
  }

  public ProvenanceActivator provenanceActivator() {
    return provenanceActivator;
  }

  public boolean graphTraversalStatistics() {
    return traversalStatistics;
  }

  public int syntheticTupleSize() {
    return this.syntheticTupleSize;
  }

  public ProvenanceGraphEncoder newGraphEncoder(String name, int subtaskIndex) {

    if (FileProvenanceGraphEncoder.class.getSimpleName().equals(graphEncoder)) {
      return new FileProvenanceGraphEncoder(outputFile(subtaskIndex, name), autoFlush);
    } else if (TimestampedFileProvenanceGraphEncoder.class.getSimpleName().equals(graphEncoder)) {
      return new TimestampedFileProvenanceGraphEncoder(outputFile(subtaskIndex, name), autoFlush);
    } else if (GephiProvenanceGraphEncoder.class.getSimpleName().equals(graphEncoder)) {
      return new GephiProvenanceGraphEncoder("workspace1");
    } else if (NoOpProvenanceGraphEncoder.class.getSimpleName().equals(graphEncoder)) {
      return new NoOpProvenanceGraphEncoder();
    } else {
      throw new IllegalArgumentException(String.format("Invalid graph encoder: %s", graphEncoder));
    }

  }

  public boolean disableSinkChaining() {
    return disableSinkChaining;
  }

  public long pollFrequencyMillis() {
    return pollFrequencyMillis;
  }

  public boolean uniqueSqlKeys() {
    return uniqueDbKeys;
  }

  public boolean detailedProvenance() {
    return detailedProvenance;
  }

  public int parallelism() {
    return parallelism;
  }

  public long predicateSlackMillis() {
    Validate.isTrue(predicateSlack >= 0, "predicateSlack < 0");
    return TimeUnit.SECONDS.toMillis(predicateSlack);
  }

  public double syntheticPredicateSelectivity() {
    Validate.isTrue(syntheticPredicateSelectivity > 0 && syntheticPredicateSelectivity < 100,
        "Selectivity must be in (0, 100)");
    return syntheticPredicateSelectivity / 100.0;
  }

  public double syntheticFilterDiscardRate() {
    Validate.isTrue(syntheticFilterDiscardRate > 0 && syntheticFilterDiscardRate < 100,
        "Selectivity must be in (0, 100)");
    return syntheticFilterDiscardRate / 100.0;
  }

  public int kafkaSenderQueueSize() {
    return kafkaSenderQueueSize;
  }

  public long syntheticPredicateEvaluationTime() {
    Validate.isTrue(syntheticPredicateEvaluationTime >= 0, "syntheticPredicateEvaluationTime < 0");
    return syntheticPredicateEvaluationTime;
  }

  public boolean syntheticUseEncapsulation() {
    return syntheticUseEncapsulation;
  }

  private static class AggregateStrategyConverter
      implements IStringConverter<Supplier<ProvenanceAggregateStrategy>> {

    @Override
    public Supplier<ProvenanceAggregateStrategy> convert(String s) {
      switch (s.trim()) {
        case "unsortedPtr":
          return (Supplier<ProvenanceAggregateStrategy> & Serializable)
              UnsortedPointersAggregateStrategy::new;
        case "sortedPtr":
          return (Supplier<ProvenanceAggregateStrategy> & Serializable)
              SortedPointersAggregateStrategy::new;
        case "list":
          return (Supplier<ProvenanceAggregateStrategy> & Serializable) ListAggregateStrategy::new;
        default:
          throw new IllegalArgumentException("Unknown GeneaLog aggregate strategy provided");
      }
    }

  }

  public ProvenanceFunctionFactory provenanceFunctionFactory(boolean forMissingAnswers) {
    final ProvenanceFunctionFactory factory;
    if (this.provenance) {
      factory = new DefaultProvenanceFunctionFactory(aggregateStrategySupplier);
    } else {
      factory = new NoopProvenanceFunctionFactory();
    }
    return forMissingAnswers ? new PickedProvenanceFunctionFactoryWrapper(factory) : factory;
  }

  public boolean provenance() {
    return provenance;
  }


  public int kafkaPartitions() {
    Validate.validState(kafkaPartitions >= 1, "Kafka partitions < 1");
    return kafkaPartitions;
  }

  public long bufferDelayMillis() {
    return bufferDelay >= 0 ? TimeUnit.SECONDS.toMillis(bufferDelay) : bufferDelay;
  }

  public long pastBufferBucketSize() {
    return pastBufferBucketSize;
  }

  public Predicate initPredicate(PredicateHolder queryPredicates) {
    final Predicate selectedPredicate = queryPredicates.get(predicate);
    if (predicateDelay == 0) {
      return selectedPredicate;
    } else {
      this.delayedPredicate = selectedPredicate;
      // If delayed, we just use a disabled predicate that forwards tuples to past checker
      // but does not output anything
      return Predicate.disabled();
    }
  }

  public void checkSendDelayedPredicate() {
    if (delayedPredicate != null) {
      Validate.validState(predicateDelay > 0, "Found delayed predicate but predicateDelay <= 0");
      KafkaPredicateUtil.sendPredicateAsync(ComponentCategory.ALL, delayedPredicate,
          predicateDelay, kafkaHost());
    }
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("statisticsFolder", statisticsFolder)
        .append("kafkaHost", kafkaHost)
        .append("inputFile", inputFile)
        .append("inputFolder", inputFolder)
        .append("outputFile", outputFile)
        .append("sourcesNumber", sourcesNumber)
        .append("autoFlush", autoFlush)
        .append("sinkParallelism", sinkParallelism)
        .append("distributed", distributed)
        .append("traversalStatistics", traversalStatistics)
        .append("sourceRepetitions", sourceRepetitions)
        .append("idShift", idShift)
        .append("sourceIP", sourceIP)
        .append("sourcePort", sourcePort)
        .append("maxParallelism", maxParallelism)
        .append("parallelism", parallelism)
        .append("kafkaPartitions", kafkaPartitions)
        .append("provenanceActivator", provenanceActivator)
        .append("aggregateStrategySupplier", aggregateStrategySupplier)
        .append("graphEncoder", graphEncoder)
        .append("watermarkInterval", watermarkInterval)
        .append("syntheticInputLength", syntheticInputLength)
        .append("syntheticDelay", syntheticDelay)
        .append("syntheticProvenanceSize", syntheticProvenanceSize)
        .append("syntheticTupleSize", syntheticTupleSize)
        .append("syntheticSourceParallelism", syntheticSourceParallelism)
        .append("syntheticProvenanceOverlap", syntheticProvenanceOverlap)
        .append("disableSinkChaining", disableSinkChaining)
        .append("pollFrequencyMillis", pollFrequencyMillis)
        .append("uniqueDbKeys", uniqueDbKeys)
        .append("dbFlowControl", dbFlowControl)
        .append("detailedProvenance", detailedProvenance)
        .append("provenance", provenance)
        .append("predicate", predicate)
        .append("bufferDelay", bufferDelay)
        .append("predicateDelay", predicateDelay)
        .append("pastBufferBucketSize", pastBufferBucketSize)
        .append("predicateSlack", predicateSlack)
        .append("syntheticFilterDiscardRate", syntheticFilterDiscardRate)
        .append("syntheticPredicateSelectivity", syntheticPredicateSelectivity)
        .append("kafkaSenderQueueSize", kafkaSenderQueueSize)
        .append("delayedPredicate", delayedPredicate)
        .toString();
  }
}
