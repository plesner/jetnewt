package com.jetnewt.geo;
/**
 * A z-quad is a 64-bit quantity that identifies a regular square sub-
 * division of a square space. Here's an illustration of the first 3 zoom
 * levels,
 *
 *       zoom = 0            zoom = 1            zoom = 2
 *   +---------------+   +-------+-------+   +---+---+---+---+
 *   |               |   |       |       |   | 5 | 6 | 9 | 10|
 *   |               |   |   1   |   2   |   +---+---+---+---+
 *   |               |   |       |       |   | 7 | 8 | 11| 12|
 *   |       0       |   +-------+-------+   +---+---+---+---+
 *   |               |   |       |       |   | 13| 14| 17| 18|
 *   |               |   |   3   |   4   |   +---+---+---+---+
 *   |               |   |       |       |   | 15| 16| 19| 20|
 *   +---------------+   +-------+-------+   +---+---+---+---+
 *
 * So 0 represents the whole space, 1 represents the top left corner of the
 * space, and so on. The greater the value the smaller and more accurate the
 * space it represents. Note that at zoom level 2 the indices don't move
 * straight across, they move in zig-zag finishing each quad from the previous
 * zoom level before moving on to the next one, so 5-8 at zoom 2 cover 1 from
 * zoom 1, and so forth.
 *
 * This class provides static methods for manipulating quads directly as 64-bit
 * longs. So you don't create instances of this class, the instances are longs.
 *
 * A bit of terminology used in the implementation. The relations between quads
 * are the following.
 *
 *   * Given a quad its _parent_ is the containing quad at the zoom level above.
 *     So the parent of 14 is 3, the parent of 3 is 0.
 *   * If B is a parent of A then A is a _child_ of B. So 3 is a child of 0 and
 *     14 is a child of 3.
 *   * A quad A is an _ancestor_ of another quad B if A and B are equal or A is
 *     an ancestor of B's parent. So 0, 3, and 14 are all ancestors of 14 but
 *     only 3 is the parent of 14.
 *   * B is a _descendant_ of A if A is an ancestor of B. So 0, 3, and 14 are
 *     all descendants of 0.
 *   * Given a quad B and an ancestor A, the _descendancy_ between A and B is
 *     the quad that indicates the location of B within A. For instance, the
 *     descendancy of 14 within 3 is 2 because if you look only at 3 then 14 is
 *     at its quad 2.
 *
 * Terminology around the implementation,
 *
 *   * For a given zoom level the _bias_ is the value of the first quad at that
 *     level. The 0th bias is 0, the 1st is 1, the 2nd is 5, the 3rd is 21,
 *     etc.
 *   * The _scalar_ value of a quad is the quad's value minus the bias for that
 *     quad's zoom level. For example, values at zoom level 2 are 5-20, the
 *     scalars are 0-15. There are 4^z scalars at zoom level z.
 *
 * The name, z-quad, comes from the fact that going between a quad and its
 * descendants and ancestors ("zooming in and out") is inexpensive, and the
 * zig-zag pattern of numbering.
 *
 * For a lot of operations you need to know the quad's zoom level explicitly.
 * It is not super expensive to calculate but it's not trivial either and often
 * you'll already know the zoom so it's a waste to recalculate it, so for
 * those operations two functions exist: one that calculates the zoom itself
 * and one that takes it as an argument.
 */
public class ZQuad {

  /**
   * The highest possible zoom level that can be stored in 64 bits. 31.
   */
  public static final int kMaxZoomLevel = 31;

  /**
   * Quad representing the whole space.
   */
  public static final long kEverything = 0;

  // The bias mask that is used to calculate the bias for a given zoom level.
  private static final long kBias64Mask = 0x5555555555555555L;

  /**
   * Returns the index of the highest set one bit in the given value. Returns 0
   * for 0.
   */
  private static int highestOneBit(long value) {
    return 64 - Long.numberOfLeadingZeros(value);
  }

