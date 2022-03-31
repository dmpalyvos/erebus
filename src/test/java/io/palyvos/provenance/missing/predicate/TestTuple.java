package io.palyvos.provenance.missing.predicate;

class TestTuple {

  public static final long TIMESTAMP = 100;
  private long stimulus = -1;
  public long otherAttribute = 123;
  public long timestamp = TIMESTAMP;
  private Nested nested = new Nested(5, 10);
  private Nested nested2 = new Nested(6, 111);

  TestTuple() {
    nested.doubleNested = new Nested(151, 141);
  }

  public long getTimestamp() {
    return timestamp;
  }

  public long getStimulus() {
    return stimulus;
  }

  public void setStimulus(long stimulus) {
    this.stimulus = stimulus;
  }

  public Nested getNested() {
    return nested;
  }

  public Nested getNested2() {
    return nested2;
  }

  static class Nested {

    public int f0;
    public long f1;
    public Nested doubleNested;


    public Nested(int f0, long f1) {
      this.f0 = f0;
      this.f1 = f1;
    }
  }
}
