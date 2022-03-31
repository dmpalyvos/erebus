package io.palyvos.provenance.usecases.smartgrid;

import io.palyvos.provenance.usecases.smartgrid.AverageHouseholdUsageFunction.SmartMeterAccumulator;
import java.io.Serializable;
import org.apache.flink.api.common.functions.AggregateFunction;

public class AverageHouseholdUsageFunction implements
    AggregateFunction<SmartGridTuple, SmartMeterAccumulator, HouseholdUsageTuple> {


  @Override
  public SmartMeterAccumulator createAccumulator() {
    return new SmartMeterAccumulator();
  }

  @Override
  public SmartMeterAccumulator add(SmartGridTuple tuple, SmartMeterAccumulator accumulator) {
    accumulator.add(tuple);
    return accumulator;
  }

  @Override
  public HouseholdUsageTuple getResult(SmartMeterAccumulator accumulator) {
    return accumulator.getResult();
  }

  @Override
  public SmartMeterAccumulator merge(SmartMeterAccumulator a, SmartMeterAccumulator b) {
    throw new UnsupportedOperationException("not implemented");
  }

  public static class SmartMeterAccumulator implements Serializable {

    private int householdId;
    private int houseId;
    private double value;
    private int count;
    private long stimulus = -1;
    private long timestamp = -1;

    public void add(SmartGridTuple tuple) {
      this.householdId = tuple.householdId;
      this.houseId = tuple.houseId;
      this.stimulus = Math.max(this.stimulus, tuple.stimulus);
      this.timestamp = Math.max(this.timestamp, tuple.timestamp);
      value += tuple.value;
      count += 1;
    }

    public HouseholdUsageTuple getResult() {
      final double average = count > 0 ? value / count : 0;
      return new HouseholdUsageTuple(timestamp, householdId, houseId, average, stimulus);
    }

  }

}
