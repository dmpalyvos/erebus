package io.palyvos.provenance.missing.predicate;

import io.palyvos.provenance.missing.predicate.Predicate.PredicateStrategy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class TwoVariableCondition implements Condition {

  protected final Variable var1;
  protected final Variable var2;
  private final TwoVariableFunction function;
  private final Collection<Variable> variables;

  public TwoVariableCondition(Variable var1, Variable var2,
      TwoVariableFunction function) {
    Validate.notNull(var1, "var1");
    Validate.notNull(var2, "var2");
    Validate.notNull(function, "function");
    this.var1 = var1;
    this.var2 = var2;
    this.function = function;
    this.variables = Collections.unmodifiableCollection(Arrays.asList(var1, var2));
  }

  @Override
  public final boolean evaluate(long timestamp) {
    return function.evaluate(var1, var2);
  }

  @Override
  public final boolean isLoaded() {
    return var1.isLoaded() && var2.isLoaded();
  }

  @Override
  public final Collection<Variable> variables() {
    return variables;
  }

  @Override
  public final Condition renamed(Map<String, ? extends Collection<VariableRenaming>> renamings) {
    List<Condition> renamed = new ArrayList<>();
    for (Variable v1 : var1.renamed(renamings)) {
      for (Variable v2 : var2.renamed(renamings)) {
        renamed.add(new TwoVariableCondition(v1, v2, function));
      }
    }
    Validate.notEmpty(renamed, "Condition renaming failed!");
    if (renamed.size() == 1) {
      return renamed.get(0);
    }
    return Predicate.of(PredicateStrategy.AND, renamed.toArray(new Condition[0]));
  }

  @Override
  public String description() {
    return "TwoVariableCondition [var1=" + var1.id() + ", var2=" + var2.id() + "]";
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("var1", var1)
        .append("var2", var2)
        .toString();
  }
}
