package io.palyvos.provenance.missing.predicate;

import java.util.ArrayList;
import java.util.Collections;
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
  private final List<String> transformKeys;
  private Optional<TransformFunction> transform = Optional.empty();

  public static VariableRenaming of(String variable) {
    return new VariableRenaming(variable, new ArrayList<>());
  }

  VariableRenaming(String name, List<String> transformKeys) {
    Validate.notEmpty(name, "name");
    Validate.notNull(transformKeys, "transforms");
    this.name = name;
    this.transformKeys = new ArrayList<>(transformKeys);
  }

  VariableRenaming transformed(String newName, String transformKey) {
    VariableRenaming renaming = new VariableRenaming(newName, this.transformKeys);
    if (transformKey != null && !transformKey.isEmpty()) {
      renaming.transformKeys.add(transformKey);
    }
    return renaming;
  }

  public VariableRenaming withReverseTransforms(String newName) {
    VariableRenaming renaming = new VariableRenaming(newName, this.transformKeys);
    Collections.reverse(renaming.transformKeys);
    return renaming;
  }

  public String name() {
    return name;
  }

  public List<String> transformkeys() {
    return Collections.unmodifiableList(transformKeys);
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
    Validate.notEmpty(transformKeys, "No transform setup for this renaming. This is a bug!");
    TransformFunction compositeTransform = null;
    for (String transform : transformkeys()) {
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
    VariableRenaming renaming = (VariableRenaming) o;
    return name.equals(renaming.name) && transformKeys.equals(renaming.transformKeys);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, transformKeys);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SIMPLE_STYLE)
        .append("name", name)
        .append("transformKeys", transformKeys)
        .toString();
  }
}
