package io.palyvos.provenance.ananke.functions.noop;

import org.apache.commons.lang3.Validate;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.typeutils.ResultTypeQueryable;

public class NoopKeySelectorWithTypeInfo<IN, KEY> extends
    NoopKeySelector<IN, KEY> implements
    ResultTypeQueryable<KEY> {

  private final TypeInformation<KEY> typeInfo;

  public NoopKeySelectorWithTypeInfo(
      KeySelector<IN, KEY> delegate, Class<KEY> clazz) {
    super(delegate);
    Validate.notNull(clazz);
    this.typeInfo = TypeInformation.of(clazz);
  }

  public NoopKeySelectorWithTypeInfo(KeySelector<IN, KEY> delegate, TypeInformation<KEY> typeInfo) {
    super(delegate);
    Validate.notNull(typeInfo);
    this.typeInfo = typeInfo;
  }

  @Override
  public TypeInformation<KEY> getProducedType() {
    return typeInfo;
  }
}