  /**
   * Given a quad returns the zoom level. Zoom levels are counted from 0
   * (the whole space) and up, so higher zoom level means higher granularity.
   * This operation is constant-time but may be a little expensive depending on
   * whether the machine supports {@link Long#numberOfLeadingZeros(long)}
   * natively.
   */
  public static int getZoomLevel(long quad) {
    // Determine which is the highest one-bit. Each zoom level uses exactly 2
    // bits of state but are offset by ~33%, hence the zoom level is ln2(n)/2
    // adjusted for the offset.
    int highestOneBit = highestOneBit(quad);
    // Calculate the zoom level bias that separates the coarser zoom level that
    // can have this highest bit set, and the finer one.
    long zoomBias = ((1L << highestOneBit) - 1L) & kBias64Mask;
    // Calculate difference between the value and the boundary. If the value is
    // below then we're looking at a value belonging to the coarser zoom level
    // and the delta becomes non-negative. If it is greater than then it
    // belongs to the finer zoom level and the value becomes negative.
    long delta = (zoomBias - 1) - quad;
    // Extract the sign bit as an integer. If the value belongs to the finer
    // zoom level the delta is negative and this value is 1, otherwise it is 0.
    int isFineBonus = (int) ((delta >> 63) & 1);
    // Adjust for the fineness and then shift down to account for each zoom
    // level spanning 2 bits.
    return (highestOneBit + isFineBonus) >> 1;
  }

  /**
   * Given a zoom level, returns the zoom bias for that level. Given a quad at
   * zoom level z, if you subtract the result of getZoomBias(z) you'll get the
   * scalar value between 0 and 4^z.
   */
  static long getZoomBias(int zoomLevel) {
    return kBias64Mask & ((1L << (zoomLevel << 1)) - 1L);
  }

  /**
   * Given a quad, returns the ancestor 'amount' levels above it. Note that the
   * input must be at a zoom level greater than or equal to 'amount', otherwise
   * the result will be nonsensical. This operation is ridiculously cheap.
   */
  public static long getAncestor(long quad, int amount) {
    return (quad - getZoomBias(amount)) >> (amount << 1);
  }

  /**
   * Returns the descendant of the given quad at the given descendancy.
   */
  public static long getDescendant(long quad, long descendancy) {
    return getDescendant(quad, descendancy, getZoomLevel(descendancy));
  }

  /**
   * Returns the descendant of the given quad at the given descendancy and
   * descendancy zoom level.
   */
  public static long getDescendant(long quad, long descendancy, int descendancyZoom) {
    return (quad << (descendancyZoom << 1)) + descendancy;
  }

  /**
   * Returns the smallest quad that is an ancestor of both arguments.
   */
  public static long getLeastCommonAncestor(long aQuad, long bQuad) {
    return getLeastCommonAncestor(aQuad, getZoomLevel(aQuad), bQuad, getZoomLevel(bQuad));
  }

  /**
   * Returns the smallest quad that is an ancestor of both arguments, whose zoom
   * levels are given explicitly.
   */
  public static long getLeastCommonAncestor(long aQuad, int aZoom, long bQuad, int bZoom) {
    // Normalize a and b so they're both at the same zoom level.
    if (aZoom < bZoom) {
      bQuad = getAncestor(bQuad, bZoom - aZoom);
      bZoom = aZoom;
    } else {
      aQuad = getAncestor(aQuad, aZoom - bZoom);
      aZoom = bZoom;
    }
    // Get their respective scalars.
    long aScalar = quadToScalar(aQuad, aZoom);
    long bScalar = quadToScalar(bQuad, bZoom);
    // Find most significant bit of difference between the two scalars. Because
    // of the recursive zig-zag way the quad indices are constructed this gives
    // the highest zoom level where there is a difference.
    long allDifferences = aScalar ^ bScalar;
    int highestDifference = highestOneBit(allDifferences);
    // The amount to zoom out such that the most significant difference will be
    // discarded.
    int ancestorDeltaZoom = (highestDifference + 1) >> 1;
    // Zoom out by that amount.
    return getAncestor(aQuad, ancestorDeltaZoom);
  }

