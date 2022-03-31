package io.palyvos.provenance.missing.predicate;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

final class UnsatisfiableCondition implements Condition {

  private final Condition original;
  private final String reason;

  UnsatisfiableCondition(Condition original, String reason) {
    this.original = original;
    this.reason = reason;
  }

  @Override
  public Collection<Variable> variables() {
    return Collections.emptyList();
  }

  @Override
  public boolean evaluate(long timestamp) {
    return false;
  }

  @Override
  public boolean isLoaded() {
    return true;
  }

  @Override
  public Condition renamed(Map<String, ? extends Collection<VariableRenaming>> renamings) {
    return this;
  }

  @Override
  public boolean isSatisfiable() {
    return false;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("reason", reason)
        .append("original", original)
        .toString();
  }
}
