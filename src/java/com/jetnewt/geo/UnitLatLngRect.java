package com.jetnewt.geo;

/**
 * A double-valued rectangle within the unit square.
 */
public class UnitLatLngRect {

  private final UnitLatLng northEast;
  private final UnitLatLng southWest;

  /**
   * There is no uniquely most obvious way to construct a rect, it both makes
   * sense to use boundaries and corner + extent so this constructor is private
   * so you have to explicitly use the constructor functions below.
   */
  public UnitLatLngRect(UnitLatLng northEast, UnitLatLng southWest) {
    assert northEast.getUnitLat() <= southWest.getUnitLat();
    assert northEast.getUnitLng() <= southWest.getUnitLng();
    this.northEast = northEast;
    this.southWest = southWest;
  }

  public UnitLatLng getNorthEast() {
    return this.northEast;
  }

  public UnitLatLng getSouthWest() {
    return this.southWest;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!(obj instanceof UnitLatLngRect))
      return false;
    UnitLatLngRect that = (UnitLatLngRect) obj;
    return equals(that, 0.0);
  }

  /**
   * Returns true if this and the given rect are at most delta apart on any of
   * the boundaries.
   */
  private boolean equals(UnitLatLngRect that, double delta) {
    return this.southWest.equals(that.southWest, delta)
        && this.northEast.equals(that.northEast, delta);
  }

  /**
   * Is the given unit point within this rect?
   */
  public boolean contains(UnitLatLng p) {
    return (northEast.getUnitLat() <= p.getUnitLat())
        && (p.getUnitLat() <= southWest.getUnitLat())
        && (northEast.getUnitLng() <= p.getUnitLng())
        && (p.getUnitLng() <= southWest.getUnitLng());
  }

}
