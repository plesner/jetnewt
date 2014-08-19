package com.jetnewt.geo;
/**
 * A two-dimentional double-precision point within the unit square, so the
 * values are both between 0 and 1.
 */
public class UnitLatLng {

  private final double unitLat;
  private final double unitLng;

  public UnitLatLng(double unitLat, double unitLng) {
    assert 0 <= unitLat && unitLat <= 1;
    assert 0 <= unitLng && unitLng <= 1;
    this.unitLat = unitLat;
    this.unitLng = unitLng;
  }

  public double getUnitLat() {
    return this.unitLat;
  }

  public double getUnitLng() {
    return this.unitLng;
  }

  public double getDistance(UnitLatLng that) {
    double dx = this.unitLat - that.unitLat;
    double dy = this.unitLng - that.unitLng;
    return Math.sqrt(dx * dx + dy * dy);
  }

  public boolean equals(UnitLatLng that, double delta) {
    return Math.abs(this.unitLat - that.unitLat) <= delta
        && Math.abs(this.unitLng - that.unitLng) <= delta;
  }

}
