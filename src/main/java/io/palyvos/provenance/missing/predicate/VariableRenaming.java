package io.palyvos.provenance.missing.predicate;

import io.palyvos.provenance.missing.util.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Renaming for a variable, including a series of transforms. The renaming implies that applying the
 * transforms to the name provided by the renaming will yield a value equal to that of the original
 * variable.
 */
public class VariableRenaming {

  private final String name;
  private final Path transformKeys;

  private final Path operators;
  private Optional<TransformFunction> transform = Optional.empty();

  public static VariableRenaming newInstance(String variable) {
    return new VariableRenaming(variable, Path.empty(), Path.empty());
  }


  VariableRenaming(String name, List<String> transformKeys, List<String> operators) {
    this(name, Path.of(transformKeys), Path.of(operators));
  }

  VariableRenaming(String name, Path transformKeys, Path operators) {
    Validate.notEmpty(name, "name");
    Validate.notNull(transformKeys, "transforms");
    Validate.notNull(operators, "operators");
    this.name = name;
    this.transformKeys = transformKeys;
    this.operators = operators;
  }

  VariableRenaming transformed(String newName, String transformKey, String operator) {
    return new VariableRenaming(newName, transformKeys.extendedIfNotEmpty(transformKey),
        operators.extended(operator));
  }

  public VariableRenaming withReverseTransforms(String newName) {
    return new VariableRenaming(newName, transformKeys.reversed(), operators.reversed());
  }

  public String name() {
    return name;
  }

  public Path transformKeys() {
    return transformKeys;
  }

  public Path operators() {
    return operators;
  }

  public Optional<TransformFunction> transform() {
    return this.transform;
  }

  void computeTransform(Map<String, TransformFunction> registeredTransforms) {
    this.transform = this.transformKeys.isEmpty()
        ? Optional.empty()
        : Optional.of(doComputeTransform(registeredTransforms));
  }

  private TransformFunction doComputeTransform(
      Map<String, TransformFunction> registeredTransforms) {
    Validate.isTrue(!transformKeys.isEmpty(),
        "No transform setup for this renaming. This is a bug!");
    TransformFunction compositeTransform = null;
    for (String transform : transformKeys) {
      TransformFunction currentTransform = registeredTransforms.get(transform);
      Validate.notNull(currentTransform, "No registration for variable transform with key: %s",
          transform);
      compositeTransform = compositeTransform == null ? currentTransform
          : andThen(compositeTransform, currentTransform);
    }
    return compositeTransform;
  }

  private TransformFunction andThen(TransformFunction current, TransformFunction after) {
    return (Object t) -> after.apply(current.apply(t));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    VariableRenaming that = (VariableRenaming) o;
    return name.equals(that.name) && transformKeys.equals(that.transformKeys)
        && operators.equals(
        that.operators);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, transformKeys, operators);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("name", name)
        .append("transformKeys", transformKeys)
        .append("operators", operators)
        .toString();
  }
}
