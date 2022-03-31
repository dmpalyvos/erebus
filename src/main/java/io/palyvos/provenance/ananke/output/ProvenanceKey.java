package io.palyvos.provenance.ananke.output;

import io.palyvos.provenance.util.TimestampConverter;
import io.palyvos.provenance.util.TimestampedUIDTuple;
import java.util.Comparator;

public abstract class ProvenanceKey implements Comparable<ProvenanceKey> {

  private static final Comparator keyComparator =
      Comparator.comparingLong(ProvenanceKey::timestamp).thenComparing(ProvenanceKey::uid);

  public static TupleProvenanceKey ofTuple(
      TimestampedUIDTuple sourceTuple, TimestampConverter timestampConverter, long stimulus) {
    return new TupleProvenanceKey(sourceTuple, timestampConverter, stimulus);
  }

  public static TimestampProvenanceKey ofTimestamp(long convertedTimestamp) {
    return new TimestampProvenanceKey(convertedTimestamp);
  }

  public abstract long timestamp();

  public abstract long uid();

  @Override
  public int compareTo(ProvenanceKey o) {
    return keyComparator.compare(this, o);
  }
}
