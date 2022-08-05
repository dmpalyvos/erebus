package io.palyvos.provenance.missing.integration;

import org.apache.commons.lang3.builder.ToStringBuilder;

class TwoVarTestTuple {

  public int A;
  public int B;

  public TwoVarTestTuple(int a, int b) {
    A = a;
    B = b;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("A", A)
        .append("B", B)
        .toString();
  }
}
