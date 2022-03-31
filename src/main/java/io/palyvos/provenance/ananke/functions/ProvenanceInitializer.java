package io.palyvos.provenance.ananke.functions;

import io.palyvos.provenance.genealog.GenealogTupleType;
import io.palyvos.provenance.util.LongExtractor;
import org.apache.flink.api.common.functions.MapFunction;

public class ProvenanceInitializer<T> implements MapFunction<T, ProvenanceTupleContainer<T>> {

  private final LongExtractor<T> timestampFunction;
  private final LongExtractor<T> stimulusFunction;

  public ProvenanceInitializer(LongExtractor<T> timestampFunction,
      LongExtractor<T> stimulusFunction) {
    this.timestampFunction = timestampFunction;
    this.stimulusFunction = stimulusFunction;
  }

  @Override
  public ProvenanceTupleContainer<T> map(T value) throws Exception {
    ProvenanceTupleContainer<T> out = new ProvenanceTupleContainer<>(value);
    out.initGenealog(GenealogTupleType.SOURCE);
    out.setTimestamp(timestampFunction.applyAsLong(value));
    out.setStimulus(stimulusFunction.applyAsLong(value));
    return out;
  }

}
