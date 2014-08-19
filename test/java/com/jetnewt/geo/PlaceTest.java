package com.jetnewt.geo;

import junit.framework.TestCase;

public class PlaceTest extends TestCase {

  public void testEarthBounds() {
    Place earth = Place.fromQuad(ZQuad.kEverything);
    assertTrue(earth.isEverything());
    UnitLatLngRect earthBounds = earth.getUnitBounds();
    assertEquals(earthBounds, new UnitLatLngRect(
        new UnitLatLng(0.0, 0.0),
        new UnitLatLng(1.0, 1.0)));
  }

  public void testExpanding() {
    for (ArcTest.Position pos : ArcTest.kAllPositions) {
      GeoLatLng point = pos.dec;
      Place place = Place.fromWgs84(point);
      assertEquals(31, place.getZoom());
      UnitLatLng pointMiddle = place.getUnitMiddle();
      Place currentParent = place;
      while (!currentParent.isEverything()) {
        assertTrue(currentParent.contains(place));
        UnitLatLngRect bounds = currentParent.getUnitBounds();
        assertTrue(bounds.contains(pointMiddle));
        currentParent = currentParent.getAncestor(1);
      }
    }
  }

}
