package io.palyvos.provenance.usecases.smartgrid;

import org.apache.flink.api.common.functions.JoinFunction;

public class NewAnomalyJoinFunction implements JoinFunction<HouseholdUsageTuple,
    PlugUsageTuple, AnomalyResultTuple> {

  @Override
  public AnomalyResultTuple join(HouseholdUsageTuple household, PlugUsageTuple plug)
      throws Exception {
    return new AnomalyResultTuple(plug.plugId, plug.householdId, plug.houseId,
        Math.round(plug.usage), household.usage,
        Math.abs(household.usage - plug.usage),
        Math.max(household.stimulus, plug.stimulus));
  }
}