  /**
   * Returns the given quad's scalar value, given its zoom level.
   */
  private static long quadToScalar(long quad, int zoom) {
    return quad - getZoomBias(zoom);
  }

  /**
   * Returns the quad at the given zoom level whose scalar is the given value.
   */
  private static long scalarToQuad(long scalar, int zoom) {
    return scalar + getZoomBias(zoom);
  }

  /**
   * Returns true iff the maybe-ancestor is an ancestor of the maybe-descendant.
   */
  public static boolean isAncestor(long maybeAncestor, long maybeDescendant) {
    return isAncestor(maybeAncestor, getZoomLevel(maybeAncestor),
        maybeDescendant, getZoomLevel(maybeDescendant));
  }

  /**
   * Returns true iff the maybe-ancestor is an ancestor of the maybe-descendant.
   */
  public static boolean isAncestor(long maybeAncestor, int maybeAncestorZoom,
      long maybeChild, int maybeChildZoom) {
    if (maybeAncestorZoom > maybeChildZoom)
      // If the "ancestor" is smaller than the "descendant" then it definitely
      // can't be an actual ancestor.
      return false;
    // If the descendant is within the ancestor (or equal to it) then getting
    // the descendant's ancestor at the same level will yield the same value.
    return getAncestor(maybeChild, maybeChildZoom - maybeAncestorZoom) == maybeAncestor;
  }

  /**
   * Given a descendant quad, return the descendency quad relative to the
   * the ancestor 'amount' levels above it. You can think of it as: given a
   * descendant and an ancestor, if we decided to ignore everything above the
   * ancestor's level and now considered the ancestor to be everything, what is
   * the quad that corresponds to the descendant.
   */
  public static long getDescendancy(long quad, int amount) {
    long descendancyMask = ((1L << (amount << 1)) - 1);
    long descendancyScalar = (quad - getZoomBias(amount)) & descendancyMask;
    return scalarToQuad(descendancyScalar, amount);
  }

  /**
   * Given a long where there are only bits set in the lower half, returns a
   * long where those bits have been spread evenly across the whole word. That
   * is, given this input,
   *
   *   00000000000000000000000000000000abcdefghijklmnopqrstuvwxyzABCDEF
   *
   * returns
   *
   *   0a0b0c0d0e0f0g0h0i0j0k0l0m0n0o0p0q0r0s0t0u0v0w0x0y0z0A0B0C0D0E0F
   */
  static long spread32To64(long value) {
    // 00000000000000000000000000000000abcdefghijklmnopqrstuvwxyzABCDEF
    long current = value;
    // 0000000000000000abcdefghijklmnop0000000000000000qrstuvwxyzABCDEF
    current = ((current << 16) & 0x0000FFFF00000000L) | (current & 0x000000000000FFFFL);
    // 00000000abcdefgh00000000ijklmnop00000000qrstuvwx00000000yzABCDEF
    current = ((current <<  8) & 0x00FF000000FF0000L) | (current & 0x000000FF000000FFL);
    // 0000abcd0000efgh0000ijkl0000mnop0000qrst0000uvwx0000yzAB0000CDEF
    current = ((current <<  4) & 0x0F000F000F000F00L) | (current & 0x000F000F000F000FL);
    // 00ab00cd00ef00gh00ij00kl00mn00op00qr00st00uv00wx00yz00AB00CD00EF
    current = ((current <<  2) & 0x3030303030303030L) | (current & 0x0303030303030303L);
    // 0a0b0c0d0e0f0g0h0i0j0k0l0m0n0o0p0q0r0s0t0u0v0w0x0y0z0A0B0C0D0E0F
    current = ((current <<  1) & 0x4444444444444444L) | (current & 0x1111111111111111L);
    return current;
  }

