package com.jetnewt.geo;

import com.jetnewt.console.IConsoleObject;
import com.jetnewt.console.PlanktonObject;

public class GeoLatLngRect implements IConsoleObject {

  private final GeoLatLng northEast;
  private final GeoLatLng southWest;

  public GeoLatLngRect(GeoLatLng northEast, GeoLatLng southWest) {
    this.northEast = northEast;
    this.southWest = southWest;
  }

  public GeoLatLng getNorthEast() {
    return this.northEast;
  }

  public GeoLatLng getSouthWest() {
    return this.southWest;
  }

  @Override
  public Object toPlankton() {
    return new PlanktonObject("geo.LatLngRect")
      .setField("north_east", northEast)
      .setField("south_west", southWest);
  }

}
