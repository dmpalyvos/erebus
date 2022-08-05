package io.palyvos.provenance.missing.predicate;

import java.util.Collection;
import java.util.Map;

public interface VariableCondition extends Condition {

  Condition renamed(Map<String, ? extends Collection<VariableRenaming>> renamings);

}
