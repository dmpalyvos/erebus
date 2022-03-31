package io.palyvos.provenance.usecases.linearroad.noprovenance;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class AccidentTuple implements Serializable {

  public final Set<Long> vids = new HashSet<>();
  public int count;
  public long timestamp = -1;
  public long stimulus = -1;
  public int xWay;
  public int lane;
  public int dir;
  public int seg;
  public int pos;


  public void accumulate(VehicleTuple tuple) {
    timestamp = Math.max(timestamp, tuple.getTimestamp());
    stimulus = Math.max(stimulus, tuple.getStimulus());
    xWay = tuple.latestXWay;
    lane = tuple.latestLane;
    dir = tuple.latestDir;
    seg = tuple.latestSeg;
    pos = tuple.latestPos;
    vids.add(tuple.getVid());
    count = vids.size();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AccidentTuple that = (AccidentTuple) o;
    return count == that.count && timestamp == that.timestamp && stimulus == that.stimulus
        && xWay == that.xWay && lane == that.lane && dir == that.dir && seg == that.seg
        && pos == that.pos && vids.equals(that.vids);
  }

  @Override
  public int hashCode() {
    return Objects.hash(vids, count, timestamp, stimulus, xWay, lane, dir, seg, pos);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("vids", vids)
        .append("count", count)
        .append("xWay", xWay)
        .append("lane", lane)
        .append("dir", dir)
        .append("seg", seg)
        .append("pos", pos)
        .toString();
  }
}
