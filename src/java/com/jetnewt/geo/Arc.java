package com.jetnewt.geo;
/**
 * An arc coordinate, half of a position. This is an ADT-type class so
 * you don't use instances of it, you use the static methods with double
 * values. The "native" value used to represent an arc is a value between 0 and
 * 1 that starts at the 0 meridian and then goes around the earth towards the
 * east (for lats) or south (for longs) all the way around until it comes back.
 * Note that this is different from the "classic" coordinates which would
 * stretch out from 0 in both directions and meet at the opposite side of the
 * globe.
 *
 * All the conversions to traditional coordinates use values counted in arcsecs.
 * There are 1,296,000 (= 60 * 60 * 360) arcsecs around the globe, 648,000
 * around each hemisphere. Depending on what we need three different kinds of
 * arcsecs are used (sigh, I know, but we're dealing with historical formats
 * here). They are:
 *
 *   * Global arcsecs. This is simply the arc scaled up to a value between 0
 *     and 1,296,000.
 *   * Mirrored arcsecs. This is the absolute distance in arcsecs to the 0
 *     meridian, a value between 0 and 648,000. It is mirrored because for each
 *     value there are two places on the earth with that value, one on either
 *     side of 0.
 *   * Signed arcsecs. This is the same as mirrored arcsecs except that values
 *     forward from the 0 meridian are positive and backwards are negative.
 */
public class Arc {

  private static final int kSecsPerMin = 60;
  private static final int kSecsPerDeg = 60 * kSecsPerMin;
  private static final int kDegsPerHemi = 180;
  private static final int kDegsPerGlobe = kDegsPerHemi * 2;
  private static final int kSecsPerHemi = kDegsPerHemi * kSecsPerDeg;
  private static final int kSecsPerGlobe = 2 * kSecsPerHemi;

  /**
   * Given a degree-minute-second position indicating a WGS84 coordinate,
   * returns the corresponding arc. IsForward indicates north/south (north is
   * forward) and east/west (west is forward).
   */
  public static double fromWgs84(int degs, int mins, double secs, boolean isForward) {
    double mirroredArcsecs = secs + (kSecsPerMin * mins) + (kSecsPerDeg * degs);
    double globalArcsecs = mirroredToGlobalArcsecs(mirroredArcsecs, isForward);
    return globalArcsecs / kSecsPerGlobe;
  }

  /**
   * Given a signed decimal degree value representing a wgs84 coordinate,
   * returns the corresponding arc.
   */
  public static double fromWgs84(double value) {
    return (value < 0)
        ? (kDegsPerGlobe + value) / kDegsPerGlobe
        : (value / kDegsPerGlobe);
  }

  /**
   * Given an arc value from 0 to 1 returns the mirrored arcsecs, the absolute
   * distance in arcsecs from the 0 meridian.
   */
  static double arcToMirroredArcsecs(double arc) {
    return (arc < 0.5) ? (arc * kSecsPerGlobe) : ((1.0 - arc) * kSecsPerGlobe);
  }

  /**
   * Given an arc value from 0 to 1 (excluding 1) returns the signed arcsecs,
   * the signed distance in arcsecs from the 0 meridian. Positive values
   * correspond to forward (north, east) negative to backward (south, west).
   */
  static double arcToSignedArcsecs(double arc) {
    return (arc < 0.5)
        ? (arc * kSecsPerGlobe)
        : ((arc - 1.0) * kSecsPerGlobe);
  }

  /**
   * Given a mirrored arcsecs value and whether they're going forward or
   * backward, returns a global arcsecs value.
   */
  static double mirroredToGlobalArcsecs(double mirrored, boolean isForward) {
    return isForward
        ? mirrored
        : (kSecsPerGlobe - mirrored);
  }

  /**
   * Given an arc value returns the integer degrees component (0-179).
   */
  public static int getDegrees(double arc) {
    return (int) (arcToMirroredArcsecs(arc) / kSecsPerDeg);
  }

  /**
   * Given an arc value returns the minutes component (0-59).
   */
  public static int getMinutes(double arc) {
    return ((int) (arcToMirroredArcsecs(arc) / kSecsPerMin)) % 60;
  }

  /**
   * Given an arc value returns the seconds component (0-60, not including 60).
   */
  public static double getSeconds(double arc) {
    return arcToMirroredArcsecs(arc) % 60.0;
  }

  /**
   * Given an arc value, returns its representation as a value between 0 and 1,
   * not including 1. This is the identity but it's useful in external code that
   * it can be written explicitly that now we're dealing with a unit value,
   * rather than just assume implicitly that arcs are units, which they are
   * but it's really an implementation detail.
   */
  public static double toUnit(double arc) {
    return arc;
  }

  /**
   * Given an arc value, returns its representation as a signed decimal degree
   * value.
   */
  public static double toDecimal(double arc) {
    return arcToSignedArcsecs(arc) / kSecsPerDeg;
  }

}
