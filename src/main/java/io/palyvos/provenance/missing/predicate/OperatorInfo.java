package io.palyvos.provenance.missing.predicate;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.apache.commons.lang3.Validate;

class OperatorInfo {

  final String id;
  final Map<String, String> renamings = new HashMap<>();
  final Collection<String> downstreamOperators = new HashSet<>();
  final Map<String, String> transforms = new HashMap<>();
  long windowSize;
  long windowAdvance = -1;

  public OperatorInfo(String id) {
    Validate.notBlank(id, "operator id");
    this.id = id;
  }

  long windowAdvance() {
    if (windowSize == 0) {
      // If stateless, then windowAdvance should be ignored
      return -1;
    }
    // If windowAdvance not set, then assume we have tumbling windows
    // and windowAdvance == windowSize
    return windowAdvance > 1 ? windowAdvance : windowSize;
  }
}
