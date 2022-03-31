package io.palyvos.provenance.util;

import java.io.Serializable;
import java.util.function.ToLongFunction;

@FunctionalInterface
public interface LongExtractor<T> extends ToLongFunction<T>, Serializable {


}
