package com.jetnewt.geo;

import com.jetnewt.console.IConsoleObject;
import com.jetnewt.console.PlanktonObject;

/**
 * A geographic lat/lng pair.
 */
public class GeoLatLng implements IConsoleObject {

  private final double geoLat;
  private final double geoLng;

  public GeoLatLng(double geoLat, double geoLng) {
    this.geoLat = geoLat;
    this.geoLng = geoLng;
  }

  public double getGeoLat() {
    return this.geoLat;
  }

  public double getGeoLng() {
    return this.geoLng;
  }

  @Override
  public Object toPlankton() {
    return new PlanktonObject("geo.LatLng")
      .setField("lat", geoLat)
      .setField("lng", geoLng);
  }

}
