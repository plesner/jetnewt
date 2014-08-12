package com.jetnewt.geo;

import junit.framework.TestCase;

public class PlaceTest extends TestCase {

  public void testEarthBounds() {
    Place earth = Place.fromQuad(ZQuad.kEverything);
    assertTrue(earth.isEverything());
    UnitRect earthBounds = earth.getUnitBounds();
    assertEquals(earthBounds, UnitRect.fromBounds(0.0, 0.0, 1.0, 1.0));
  }

  public void testExpanding() {
    for (ArcTest.Position pos : ArcTest.kAllPositions) {
      LatLongPoint point = pos.dec;
      Place place = Place.fromWgs84(point.getLat(), point.getLong());
      assertEquals(31, place.getZoom());
      UnitPoint pointMiddle = place.getUnitMiddle();
      Place currentParent = place;
      while (!currentParent.isEverything()) {
        assertTrue(currentParent.contains(place));
        UnitRect bounds = currentParent.getUnitBounds();
        assertTrue(bounds.contains(pointMiddle));
        currentParent = currentParent.getAncestor(1);
      }
    }
  }

}