  /**
   * Given a long where only even-offset bits are set, returns a value where the
   * odd-offset bits have been discarded and the even-offset bits have been
   * packed together in the lower half. That is, given this input
   *
   *   0a0b0c0d0e0f0g0h0i0j0k0l0m0n0o0p0q0r0s0t0u0v0w0x0y0z0A0B0C0D0E0F
   *
   * returns
   *
   *   00000000000000000000000000000000abcdefghijklmnopqrstuvwxyzABCDEF
   */
  static long compact64To32(long value) {
    // 0a0b0c0d0e0f0g0h0i0j0k0l0m0n0o0p0q0r0s0t0u0v0w0x0y0z0A0B0C0D0E0F
    long current = value;
    // 00ab00cd00ef00gh00ij00kl00mn00op00qr00st00uv00wx00yz00AB00CD00EF
    current = ((current >>>  1) & 0x2222222222222222L) | (current & 0x1111111111111111L);
    // 0000abcd0000efgh0000ijkl0000mnop0000qrst0000uvwx0000yzAB0000CDEF
    current = ((current >>>  2) & 0x0C0C0C0C0C0C0C0CL) | (current & 0x0303030303030303L);
    // 00000000abcdefgh00000000ijklmnop00000000qrstuvwx00000000yzABCDEF
    current = ((current >>>  4) & 0x00F000F000F000F0L) | (current & 0x000F000F000F000FL);
    // 0000000000000000abcdefghijklmnop0000000000000000qrstuvwxyzABCDEF
    current = ((current >>>  8) & 0x0000FF000000FF00L) | (current & 0x000000FF000000FFL);
    // 00000000000000000000000000000000abcdefghijklmnopqrstuvwxyzABCDEF
    current = ((current >>> 16) & 0x00000000FFFF0000L) | (current & 0x000000000000FFFFL);
    return current;
  }

  /**
   * One past the highest value that can be stored in 31 bits.
   */
  private static final double kInt31Limit = (double) 0x80000000L;

  /**
   * Given a double in the unit interval (half-open from 0 to 1) returns an
   * integer between 0 and Integer.MAX_VALUE. Note that since the result is
   * always positive this retains only 31 bits of accuracy, discarding the 32th
   * sign bit.
   */
  static int unitToInt32(double v) {
    return (int) (v * kInt31Limit);
  }

  /**
   * Given a non-negative integer, returns a linear mapping into the unit
   * interval between 0 (inclusive) and 1 (exclusive).
   */
  static double int32ToUnit(int value) {
    return value / kInt31Limit;
  }

  /**
   * Given a pair of coordinates within the unit square (that is, both between
   * 0 and 1, not including 1), returns the zoom level 31 quad over the unit
   * square that contains that point.
   */
  public static long fromUnit(double x, double y) {
    // Convert the doubles to 31-bit integers.
    int x32 = unitToInt32(x);
    int y32 = unitToInt32(y);
    // Spread each of them out from 31 bits to alternating over 62 bits.
    long xSpread = spread32To64(x32);
    long ySpread = spread32To64(y32);
    // Mask the values together, yielding the scalar quad value. This is a
    // little bit magical but it's easier to see what's happening if you look
    // at just a single bit at a time. Consider if we were using just 1-bit
    // coordinates,
    //
    //   +------+------+
    //   | 0, 0 | 1, 0 |
    //   +------+------+
    //   | 1, 0 | 1, 1 |
    //   +------+------+
    //
    // We want this to map to these scalar values (and then we'll add the bias
    // later),
    //
    //   x  y
    //   0, 0 -> 0 (bin 00)
    //   1, 0 -> 1 (bin 01)
    //   0, 1 -> 2 (bin 10)
    //   1, 1 -> 3 (bin 11)
    //
    // The solution is to calculate x + (2 * y) or, equivalently, x | (y << 1).
    // What we're doing below is exactly this for every pair of bits in x and y,
    // except that we're doing it in parallel for all of x and y at once. For
    // example, if we look only at 4 bits:
    //
    //   x3 x2 x1 x0                            -> 00 x3 00 x2 00 x1 00 x0
    //   y3 y2 y1 y0 -> 00 y3 00 y2 00 y1 00 y0 -> y3 00 y2 00 y1 00 y0 00
    //                                           = y3 x3 y2 x2 y1 x1 y0 x0
    //
    // The whole spreading out thing is really just a way to make room for a
    // 2-bit result for each pair of 1-bits in x any y. And by the recursive
    // construction of quads this sum is exactly the scalar value of the
    // corresponding quad. This is part of why the enumeration of indexes is
    // zig-zag the way it is, to make this work.
    long scalar = xSpread | (ySpread << 1);
    return scalarToQuad(scalar, kMaxZoomLevel);
  }

