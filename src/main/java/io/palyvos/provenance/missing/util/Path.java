package io.palyvos.provenance.missing.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.Validate;

public class Path implements Serializable, Iterable<String> {

  private final List<String> components;

  public static Path empty() {
    return new Path(Collections.emptyList());
  }

  public static Path of(List<String> components) {
    Validate.notEmpty(components, "at least one component required, otherwise call empty()");
    for (String component : components) {
      Validate.notEmpty(component, "Empty components not allowed in path: %s", component);
    }
    return new Path(components);
  }

  public static Path of(String... components) {
    Validate.notEmpty(components, "at least one component required, otherwise call empty()");
    return Path.of(Arrays.asList(components));
  }

  protected Path(List<String> components) {
    Validate.notNull(components, "components");
    this.components = Collections.unmodifiableList(components);
  }

  public Path extended(String component) {
    Validate.notEmpty(component, "empty component");
    final List<String> extendedComponents = new ArrayList<>(components);
    extendedComponents.add(component);
    return new Path(extendedComponents);
  }

  public Path extendedIfNotEmpty(String component) {
    final boolean isEmpty = component == null || component.isEmpty();
    return isEmpty ? this : extended(component);
  }

  public Path reversed() {
    final List<String> reversedComponents = new ArrayList<>(components);
    Collections.reverse(reversedComponents);
    return new Path(reversedComponents);
  }

  public Collection<String> components() {
    return components;
  }

  public boolean isEmpty() {
    return components.isEmpty();
  }

  @Override
  public Iterator<String> iterator() {
    return components.iterator();
  }

  public String at(int index) {
    return components.get(index);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Path path = (Path) o;
    return components.equals(path.components);
  }

  @Override
  public int hashCode() {
    return Objects.hash(components);
  }

  @Override
  public String toString() {
    return "Path[" + components.stream().collect(Collectors.joining("->")) + "]";
  }
}
