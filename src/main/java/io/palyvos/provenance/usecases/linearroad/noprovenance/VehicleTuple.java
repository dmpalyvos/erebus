package io.palyvos.provenance.usecases.linearroad.noprovenance;


import io.palyvos.provenance.util.BaseTuple;
import java.util.Objects;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class VehicleTuple extends BaseTuple {

	public long vid;
	public int reports;
	public int latestXWay;
	public int latestLane;
	public int latestDir;
	public int latestSeg;
	public int latestPos;
	public boolean uniquePosition;


	public VehicleTuple(long timestamp, long vid, int reports, int xway, int lane,
			int dir, int seg, int pos, boolean uniquePosition, long stimulus) {
	  super(timestamp, xway + "," + lane + "," + dir + "," + seg + "," + pos, stimulus);
		this.vid = vid;
		this.reports = reports;
		this.latestXWay = xway;
		this.latestLane = lane;
		this.latestDir = dir;
		this.latestSeg = seg;
		this.latestPos = pos;
		this.uniquePosition = uniquePosition;
	}

  public long getVid() {
    return vid;
  }

  public int getReports() {
    return reports;
  }

  public int getLatestXWay() {
    return latestXWay;
  }

  public int getLatestLane() {
    return latestLane;
  }

  public int getLatestDir() {
    return latestDir;
  }

  public int getLatestSeg() {
    return latestSeg;
  }

  public int getLatestPos() {
    return latestPos;
  }

  public boolean isUniquePosition() {
    return uniquePosition;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    VehicleTuple that = (VehicleTuple) o;
    return vid == that.vid &&
        reports == that.reports &&
        latestXWay == that.latestXWay &&
        latestLane == that.latestLane &&
        latestDir == that.latestDir &&
        latestSeg == that.latestSeg &&
        latestPos == that.latestPos &&
        uniquePosition == that.uniquePosition;
  }

  @Override
  public int hashCode() {
    return Objects
        .hash(super.hashCode(), vid, reports, latestXWay, latestLane, latestDir, latestSeg,
            latestPos,
            uniquePosition);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
        .append("vid", vid)
        .append("reports", reports)
        .append("latestXWay", latestXWay)
        .append("latestLane", latestLane)
        .append("latestDir", latestDir)
        .append("latestSeg", latestSeg)
        .append("latestPos", latestPos)
        .append("uniquePosition", uniquePosition)
        .toString();
  }
}
