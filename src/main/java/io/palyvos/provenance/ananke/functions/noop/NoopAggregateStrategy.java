package io.palyvos.provenance.ananke.functions.noop;

import io.palyvos.provenance.ananke.aggregate.ProvenanceAggregateStrategy;
import io.palyvos.provenance.genealog.GenealogData;
import io.palyvos.provenance.genealog.GenealogTuple;
import java.util.Collections;
import java.util.Iterator;

class NoopAggregateStrategy implements
    ProvenanceAggregateStrategy {

  @Override
  public <T extends GenealogTuple> void addWindowProvenance(T in) {
  }

  @Override
  public <T extends GenealogTuple> void annotateWindowResult(T result) {
    result.initGenealog(NoopProvenanceFunctionFactory.NO_PROVENANCE_TUPLE_TYPE);
  }

  @Override
  public <T extends GenealogTuple> Iterator<GenealogTuple> provenanceIterator(T tuple) {
    return Collections.emptyIterator();
  }

  @Override
  public Iterator<GenealogTuple> provenanceIterator(GenealogData genealogData) {
    return Collections.emptyIterator();
  }
}
