package io.palyvos.provenance.missing.predicate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class NestedVariable implements Variable {

  private final Variable[] variables;
  private Object value;
  private boolean isLoaded;
  private final String id;

  public static Variable of(Variable... variables) {
    return new NestedVariable(variables);
  }

  protected NestedVariable(Variable... variables) {
    Validate.notEmpty(variables, "variables");
    Validate.isTrue(variables.length > 1, "No point in NestedVariable with one variable");
    for (Variable var : variables) {
      Validate.isTrue(!(var instanceof NestedVariable),
          "NestedVariable cannot contain NestedVariables");
    }
    this.variables = variables;
    this.id = Arrays.stream(variables).map(var -> var.id()).collect(Collectors.joining("."));
  }

  @Override
  public void setValue(Object value, boolean isLoaded) {
    this.value = value;
    this.isLoaded = isLoaded;
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public void clear() {
    for (Variable variable : variables) {
      variable.clear();
    }
  }

  @Override
  public void load(Object object) {
    isLoaded = false;
    Object prev = object;
    for (Variable var : variables) {
      var.load(prev);
      prev = var.asObject();
    }
    this.value = prev;
    isLoaded = true;
  }

  @Override
  public List<Variable> renamed(Map<String, ? extends Collection<VariableRenaming>> renamings) {
    List<Variable> renamed = new ArrayList<>();
    doRename(new ArrayList<>(), variables[0], Arrays.asList(variables).subList(1, variables.length),
        renamings, renamed);
    return renamed;
  }

  private void doRename(List<Variable> left, Variable current, List<Variable> right,
      Map<String, ? extends Collection<VariableRenaming>> renaming, List<Variable> result) {
    List<Variable> currentRenamed = current.renamed(renaming);
    for (Variable newCurrent : currentRenamed) {
      if (right.isEmpty()) {
        addRenaming(left, result, newCurrent);
        continue;
      }
      final List<Variable> newLeft = ListUtils.union(left, Arrays.asList(newCurrent));
      final List<Variable> newRight =
          right.size() > 0 ? right.subList(1, right.size()) : Collections.emptyList();
      doRename(newLeft, right.get(0), newRight, renaming, result);
    }
  }

  private void addRenaming(List<Variable> left, List<Variable> result, Variable newCurrent) {
    final Variable[] variableSequence = ((List<Variable>) ListUtils.union(
        left, Arrays.asList(newCurrent)))
        .toArray(new Variable[0]);
    result.add(new NestedVariable(variableSequence));
    return;
  }


  @Override
  public boolean isLoaded() {
    return isLoaded;
  }

  @Override
  public boolean asBoolean() {
    return (boolean) value;
  }

  @Override
  public int asInt() {
    return (int) value;
  }

  @Override
  public long asLong() {
    return (long) value;
  }

  @Override
  public double asDouble() {
    return (double) value;
  }

  @Override
  public String asString() {
    return (String) value;
  }

  @Override
  public Object asObject() {
    return value;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("id", id)
        .append("value", value)
        .append("isLoaded", isLoaded)
        .toString();
  }
}
