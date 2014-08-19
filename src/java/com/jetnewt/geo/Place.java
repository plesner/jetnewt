package com.jetnewt.geo;

import com.jetnewt.console.IConsoleObject;

/**
 * A place represents a subdivision of the surface of the earth.
 */
public class Place implements IConsoleObject {

  private final int zoom;
  private final long quad;

  private Place(long quad) {
    this(ZQuad.getZoomLevel(quad), quad);
  }

  private Place(int zoom, long quad) {
    assert zoom == ZQuad.getZoomLevel(quad);
    this.zoom = zoom;
    this.quad = quad;
  }

  /**
   * Returns this place's zoom level. For testing basically.
   */
  int getZoom() {
    return this.zoom;
  }

  long getQuad() {
    return this.quad;
  }

  /**
   * Returns true if this place represents the entire earth.
   */
  public boolean isEverything() {
    return quad == ZQuad.kEverything;
  }

  /**
   * Returns a rect describing the boundaries of this place on the unit square.
   */
  public UnitLatLngRect getUnitBounds() {
    UnitPoint topLeft = ZQuad.getTopLeft(quad, zoom);
    double length = ZQuad.getLength(zoom);
    UnitLatLng northEast = new UnitLatLng(topLeft.getY(), topLeft.getX());
    UnitLatLng southWest = new UnitLatLng(topLeft.getY() + length,
        topLeft.getX() + length);
    return new UnitLatLngRect(northEast, southWest);
  }

  /**
   * Returns a point describing the middle of this place on the unit square.
   */
  public UnitLatLng getUnitMiddle() {
    UnitPoint point = ZQuad.getMiddle(quad, zoom);
    return new UnitLatLng(point.getY(), point.getX());
  }

  public GeoLatLngRect getGeoBounds() {
    return CoordinateConverter.global().unitToGeo(getUnitBounds());
  }

  /**
   * Returns a point describing the middle of this place on the earth.
   */
  public GeoLatLng getGeoMiddle() {
    return CoordinateConverter.global().unitToGeo(getUnitMiddle());
  }

  /**
   * Returns the n'th ancestor place that contains this one. The first ancestor
   * is the immediate parent, the second is the first ancestor's immediate
   * parent, and so on.
   */
  public Place getAncestor(int steps) {
    assert this.zoom >= steps;
    return new Place(this.zoom - steps, ZQuad.getAncestor(this.quad, steps));
  }

  public Place toZoom(int dest) {
    assert this.zoom >= dest;
    return new Place(dest, ZQuad.getAncestor(this.quad, this.zoom - dest));
  }

  /**
   * Returns true iff this place contains the given place or is equal to it.
   */
  public boolean contains(Place that) {
    return ZQuad.isAncestor(quad, zoom, that.quad, that.zoom);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!(obj instanceof Place))
      return false;
    Place that = (Place) obj;
    // The zoom level is encoded in the quad so we don't need to test that
    // explicitly.
    return this.quad == that.quad;
  }

  @Override
  public int hashCode() {
    return ZQuad.hashCode(this.quad);
  }

  /**
   * Given a WGS84 latitude and longitude, returns the place that contains that
   * point that has the highest resolution we can represent.
   */
  public static Place fromWgs84(GeoLatLng geo) {
    UnitLatLng unit = CoordinateConverter.global().geoToUnit(geo);
    long quad = ZQuad.fromUnit(unit.getUnitLng(), unit.getUnitLat());
    return new Place(31, quad);
  }

  /**
   * Returns a place that represents the area of the earth corresponding to
   * the given quad, where the zero quad represents the whole earth.
   */
  public static Place fromQuad(long quad) {
    return new Place(quad);
  }

  @Override
  public Object toPlankton() {
    return getGeoBounds();
  }

}
