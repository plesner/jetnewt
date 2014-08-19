package com.jetnewt.geo;

public class UnitPoint {

  private final double x;
  private final double y;

  public UnitPoint(double x, double y) {
    this.x = x;
    this.y = y;
  }

  public double getX() {
    return this.x;
  }

  public double getY() {
    return this.y;
  }

  public double getToroidDistance(double x, double y) {
    double dx = getToroidDelta(this.x, x);
    double dy = getToroidDelta(this.y, y);
    return Math.sqrt(dx * dx + dy * dy);
  }

  public double getToroidDistance(UnitPoint p) {
    return getToroidDistance(p.getX(), p.getY());
  }

  /**
   * Returns the unsigned delta between two unit value. The result is the
   * smallest of the two distances between them, either directly or wrapping
   * around 1. For instance, the distances between 0.05 and 0.95 are 0.9
   * directly and 0.1 going through 1.0 and coming back around 0.0. The result
   * in that case is 0.1.
   */
  public static double getToroidDelta(double a, double b) {
    double direct = Math.abs(a - b);
    return (direct <= 0.5) ? direct : (1.0 - direct);
  }

}
