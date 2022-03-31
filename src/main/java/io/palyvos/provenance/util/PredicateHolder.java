package io.palyvos.provenance.util;

import io.palyvos.provenance.missing.predicate.Predicate;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.Validate;

public interface PredicateHolder {

  Class<?> query();

  default Predicate get(String name) {
    Validate.notBlank(name, "blank predicate name");
    final Predicate predicate = predicates().get(name);
    Validate.validState(predicate != null, "no predicate with name %s\nAvailable predicates: ",
        name, predicates());
    return predicate;
  }

  Map<String, Predicate> predicates();


  default Map<String, Predicate> basicPredicates() {
    Map<String, Predicate> predicates = new HashMap<>();
    predicates.put("none", Predicate.disabled()); // No discarded tuple forwarded
    predicates.put("true", Predicate.alwaysTrue()); // All discarded tuples forwarded and outputted
    return predicates;
  }


}
