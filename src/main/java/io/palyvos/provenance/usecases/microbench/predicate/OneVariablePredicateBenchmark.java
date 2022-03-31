package io.palyvos.provenance.usecases.microbench.predicate;

import io.palyvos.provenance.missing.predicate.Condition;
import io.palyvos.provenance.missing.predicate.OneVariableCondition;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.predicate.Predicate.PredicateStrategy;
import io.palyvos.provenance.missing.predicate.ReflectionVariable;
import io.palyvos.provenance.missing.predicate.Variable;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
public class OneVariablePredicateBenchmark {

  // Predicate strategy is OR and all tuple attributes will be equal to earlyStop
  // If true, evaluation will stop at first condition (because OR will be true)
  @Param({"true", "false"})
  public boolean earlyStop;

  @Param({"2", "4", "8", "16"})
  public int nConditions;

  @Param({"1", "2", "4", "8", "16"})
  public int nVariables;

  public DummyBooleanTuple tuple;
  public Predicate predicate;

  public static void main(String[] args) throws Exception {
    CommandLineOptions cliOpts = new CommandLineOptions(args);
    Options opts = new OptionsBuilder()
        .parent(cliOpts)
        .include(OneVariablePredicateBenchmark.class.getSimpleName())
        .build();
    System.out.println(Arrays.toString(args));
    new Runner(opts).run();
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @Fork(value = 1)
  @Warmup(iterations = 2)
  @Measurement(iterations = 10)
  public boolean predicateBench() {
    return predicate.evaluate(new DummyBooleanTuple(earlyStop), 123);
  }


  @Setup(Level.Iteration)
  public void predicateSetup() {
    if (nVariables > nConditions) {
      System.out.println("\n> Skipping invalid config...");
      System.exit(0);
    }
    predicate = Predicate.of(PredicateStrategy.OR, newConditions(nConditions, nVariables));
  }


  static Condition[] newConditions(int nConditions, int nVariables) {
    Condition[] conditions = new Condition[nConditions + 1];
    Variable[] variables = new Variable[nVariables + 1];
    for (int i = 0; i < variables.length; i++) {
      variables[i] = ReflectionVariable.fromField("f" + i);
    }
    for (int i = 0; i < conditions.length; i++) {
      conditions[i] = new OneVariableCondition(variables[i % nVariables],
          var -> var.asBoolean() == true);
    }
    // Add non-existent variable to test renaming functionality
    variables[variables.length - 1] = ReflectionVariable.fromField("nonExistent");
    conditions[conditions.length - 1] = new OneVariableCondition(variables[variables.length - 1],
        var -> var.asBoolean() == true);
    return conditions;
  }

}
