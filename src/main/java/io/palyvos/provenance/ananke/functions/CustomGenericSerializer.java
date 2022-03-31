package io.palyvos.provenance.ananke.functions;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.Validate;

public class CustomGenericSerializer implements Serializable {

  public static class MetaSerializer implements Serializable {

    public final Serializer serializer;
    public final int code;
    public final Class clazz;

    public MetaSerializer(Serializer serializer, int code, Class clazz) {
      this.serializer = serializer;
      this.code = code;
      this.clazz = clazz;
    }
  }

  public static final byte DEFAULT_SERIALIZATION_CODE = Byte.MIN_VALUE;
  public static final int MIN_CLASS_CODE = DEFAULT_SERIALIZATION_CODE + 1;
  private final Map<Class, MetaSerializer> classIndexedMetaSerializers = new HashMap<>();
  private final Map<Integer, MetaSerializer> codeIndexedMetaSerializers = new HashMap<>();
  private final AtomicInteger codeAssigner = new AtomicInteger(MIN_CLASS_CODE);
  private MetaSerializer last;

  public <T, S extends Serializer<T> & Serializable> void register(Class<T> generic, S serializer) {
    int classCode = codeAssigner.getAndIncrement();
    Validate.isTrue(classCode < Byte.MAX_VALUE);
    final MetaSerializer metaSerializer = new MetaSerializer(serializer, classCode, generic);
    classIndexedMetaSerializers.put(generic, metaSerializer);
    codeIndexedMetaSerializers.put(classCode, metaSerializer);
  }

  public void write(Kryo kryo, Output output, Object object) {
    MetaSerializer lastSerializer = last; // In case of concurrent alterations
    // We can avoid map retrieval if serializer is only writing one kind of object
    MetaSerializer meta;
    final Class<?> objectClass = object.getClass();
    if ((lastSerializer != null) && (lastSerializer.clazz == objectClass)) {
      meta = lastSerializer;
    } else {
      meta = classIndexedMetaSerializers.get(objectClass);
    }
    if (meta != null) {
      output.writeByte(meta.code);
      meta.serializer.write(kryo, output, object);
      last = meta;
    } else {
      output.writeByte(DEFAULT_SERIALIZATION_CODE);
      kryo.writeClassAndObject(output, object);
    }
  }

  public Object read(Kryo kryo, Input input) {
    int classCode = input.readByte();
    if (classCode == DEFAULT_SERIALIZATION_CODE) {
      return kryo.readClassAndObject(input);
    } else {
      final MetaSerializer meta = codeIndexedMetaSerializers.get(classCode);
      return meta.serializer.read(kryo, input, meta.clazz);
    }
  }
}
