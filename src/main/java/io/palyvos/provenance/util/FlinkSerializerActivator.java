package io.palyvos.provenance.util;

import com.esotericsoftware.kryo.Serializer;
import io.palyvos.provenance.ananke.functions.CustomGenericSerializer;
import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer;
import io.palyvos.provenance.ananke.functions.ProvenanceTupleContainer.KryoSerializer;
import io.palyvos.provenance.ananke.stdops.HelperProvenanceGraphTuple;
import io.palyvos.provenance.genealog.GenealogData;
import io.palyvos.provenance.genealog.GenealogDataSerializer;
import java.io.Serializable;
import java.util.Collection;
import org.apache.commons.lang3.Validate;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public enum FlinkSerializerActivator {
  NOPROVENANCE {
    @Override
    public SerializerRegistry activate(StreamExecutionEnvironment env,
        ExperimentSettings settings) {
      return new SerializerRegistry(env, null, null);
    }
  },
  PROVENANCE_OPTIMIZED {
    @Override
    public SerializerRegistry activate(StreamExecutionEnvironment env,
        ExperimentSettings settings) {

      final GenealogDataSerializer genealogDataSerializer = settings.genealogDataSerializer();
      env.addDefaultKryoSerializer(GenealogData.class, genealogDataSerializer);

      env.addDefaultKryoSerializer(
          HelperProvenanceGraphTuple.class, new HelperProvenanceGraphTuple.KryoSerializer());
      return new SerializerRegistry(env, genealogDataSerializer, null);
    }
  },
  PROVENANCE_TRANSPARENT {
    @Override
    public SerializerRegistry activate(StreamExecutionEnvironment env,
        ExperimentSettings settings) {
      final GenealogDataSerializer genealogDataSerializer = settings.genealogDataSerializer();
      env.addDefaultKryoSerializer(GenealogData.class, genealogDataSerializer);

      final CustomGenericSerializer customGenericSerializer = new CustomGenericSerializer();

      final KryoSerializer tupleContainerSerializer = new KryoSerializer(genealogDataSerializer,
          customGenericSerializer);
      env.addDefaultKryoSerializer(ProvenanceTupleContainer.class, tupleContainerSerializer);

      env.addDefaultKryoSerializer(
          HelperProvenanceGraphTuple.class,
          new HelperProvenanceGraphTuple.KryoSerializer(customGenericSerializer));

      return new SerializerRegistry(env, customGenericSerializer, genealogDataSerializer,
          tupleContainerSerializer);
    }
  };

  /**
   * Adapter that provides a uniform interface for registering serializers in both {@link
   * StreamExecutionEnvironment} and {@link CustomGenericSerializer}.
   */
  public static class SerializerRegistry {

    private final StreamExecutionEnvironment env;
    private final CustomGenericSerializer genericSerializer;
    private final GenealogDataSerializer genealogDataSerializer;
    private final ProvenanceTupleContainer.KryoSerializer containerSerializer;

    public SerializerRegistry(StreamExecutionEnvironment env,
        GenealogDataSerializer genealogDataSerializer,
        KryoSerializer containerSerializer) {
      this.env = env;
      this.genericSerializer = null;
      this.genealogDataSerializer = genealogDataSerializer;
      this.containerSerializer = containerSerializer;
    }

    public SerializerRegistry(
        StreamExecutionEnvironment env,
        CustomGenericSerializer genericSerializer,
        GenealogDataSerializer genealogDataSerializer,
        KryoSerializer containerSerializer) {
      this.env = null;
      this.genericSerializer = genericSerializer;
      this.genealogDataSerializer = genealogDataSerializer;
      this.containerSerializer = containerSerializer;
    }

    public <T, S extends Serializer<T> & Serializable> SerializerRegistry register(Class<T> clazz,
        S serializer) {
      if (genericSerializer != null) {
        genericSerializer.register(clazz, serializer);
      }
      if (env != null) {
        env.addDefaultKryoSerializer(clazz, serializer);
      }
      return this;
    }

    public SerializerRegistry registerOperators(Collection<String> operators) {
      Validate.notNull(containerSerializer,
          "Tried to register operators but ProvenanceTupleContainer#serializer is null");
      Validate.notEmpty(operators, "No operator provided");
      containerSerializer.registerOperators(operators.toArray(new String[0]));
      return this;
    }

    public GenealogDataSerializer genealogDataSerializer() {
      return genealogDataSerializer;
    }
  }

  public abstract SerializerRegistry activate(StreamExecutionEnvironment env,
      ExperimentSettings settings);
}
