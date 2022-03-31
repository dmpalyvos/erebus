package io.palyvos.provenance.ananke.output;

import io.palyvos.provenance.util.TimestampedUIDTuple;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import org.apache.commons.lang3.Validate;

public class PickedProvenanceFileProvenanceGraphEncoder implements ProvenanceGraphEncoder,
    Serializable {

  public static final String TYPE_SOURCE = "SOURCE";
  protected final PrintWriter writer;

  public PickedProvenanceFileProvenanceGraphEncoder(String outputFile, boolean autoFlush) {
    try {
      writer = new PrintWriter(new FileWriter(outputFile), autoFlush);
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Override
  public void sourceVertex(TimestampedUIDTuple tuple, long streamTimestamp, long dataTimestamp) {
    writer.println(vertexString(TYPE_SOURCE, dataTimestamp, tuple));
  }

  @Override
  public void sourceVertex(long uid, String tuple) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void sinkVertex(TimestampedUIDTuple tuple, long streamTimestamp, long dataTimestamp) {
    writer.println(vertexString(tuple.operator(), dataTimestamp, tuple));
  }

  @Override
  public void sinkVertex(long uid, String tuple) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void edge(long sourceUID, long sinkUID, long timestamp) {
    writer.println("EDGE ::: " + sourceUID + " -> " + sinkUID);
  }

  @Override
  public void ack(long uid, long timestamp) {
    writer.println("ACK ::: " + uid);
  }

  @Override
  public void debug(String message) {
    writer.println(message);
  }

  @Override
  public void close() {
    writer.flush();
    writer.close();
  }

  public String vertexString(String type, long ts, TimestampedUIDTuple tuple) {
    Validate.notNull(tuple);
    return vertexString(type, tuple.getUID(), ts, String.valueOf(tuple));
  }

  private String vertexString(String type, long uid, long ts, String tuple) {
    return type + " ::: " + uid + " ::: " + ts + " ::: " + tuple;
  }

}
