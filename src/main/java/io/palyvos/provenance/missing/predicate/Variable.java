package io.palyvos.provenance.missing.predicate;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface Variable extends Serializable {

  void setValue(Object value, boolean isLoaded);

  void load(Object object);

  List<Variable> renamed(Map<String, ? extends Collection<VariableRenaming>> renamings);

  Object asObject();

  boolean isLoaded();

  boolean asBoolean();

  int asInt();

  long asLong();

  double asDouble();

  String asString();

  String id();

  void clear();
}
