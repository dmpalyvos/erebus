package io.palyvos.provenance.usecases.linearroad.noprovenance;


import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.palyvos.provenance.util.TimestampedTuple;
import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class LinearRoadInputTuple implements TimestampedTuple {

  private static final Pattern DELIMITER_PATTERN = Pattern.compile(",");
  public long timestamp;
  public long stimulus;
  public int type;
  public long vid;
  public int speed;
  public int xway;
  public int lane;
  public int dir;
  public int seg;
  public int pos;

  public static LinearRoadInputTuple fromReading(String reading) {
    try {
      String[] tokens = DELIMITER_PATTERN.split(reading.trim());
      return new LinearRoadInputTuple(tokens);
    } catch (Exception exception) {
      throw new IllegalArgumentException(String.format(
          "Failed to parse reading: %s", reading), exception);
    }
  }

  protected LinearRoadInputTuple(String[] readings) {
    this(Integer
            .valueOf(readings[0]), TimeUnit.SECONDS.toMillis(Long.valueOf(readings[1])),
        Integer.valueOf(readings[2]), Integer
            .valueOf(readings[3]), Integer
            .valueOf(readings[4]), Integer
            .valueOf(readings[5]), Integer
            .valueOf(readings[6]), Integer
            .valueOf(readings[7]), Integer
            .valueOf(readings[8]), System.currentTimeMillis());
  }

  protected LinearRoadInputTuple(int type, long timestamp, long vid, int speed,
      int xway, int lane, int dir, int seg, int pos, long stimulus) {
    this.type = type;
    this.vid = vid;
    this.speed = speed;
    this.xway = xway;
    this.lane = lane;
    this.dir = dir;
    this.seg = seg;
    this.pos = pos;
    this.timestamp = timestamp;
    this.stimulus = stimulus;
  }

  public int getType() {
    return type;
  }

  public void setType(int type) {
    this.type = type;
  }

  public long getVid() {
    return vid;
  }

  public void setVid(long vid) {
    this.vid = vid;
  }

  public int getSpeed() {
    return speed;
  }

  public void setSpeed(int speed) {
    this.speed = speed;
  }

  public int getXway() {
    return xway;
  }

  public void setXway(int xway) {
    this.xway = xway;
  }

  public int getLane() {
    return lane;
  }

  public void setLane(int lane) {
    this.lane = lane;
  }

  public int getDir() {
    return dir;
  }

  public void setDir(int dir) {
    this.dir = dir;
  }

  public int getSeg() {
    return seg;
  }

  public void setSeg(int seg) {
    this.seg = seg;
  }

  public int getPos() {
    return pos;
  }

  public void setPos(int pos) {
    this.pos = pos;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LinearRoadInputTuple that = (LinearRoadInputTuple) o;
    return timestamp == that.timestamp && stimulus == that.stimulus && type == that.type
        && vid == that.vid && speed == that.speed && xway == that.xway && lane == that.lane
        && dir == that.dir && seg == that.seg && pos == that.pos;
  }

  @Override
  public int hashCode() {
    return Objects.hash(timestamp, vid);
  }

  @Override
  public long getTimestamp() {
    return timestamp;
  }

  @Override
  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  @Override
  public long getStimulus() {
    return stimulus;
  }

  @Override
  public void setStimulus(long stimulus) {
    this.stimulus = stimulus;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("type", type)
        .append("vid", vid)
        .append("speed", speed)
        .append("xway", xway)
        .append("lane", lane)
        .append("dir", dir)
        .append("seg", seg)
        .append("pos", pos)
        .toString();
  }

  public static class KryoSerializer extends Serializer<LinearRoadInputTuple>
      implements Serializable {

    @Override
    public void write(Kryo kryo, Output output, LinearRoadInputTuple object) {
      output.writeInt(object.type);
      output.writeLong(object.timestamp);
      output.writeLong(object.vid);
      output.writeInt(object.speed);
      output.writeInt(object.xway);
      output.writeInt(object.lane);
      output.writeInt(object.dir);
      output.writeInt(object.seg);
      output.writeInt(object.pos);
      output.writeLong(object.stimulus);
    }

    @Override
    public LinearRoadInputTuple read(Kryo kryo, Input input, Class<LinearRoadInputTuple> type) {
      return new LinearRoadInputTuple(input.readInt(), input.readLong(), input.readLong(),
          input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt(),
          input.readInt(), input.readLong());
    }
  }
}
