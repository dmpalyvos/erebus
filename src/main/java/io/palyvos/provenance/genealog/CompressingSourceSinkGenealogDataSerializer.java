package io.palyvos.provenance.genealog;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.palyvos.provenance.ananke.aggregate.ProvenanceAggregateStrategy;
import io.palyvos.provenance.util.TimestampedUIDTuple;
import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;

@Deprecated
public class CompressingSourceSinkGenealogDataSerializer extends GenealogDataSerializer {

  public static final int COMPRESSION_PROVENANCE_SIZE_LIMIT = 50;
  private GenealogGraphTraverser genealogGraphTraverser;
  private static final byte SOURCE_TUPLE_CODE = 1;
  private static final byte REMOTE_TUPLE_CODE = 2;
  private static final byte META_SINK_TUPLE_CODE = 3;
  private static final byte META_SOURCE_TUPLE_CODE = 4;
  private static final byte COMPRESSED_REMOTE_TUPLE_CODE = 5;

  public static GenealogDataSerializer newInstance(
      ProvenanceAggregateStrategy provenanceAggregateStrategy,
      String statisticsFolder,
      boolean graphTraversalStatistics, boolean detailedProvenance) {
    return (graphTraversalStatistics)
        ? new SourceSinkGenealogDataSerializerWithStatistics(provenanceAggregateStrategy,
        statisticsFolder,
        detailedProvenance)
        : new CompressingSourceSinkGenealogDataSerializer(provenanceAggregateStrategy,
            detailedProvenance);
  }

  public CompressingSourceSinkGenealogDataSerializer(ProvenanceAggregateStrategy aggregateStrategy,
      boolean detailedProvenance) {
    this.genealogGraphTraverser = new GenealogGraphTraverser(aggregateStrategy, detailedProvenance);
  }

  public CompressingSourceSinkGenealogDataSerializer() {
    // For serialization/deserialization purposes
  }

  @Override
  public void write(Kryo kryo, Output output, GenealogData object) {
    output.writeLong(object.getUID());
    switch (object.getTupleType()) {
      case SOURCE:
        output.writeByte(SOURCE_TUPLE_CODE);
        break;
      case META_SINK:
        output.writeByte(META_SINK_TUPLE_CODE);
        break;
      case MAP:
      case AGGREGATE:
      case JOIN:
      case REMOTE:
        final Set<TimestampedUIDTuple> provenance = getProvenance(object);
        if (provenance.size() > COMPRESSION_PROVENANCE_SIZE_LIMIT) {
          byte[] cbytes = getCompressedProvenanceByteArray(kryo, provenance);
          output.writeByte(COMPRESSED_REMOTE_TUPLE_CODE);
          output.writeInt(cbytes.length);
          output.writeBytes(cbytes);
        } else {
          output.writeByte(REMOTE_TUPLE_CODE);
          kryo.writeObject(output, provenance);
        }
        break;
      case META_SOURCE:
        output.writeByte(META_SOURCE_TUPLE_CODE);
        break;
      default:
        throw new IllegalStateException(
            "No rule to serialize tuple with type " + object.getTupleType());
    }
  }

  private byte[] getCompressedProvenanceByteArray(Kryo kryo, Set<TimestampedUIDTuple> provenance) {
    Output compressed = new Output(1024, -1);
    kryo.writeObject(compressed, provenance);
    byte[] cbytes = compress(compressed.getBuffer());
    return cbytes;
  }

  public static byte[] compress(byte[] in) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      DeflaterOutputStream defl = new DeflaterOutputStream(out, new Deflater(Deflater.BEST_SPEED));
      defl.write(in);
      defl.flush();
      defl.close();
      return out.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static byte[] decompress(byte[] in) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      InflaterOutputStream infl = new InflaterOutputStream(out);
      infl.write(in);
      infl.flush();
      infl.close();
      return out.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected Set<TimestampedUIDTuple> getProvenance(GenealogData object) {
    return genealogGraphTraverser.getProvenance(object);
  }

  @Override
  public GenealogData read(Kryo kryo, Input input, Class<GenealogData> type) {
    final long uid = input.readLong();
    final int tupleCode = input.readByte();
    GenealogTupleType tupleType;
    Collection<TimestampedUIDTuple> provenance = null;
    switch (tupleCode) {
      case SOURCE_TUPLE_CODE:
        tupleType = GenealogTupleType.SOURCE;
        break;
      case META_SINK_TUPLE_CODE:
        tupleType = GenealogTupleType.META_SINK;
        break;
      case REMOTE_TUPLE_CODE:
        tupleType = GenealogTupleType.REMOTE;
        provenance = (Collection<TimestampedUIDTuple>) kryo.readObject(input, HashSet.class);
        break;
      case COMPRESSED_REMOTE_TUPLE_CODE:
        tupleType = GenealogTupleType.REMOTE;
        int l = input.readInt();
        byte[] cb = decompress(input.readBytes(l));
        Input compressedInput = new Input(cb);
        provenance = (Collection<TimestampedUIDTuple>) kryo.readObject(compressedInput, HashSet.class);
        break;
      case META_SOURCE_TUPLE_CODE:
        tupleType = GenealogTupleType.META_SOURCE;
        break;
      default:
        throw new IllegalStateException("Unknown tupleCode: " + tupleCode);
    }
    final GenealogData data = new GenealogData();
    data.init(tupleType);
    data.setUID(uid);
    data.setProvenance(provenance);
    return data;
  }
}
