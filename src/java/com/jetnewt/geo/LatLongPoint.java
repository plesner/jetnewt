package com.jetnewt.geo;
/**
 * A lat/long pair. This is really just a point with different names but it's
 * easy to get confused about lat/longs if you're using different names for them
 * like x/y or first/second.
 */
public class LatLongPoint {

  private final double lat;
  private final double lng;

  public LatLongPoint(double lat, double lng) {
    this.lat = lat;
    this.lng = lng;
  }

  public double getLat() {
    return this.lat;
  }

  public double getLong() {
    return this.lng;
  }

}
