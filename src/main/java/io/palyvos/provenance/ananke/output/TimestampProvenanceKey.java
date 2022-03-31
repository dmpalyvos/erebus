package io.palyvos.provenance.ananke.output;

import org.apache.commons.lang3.Validate;

public class TimestampProvenanceKey extends ProvenanceKey {

  private final long timestamp;

  TimestampProvenanceKey(long timestamp) {
    // Flink watermarks sometimes start from -1
    Validate.isTrue(timestamp >= -1, "timestamp < -1");
    this.timestamp = timestamp;
  }

  @Override
  public long timestamp() {
    return timestamp;
  }

  @Override
  public long uid() {
    return Long.MAX_VALUE;
  }
}
