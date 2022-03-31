package io.palyvos.provenance.util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/** Record integer selectivity as #outputs per UNIT inputs to a file*/
public class SelectivityStat {

  private static final long UNIT = 10000;
  private final PrintWriter out;
  private long countIn;
  private long countOut;
  private long prevSec;

  public SelectivityStat(String outputFile, boolean autoFlush) {
    FileWriter outFile;
    try {
      outFile = new FileWriter(outputFile);
      out = new PrintWriter(outFile, autoFlush);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    prevSec = System.currentTimeMillis() / 1000;
  }

  public void increaseIn(long v) {
    long thisSec = System.currentTimeMillis() / 1000;
    printPastData(thisSec);
    countIn += v;
  }

  public void increaseOut(long v) {
    countOut += v;
  }

  private void printPastData(long thisSec) {
    while (prevSec < thisSec) {
      out.println(prevSec + "," + selectivity(countIn, countOut));
      countIn = 0;
      countOut = 0;
      prevSec++;
    }
  }

  private static long selectivity(long countIn, long countOut) {
    if (countIn == 0) {
      return -1;
    }
    return UNIT * countOut / countIn;
  }

  public void close() {
    printPastData(System.currentTimeMillis() / 1000);
    out.flush();
    out.close();
  }
}