  /**
   * Given a quad, returns the coordinates of the midpoint within the unit
   * square. So the middle unit for everything is (0.5, 0.5), the midpoint for
   * the top left corner is (0.25, 0.25), etc.
   */
  public static UnitPoint getUnitQuadMiddle(long quad) {
    return getUnitQuadMiddle(quad, getZoomLevel(quad));
  }

  /**
   * Given a quad, returns the coordinates of the midpoint within the unit
   * square. So the middle unit for everything is (0.5, 0.5), the midpoint for
   * the top left corner is (0.25, 0.25), etc.
   */
  public static UnitPoint getUnitQuadMiddle(long quad, int zoom) {
    long scalar = quadToScalar(quad, zoom);
    // Mask out the x and y components of the scalar. This is exactly the
    // encoding step from fromUnit but in reverse.
    long xSpread = scalar & 0x5555555555555555L;
    long ySpread = (scalar >> 1) & 0x5555555555555555L;
    // Pack the spread out coordinates into 32 bits, add 0.5 to get to the
    // middle (that's the (x << 1) + 1 part) then shift the value up enough to
    // make it 31 bits. Ideally we would do (n << (30 - zoom)) but we've already
    // zoomed out one step to add 0.5 and if the zoom level is 31 we're now
    // shifting by a negative amount. On the other hand it is well defined how
    // ((n << 30) >> zoom) works even if zoom is 31.
    long x = (((compact64To32(xSpread) << 1) + 1) << 30) >>> zoom;
    long y = (((compact64To32(ySpread) << 1) + 1) << 30) >>> zoom;
    return new UnitPoint(int32ToUnit((int) x), int32ToUnit((int) y));
  }

  /**
   * Given a quad, returns the coordinates of the top left corner within the
   * unit square. So the top left unit for everything is (0.0, 0.0), the top
   * left for the bottom left corner is (0.0, 0.5), etc.
   */
  public static UnitPoint getUnitQuadTopLeft(long quad) {
    return getUnitQuadTopLeft(quad, getZoomLevel(quad));
  }

  /**
   * Given a quad, returns the coordinates of the top left corner within the
   * unit square. So the top left unit for everything is (0.0, 0.0), the top
   * left for the bottom left corner is (0.0, 0.5), etc.
   */
  public static UnitPoint getUnitQuadTopLeft(long quad, int zoom) {
    long scalar = quadToScalar(quad, zoom);
    // Mask out the x and y components of the scalar. This is exactly the
    // encoding step from fromUnit but in reverse.
    long xSpread = scalar & 0x5555555555555555L;
    long ySpread = (scalar >> 1) & 0x5555555555555555L;
    // Pack the spread out coordinates into 32 bits then shift the value up
    // enough to make it 31 bits.
    long x = compact64To32(xSpread) << (kMaxZoomLevel - zoom);
    long y = compact64To32(ySpread) << (kMaxZoomLevel - zoom);
    return new UnitPoint(int32ToUnit((int) x), int32ToUnit((int) y));
  }

  /**
   * Given a zoom level, returns the length of the side of a quad at that zoom
   * level on the unit square.
   */
  public static double getUnitQuadLength(int zoom) {
    return 1.0 / (1L << zoom);
  }

}
