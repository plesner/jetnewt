package com.jetnewt.geo;

import junit.framework.TestCase;
/**
 * Tests for {@link Arc}.
 */
public class ArcTest extends TestCase {

  private static final boolean kNorth = true;
  private static final boolean kSouth = false;
  private static final boolean kEast = true;
  private static final boolean kWest = false;

  /**
   * An old-fashioned degree-minute-second position.
   */
  private static class DmsLatLong {
    private final int degLat;
    private final int minLat;
    private final double secLat;
    private final boolean isNorth;
    private final int degLng;
    private final int minLng;
    private final double secLng;
    private final boolean isEast;
    public DmsLatLong(int degLat, int minLat, double secLat, boolean isNorth,
        int degLng, int minLng, double secLng, boolean isEast) {
      this.degLat = degLat;
      this.minLat = minLat;
      this.secLat = secLat;
      this.isNorth = isNorth;
      this.degLng = degLng;
      this.minLng = minLng;
      this.secLng = secLng;
      this.isEast = isEast;
    }
  }

  /**
   * A test position with both representations of a location.
   */
  static class Position {
    final DmsLatLong dms;
    final GeoLatLng dec;
    public Position(DmsLatLong dms, GeoLatLng dec) {
      this.dms = dms;
      this.dec = dec;
    }
  }

  // A set of points spread around the world. Both the DMS and decimal values
  // are rounded (they're from wikipedia) so they may not match perfectly but
  // they do match relatively closely. Because of floating-point rounding it
  // is best to stay away from boundaries. It's not that it doesn't work but
  // it makes it more fragile and trickier to understand why the expected values
  // look the way they do.
  static final Position kSydney = new Position(
      new DmsLatLong(33, 51, 35.0, kSouth, 151, 12, 34, kEast),
      new GeoLatLng(-33.859972, 151.209444));
  static final Position kCapeTown = new Position(
      new DmsLatLong(33, 55, 31, kSouth, 18, 25, 26, kEast),
      new GeoLatLng(-33.925278, 18.423889));
  static final Position kBuenosAires = new Position(
      new DmsLatLong(34, 36, 12, kSouth, 58, 22, 54, kWest),
      new GeoLatLng(-34.603333, -58.381667));
  static final Position kScottBaseAntarctica = new Position(
      new DmsLatLong(77, 50, 53.8, kSouth, 166, 46, 11.7, kEast),
      new GeoLatLng(-77.848281, 166.769907));
  static final Position kQuito = new Position(
      new DmsLatLong(0, 15, 1, kSouth, 78, 35, 1, kWest),
      new GeoLatLng(-0.2505, -78.583833));
  static final Position kUpoluSamoa = new Position(
      new DmsLatLong(13, 55, 1, kSouth, 171, 45, 1, kWest),
      new GeoLatLng(-13.917267, -171.7505));
  static final Position kAlmostZeroForward = new Position(
      new DmsLatLong(0, 0, 0.0001, kNorth, 0, 0, 0.0001, kEast),
      new GeoLatLng(0.00001, 0.00001));
  static final Position kAlmostZeroBackward = new Position(
      new DmsLatLong(0, 0, 0.001, kSouth, 0, 0, 0.001, kWest),
      new GeoLatLng(-0.00001, -0.00001));
  static final Position kKarachi = new Position(
      new DmsLatLong(24, 51, 36, kNorth, 67, 0, 36, kEast),
      new GeoLatLng(24.86, 67.01));
  static final Position kKamchatka = new Position(
      new DmsLatLong(53, 1, 1, kNorth, 158, 39, 1, kEast),
      new GeoLatLng(53.016667, 158.6505));
  static final Position kQaanaaq = new Position(
      new DmsLatLong(77, 28, 0, kNorth, 69, 13, 50, kWest),
      new GeoLatLng(77.466667, -69.230556));
  static final Position kAarhus = new Position(
      new DmsLatLong(56, 9, 0, kNorth, 10, 13, 1, kEast),
      new GeoLatLng(56.15, 10.216717));

  static final Position[] kAllPositions = {
    kSydney, kCapeTown, kBuenosAires, kScottBaseAntarctica, kQuito, kUpoluSamoa,
    kAlmostZeroForward, kAlmostZeroBackward, kKarachi, kKamchatka, kQaanaaq,
    kAarhus
  };

  private void checkConversion(OrdinateConverter unitMap, double dec, int deg, int min,
      double sec, boolean isForward) {
    double dmsUnit = unitMap.toUnit(deg, min, sec, isForward);
    assertTrue(0 <= dmsUnit);
    assertTrue(dmsUnit < 1);
    assertEquals(deg, unitMap.unitToDegrees(dmsUnit));
    assertEquals(min, unitMap.unitToMinutes(dmsUnit));
    assertEquals(sec, unitMap.unitToSeconds(dmsUnit), 1e-9);
    assertEquals(dec, unitMap.fromUnit(dmsUnit), 1e-3);
    double decUnit = unitMap.toUnit(dec);
    assertEquals(dmsUnit, decUnit, 1e-5);
    assertTrue(0 <= decUnit);
    assertTrue(decUnit < 1);
    assertEquals(deg, unitMap.unitToDegrees(decUnit));
    assertEquals(min, unitMap.unitToMinutes(decUnit));
    assertEquals(sec, unitMap.unitToSeconds(decUnit), 1.5);
  }

  public void testPointArcs() {
    CoordinateConverter globalMap = CoordinateConverter.global();
    OrdinateConverter latMap = globalMap.getLatMap();
    OrdinateConverter lngMap = globalMap.getLngMap();
    for (Position pos : kAllPositions) {
      DmsLatLong dms = pos.dms;
      checkConversion(latMap, pos.dec.getGeoLat(), dms.degLat, dms.minLat, dms.secLat, dms.isNorth);
      checkConversion(lngMap, pos.dec.getGeoLng(), dms.degLng, dms.minLng, dms.secLng, dms.isEast);
    }
  }

}
