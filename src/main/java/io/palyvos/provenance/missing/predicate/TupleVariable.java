package io.palyvos.provenance.missing.predicate;

import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Helper class representing variable that is nested inside {@link ProvenanceTupleContainer#tuple()}.
 */
public class TupleVariable implements Variable {

  private final Variable variable;
  private final String id;

  public static Variable fromField(String field) {
    return new TupleVariable(ReflectionVariable.fromField(field));
  }

  public static Variable fromMethod(String method) {
    return new TupleVariable(ReflectionVariable.fromMethod(method));
  }

  public TupleVariable(Variable variable) {
    Validate.notNull(variable, "variable");
    this.variable = variable;
    this.id = "tuple." + variable.id();
  }

  @Override
  public void setValue(Object value, boolean isLoaded) {
    variable.setValue(value, isLoaded);
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public void clear() {
    this.variable.clear();
  }

  @Override
  public void load(Object object) {
    variable.load(((ProvenanceTupleContainer) object).tuple());
  }

  @Override
  public List<Variable> renamed(Map<String, ? extends Collection<VariableRenaming>> renamings) {
    return variable.renamed(renamings).stream()
        .map(v -> new TupleVariable(v))
        .collect(Collectors.toList());
  }

  @Override
  public boolean asBoolean() {
    return variable.asBoolean();
  }

  @Override
  public int asInt() {
    return variable.asInt();
  }

  @Override
  public long asLong() {
    return variable.asLong();
  }

  @Override
  public double asDouble() {
    return variable.asDouble();
  }

  @Override
  public String asString() {
    return variable.asString();
  }

  @Override
  public Object asObject() {
    return variable.asObject();
  }

  @Override
  public boolean isLoaded() {
    return variable.isLoaded();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("id", id)
        .append("variable", variable)
        .toString();
  }
}
