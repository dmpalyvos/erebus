package io.palyvos.provenance.missing.util;

public enum ComponentCategory {
  @Deprecated
  FUTURE_CHECKER,
  @Deprecated
  QUERY_OPERATORS,
  @Deprecated
  META_OPERATORS,
  ALL;

  public boolean matches(ComponentCategory other) {
    return other == this || other == ALL;
  }
}
