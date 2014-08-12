package com.jetnewt.geo;
/**
 * A two-dimentional double-precision point within the unit square, so the
 * values are both between 0 and 1.
 */
public class UnitPoint {

  private final double x;
  private final double y;

  public UnitPoint(double x, double y) {
    assert 0 <= x && x <= 1;
    assert 0 <= y && y <= 1;
    this.x = x;
    this.y = y;
  }

  public double getX() {
    return this.x;
  }

  public double getY() {
    return this.y;
  }

}
