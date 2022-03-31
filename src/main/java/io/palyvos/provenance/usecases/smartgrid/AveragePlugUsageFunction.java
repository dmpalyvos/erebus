package io.palyvos.provenance.usecases.smartgrid;

import io.palyvos.provenance.usecases.smartgrid.AveragePlugUsageFunction.PlugAccumulator;
import java.io.Serializable;
import org.apache.flink.api.common.functions.AggregateFunction;

public class AveragePlugUsageFunction implements
    AggregateFunction<SmartGridTuple, PlugAccumulator, PlugUsageTuple> {


  @Override
  public PlugAccumulator createAccumulator() {
    return new PlugAccumulator();
  }

  @Override
  public PlugAccumulator add(SmartGridTuple tuple, PlugAccumulator accumulator) {
    accumulator.add(tuple);
    return accumulator;
  }

  @Override
  public PlugUsageTuple getResult(PlugAccumulator accumulator) {
    return accumulator.getResult();
  }

  @Override
  public PlugAccumulator merge(PlugAccumulator a, PlugAccumulator b) {
    throw new UnsupportedOperationException("not implemented");
  }

  public static class PlugAccumulator implements Serializable {

    private int plugId;
    private int householdId;
    private int houseId;
    private double value;
    private int count;
    private long stimulus = -1;
    private long timestamp = -1;

    public void add(SmartGridTuple tuple) {
      this.plugId = tuple.plugId;
      this.householdId = tuple.householdId;
      this.houseId = tuple.houseId;
      this.stimulus = Math.max(this.stimulus, tuple.stimulus);
      this.timestamp = Math.max(this.timestamp, tuple.timestamp);
      value += tuple.value;
      count += 1;
    }

    public PlugUsageTuple getResult() {
      final double average = count > 0 ? value / count : 0;
      return new PlugUsageTuple(average, plugId, householdId, houseId, stimulus);
    }

  }

}
