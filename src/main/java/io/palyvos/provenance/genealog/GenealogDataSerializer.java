package io.palyvos.provenance.genealog;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.io.Serializable;

public abstract class GenealogDataSerializer extends Serializer<GenealogData> implements Serializable {

}
