package io.palyvos.provenance.usecases.microbench.predicate;

import io.palyvos.provenance.missing.predicate.Condition;
import io.palyvos.provenance.missing.predicate.Predicate;
import io.palyvos.provenance.missing.predicate.Predicate.PredicateStrategy;
import io.palyvos.provenance.missing.predicate.ReflectionVariable;
import io.palyvos.provenance.missing.predicate.TwoVariableCondition;
import io.palyvos.provenance.missing.predicate.Variable;
import java.util.concurrent.ThreadLocalRandom;
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
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class TwoVariablePredicateBenchmark {

  public static void main(String[] args) throws Exception {
    CommandLineOptions cliOpts = new CommandLineOptions(args);
    Options opts = new OptionsBuilder()
        .parent(cliOpts)
        .include(TwoVariablePredicateBenchmark.class.getSimpleName())
        .build();
    new Runner(opts).run();
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @Fork(value = 2)
  @Warmup(iterations = 3)
  @Measurement(iterations = 10)
  public void predicateBench(ExecutionState state, Blackhole blackhole) {
    blackhole.consume(state.predicate.evaluate(new DummyLongTuple(state.tupleValue), 123));
  }

  @State(Scope.Benchmark)
  public static class ExecutionState {

    public DummyBooleanTuple tuple;
    public Predicate predicate;

    public long tupleValue;

    @Param({"2", "4", "8", "16"})
    public int nConditions;

    @Param({"2", "4", "8", "16"})
    public int nVariables;

    @Param({"AND"})
    public PredicateStrategy strategy;

    @Setup(Level.Iteration)
    public void predicateSetup() {
      tupleValue = ThreadLocalRandom.current().nextLong(0, 1000);
      if (nVariables > nConditions) {
        System.out.println("\n> Skipping invalid config...");
        System.exit(0);
      }
      predicate = Predicate.of(strategy, newConditions(nConditions, nVariables));
    }

  }


  static Condition[] newConditions(int nConditions, int nVariables) {
    Condition[] conditions = new Condition[nConditions];
    Variable[] variables = new Variable[nVariables];
    for (int i = 0; i < variables.length; i++) {
      variables[i] = ReflectionVariable.fromField("f" + i);
    }
    for (int i = 0; i < conditions.length; i++) {
      conditions[i] = new TwoVariableCondition(variables[i % nVariables],
          variables[(i + 1) % nVariables],
          (var1, var2) -> var1.asLong() * var2.asLong() > 100);
    }
    return conditions;
  }

}
