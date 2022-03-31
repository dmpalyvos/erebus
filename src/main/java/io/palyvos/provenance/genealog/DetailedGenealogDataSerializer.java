package io.palyvos.provenance.genealog;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.palyvos.provenance.ananke.aggregate.ProvenanceAggregateStrategy;
import io.palyvos.provenance.util.TimestampedUIDTuple;
import java.sql.Time;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.apache.commons.lang3.Validate;

public class DetailedGenealogDataSerializer extends
    GenealogDataSerializer {

  private ProvenanceAggregateStrategy aggregateStrategy;
  private GenealogGraphTraverser genealogGraphTraverser;
  private static final byte SOURCE_TUPLE_CODE = 1;
  private static final byte REMOTE_TUPLE_CODE = 2;
  private static final byte META_SINK_TUPLE_CODE = 3;
  private static final byte META_SOURCE_TUPLE_CODE = 4;
  private static final byte SERIALIZED_MAP_CODE = 5;
  private static final byte SERIALIZED_AGGREGATE_CODE = 6;
  private static final byte SERIALIZED_JOIN_CODE = 7;

  public static GenealogDataSerializer newInstance(
      ProvenanceAggregateStrategy provenanceAggregateStrategy,
      String statisticsFolder,
      boolean graphTraversalStatistics, boolean detailedProvenance) {
    Validate.isTrue(!graphTraversalStatistics, "traversal statistics not supported!");
    return new DetailedGenealogDataSerializer(provenanceAggregateStrategy, detailedProvenance);
  }

  public DetailedGenealogDataSerializer(ProvenanceAggregateStrategy aggregateStrategy,
      boolean detailedProvenance) {
    this.genealogGraphTraverser = new GenealogGraphTraverser(aggregateStrategy, detailedProvenance);
    this.aggregateStrategy = aggregateStrategy;
  }

  public DetailedGenealogDataSerializer() {
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
        output.writeByte(SERIALIZED_MAP_CODE);
        kryo.writeClassAndObject(output, object.getU1());
        break;
      case JOIN:
        output.writeByte(SERIALIZED_JOIN_CODE);
        kryo.writeClassAndObject(output, object.getU1());
        kryo.writeClassAndObject(output, object.getU2());
        break;
      case AGGREGATE:
        output.writeByte(REMOTE_TUPLE_CODE);
        Set<TimestampedUIDTuple> aggregateProvenance = new HashSet<>();
        Iterator<GenealogTuple> it = aggregateStrategy.provenanceIterator(object);
        while (it.hasNext()) {
          aggregateProvenance.add(it.next());
        }
        kryo.writeObject(output, aggregateProvenance);
        break;
      case REMOTE:
        output.writeByte(REMOTE_TUPLE_CODE);
        kryo.writeObject(output, object.getProvenance());
        break;
      case META_SOURCE:
        output.writeByte(META_SOURCE_TUPLE_CODE);
        break;
      default:
        throw new IllegalStateException(
            "No rule to serialize tuple with type " + object.getTupleType());
    }
  }

  protected Set<TimestampedUIDTuple> getProvenance(GenealogData object) {
    return genealogGraphTraverser.getProvenance(object);
  }

  @Override
  public GenealogData read(Kryo kryo, Input input, Class<GenealogData> type) {
    final long uid = input.readLong();
    final int tupleCode = input.readByte();
    final GenealogData data = new GenealogData();
    switch (tupleCode) {
      case SOURCE_TUPLE_CODE:
        data.init(GenealogTupleType.SOURCE);
        break;
      case META_SINK_TUPLE_CODE:
        data.init(GenealogTupleType.META_SINK);
        break;
      case SERIALIZED_MAP_CODE:
        data.init(GenealogTupleType.MAP);
        data.setU1((GenealogTuple) kryo.readClassAndObject(input));
        break;
      case SERIALIZED_AGGREGATE_CODE:
        throw new UnsupportedOperationException();
      case SERIALIZED_JOIN_CODE:
        data.init(GenealogTupleType.JOIN);
        data.setU1((GenealogTuple) kryo.readClassAndObject(input));
        data.setU2((GenealogTuple) kryo.readClassAndObject(input));
        break;
      case REMOTE_TUPLE_CODE:
        data.init(GenealogTupleType.REMOTE);
        data.setProvenance((Collection<TimestampedUIDTuple>) kryo.readObject(input, HashSet.class));
        break;
      case META_SOURCE_TUPLE_CODE:
        data.init(GenealogTupleType.META_SOURCE);
        break;
      default:
        throw new IllegalStateException("Unknown tupleCode: " + tupleCode);
    }
    data.setUID(uid);
    return data;
  }
}
