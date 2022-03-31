package io.palyvos.provenance.ananke.util;

import java.io.Serializable;
import java.util.function.Function;
import org.apache.flink.streaming.api.watermark.Watermark;

public interface WatermarkTransform extends Function<Watermark, Watermark>, Serializable {

}
