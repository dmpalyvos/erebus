package io.palyvos.provenance.missing.predicate;

import io.palyvos.provenance.missing.predicate.Predicate.PredicateStrategy;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class OneVariableCondition implements Condition {

  protected final Variable var;
  private final Collection<Variable> variables;
  private final OneVariableFunction function;

  public OneVariableCondition(Variable var, OneVariableFunction function) {
    Validate.notNull(var, "var");
    Validate.notNull(function);
    this.var = var;
    this.function = function;
    this.variables = Collections.unmodifiableCollection(Arrays.asList(var));
  }

  @Override
  public final boolean evaluate(long timestamp) {
    return function.evaluate(var);
  }

  @Override
  public final boolean isLoaded() {
    return var.isLoaded();
  }

  @Override
  public final Collection<Variable> variables() {
    return variables;
  }

  @Override
  public final Condition renamed(Map<String, ? extends Collection<VariableRenaming>> renamings) {
    List<Condition> renamed = var.renamed(renamings).stream().
        map(v -> new OneVariableCondition(v, function))
        .collect(Collectors.toList());
    Validate.notEmpty(renamed, "Condition renaming failed!");
    if (renamed.size() == 1) {
      return renamed.get(0);
    }
    return Predicate.of(PredicateStrategy.AND, renamed.toArray(new Condition[0]));
  }


  @Override
  public String description() {
    return "OneVariableCondition [var=" + var.id() + "]";
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("var", var)
        .toString();
  }
}
