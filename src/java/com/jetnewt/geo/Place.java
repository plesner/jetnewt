package com.jetnewt.geo;
/**
 * A place represents a subdivision of the surface of the earth.
 */
public class Place {

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

  /**
   * Returns true if this place represents the entire earth.
   */
  public boolean isEverything() {
    return quad == ZQuad.kEverything;
  }

  /**
   * Returns a rect describing the boundaries of this place on the unit square.
   */
  public UnitRect getUnitBounds() {
    UnitPoint topLeft = ZQuad.getUnitQuadTopLeft(quad, zoom);
    double left = topLeft.getX();
    double top = topLeft.getY();
    double length = ZQuad.getUnitQuadLength(zoom);
    return UnitRect.fromBounds(top, left, top + length, left + length);
  }

  /**
   * Returns a point describing the middle of this place on the unit square.
   */
  public UnitPoint getUnitMiddle() {
    return ZQuad.getUnitQuadMiddle(quad, zoom);
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

  /**
   * Given a WGS84 latitude and longitude, returns the place that contains that
   * point that has the highest resolution we can represent.
   */
  public static Place fromWgs84(double lat, double lng) {
    long quad = ZQuad.fromUnit(Arc.fromWgs84(lat), Arc.fromWgs84(lng));
    return new Place(31, quad);
  }

  /**
   * Returns a place that represents the area of the earth corresponding to
   * the given quad, where the zero quad represents the whole earth.
   */
  public static Place fromQuad(long quad) {
    return new Place(quad);
  }

}
