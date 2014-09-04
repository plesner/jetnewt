package com.jetnewt.geo;

import static com.jetnewt.geo.ZQuad.compact64To32;
import static com.jetnewt.geo.ZQuad.fromUnit;
import static com.jetnewt.geo.ZQuad.getAncestor;
import static com.jetnewt.geo.ZQuad.getDescendancy;
import static com.jetnewt.geo.ZQuad.getDescendant;
import static com.jetnewt.geo.ZQuad.getLength;
import static com.jetnewt.geo.ZQuad.getMiddle;
import static com.jetnewt.geo.ZQuad.getTopLeft;
import static com.jetnewt.geo.ZQuad.getZoomBias;
import static com.jetnewt.geo.ZQuad.getZoomLevel;
import static com.jetnewt.geo.ZQuad.int32ToUnit;
import static com.jetnewt.geo.ZQuad.spread32To64;
import static com.jetnewt.geo.ZQuad.unitToInt32;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

import com.jetnewt.geo.ZQuad.ChunkString;
/**
 * Unit test of {@link #ZQuad}.
 */
public class ZQuadTest extends TestCase {

  // Test the zoom level calculation for a handful of constants. If this fails
  // it's easier to debug than the exhaustive one below.
  public void testGetZoomLevelConstants() {
    assertEquals(0, getZoomLevel(0));
    assertEquals(1, getZoomLevel(1));
    assertEquals(1, getZoomLevel(4));
    assertEquals(2, getZoomLevel(5));
    assertEquals(2, getZoomLevel(20));
    assertEquals(3, getZoomLevel(21));
    assertEquals(3, getZoomLevel(84));
    assertEquals(4, getZoomLevel(85));
    assertEquals(4, getZoomLevel(340));
    assertEquals(5, getZoomLevel(341));
    assertEquals(5, getZoomLevel(1364));
    assertEquals(6, getZoomLevel(1365));
    assertEquals(30, getZoomLevel(1537228672809129300L));
    assertEquals(31, getZoomLevel(1537228672809129301L));
  }

  // Test all the zoom level boundaries.
  public void testGetZoomLevelAll() {
    long currentBoundary = 1;
    for (int currentLevel = 0; currentLevel < 32; currentLevel++) {
      long before = currentBoundary - 1;
      long after = currentBoundary;
      assertEquals(currentLevel, getZoomLevel(before));
      assertEquals(currentLevel + 1, getZoomLevel(after));
      currentBoundary += 4L << (2 * currentLevel);
    }
  }

  public void testZoomBias() {
    assertEquals(0, getZoomBias(0));
    assertEquals(1, getZoomBias(1));
    assertEquals(5, getZoomBias(2));
    assertEquals(21, getZoomBias(3));
    assertEquals(85, getZoomBias(4));
    long currentBias = 1;
    for (int zoom = 0; zoom < 31; zoom++) {
      assertEquals(currentBias, getZoomBias(zoom + 1));
      currentBias += 4L << (2 * zoom);
    }
  }

  private static double[] getBounds(long zQuad) {
    UnitPoint topLeft = getTopLeft(zQuad);
    int zoom = getZoomLevel(zQuad);
    double length = getLength(zoom);
    return new double[] {
        topLeft.getY(), topLeft.getX(),
        topLeft.getY() + length, topLeft.getX() + length
    };
  }

  /**
   * Returns true iff the given inner quad is fully contained in the outer one,
   * including if they are equal.
   */
  private static boolean slowContains(long outer, long inner) {
    double[] outerBounds = getBounds(outer);
    double[] innerBounds = getBounds(inner);
    return outerBounds[0] <= innerBounds[0]
        && outerBounds[1] <= innerBounds[1]
        && innerBounds[2] <= outerBounds[2]
        && innerBounds[3] <= innerBounds[3];
  }

  // Limited test of zooming out that just checks that zooming out N steps from
  // a value at zoom level M gives you a value at level (M-N). It doesn't check
  // that the value is actually the correct one to zoom to.
  public void testGetAncestorLevels() {
    assertEquals(0, getAncestor(1, 1));
    assertEquals(0, getAncestor(4, 1));
    assertEquals(1, getAncestor(5, 1));
    assertEquals(4, getAncestor(20, 1));
    for (long v = 1; v < 65536; v++) {
      int zoomLevel = getZoomLevel(v);
      for (int a = 0; a <= zoomLevel; a++) {
        long zoomed = getAncestor(v, a);
        assertTrue(slowContains(zoomed, v));
        assertEquals(zoomLevel - a, getZoomLevel(zoomed));
      }
    }
  }

  private void checkSpreadAndBack(long spread, long dense) {
    assertEquals(spread, spread32To64(dense));
    assertEquals(dense, compact64To32(spread));
  }

  public void testSpreadLower32() {
    checkSpreadAndBack(0x55L, 0xFL);
    checkSpreadAndBack(0x5555L, 0xFFL);
    checkSpreadAndBack(0x55555555L, 0xFFFFL);
    checkSpreadAndBack(0x5555555555555555L, 0xFFFFFFFFL);
    checkSpreadAndBack(0x5500550055005500L, 0xF0F0F0F0L);
    checkSpreadAndBack(0x0055005500550055L, 0x0F0F0F0FL);
    checkSpreadAndBack(0x0055005555005500L, 0x0F0FF0F0L);
    checkSpreadAndBack(0x0000005555005500L, 0x000FF0F0L);
    checkSpreadAndBack(0x0001005555005500L, 0x010FF0F0L);
    checkSpreadAndBack(0x0004005555005500L, 0x020FF0F0L);
    checkSpreadAndBack(0x0005005555005500L, 0x030FF0F0L);
    checkSpreadAndBack(0x0010005555005500L, 0x040FF0F0L);
    checkSpreadAndBack(0x0011005555005500L, 0x050FF0F0L);
    checkSpreadAndBack(0x0014005555005500L, 0x060FF0F0L);
    checkSpreadAndBack(0x0015005555005500L, 0x070FF0F0L);
    checkSpreadAndBack(0x0040005555005500L, 0x080FF0F0L);
    checkSpreadAndBack(0x0041005555005500L, 0x090FF0F0L);
    checkSpreadAndBack(0x0044005555005500L, 0x0A0FF0F0L);
    checkSpreadAndBack(0x0045005555005500L, 0x0B0FF0F0L);
    checkSpreadAndBack(0x0050005555005500L, 0x0C0FF0F0L);
    checkSpreadAndBack(0x0051005555005500L, 0x0D0FF0F0L);
    checkSpreadAndBack(0x0054005555005500L, 0x0E0FF0F0L);
  }

  public void testUnitToInt32() {
    assertEquals(0, unitToInt32(0.0));
    assertEquals(1 << 28, unitToInt32(0.125));
    assertEquals(1 << 29, unitToInt32(0.25));
    assertEquals(1 << 30, unitToInt32(0.5));
    assertEquals(3 << 29, unitToInt32(0.75));
  }

  public void testInt32ToUnit() {
    assertEquals(0.0, int32ToUnit(0));
    assertEquals(0.5, int32ToUnit(1 << 30));
    double almostOne = int32ToUnit(Integer.MAX_VALUE);
    assertTrue(almostOne < 1.0);
    assertEquals(1.0, almostOne, 0.00000001);
  }

  public void testFromUnit() {
    long topLeft = fromUnit(0.0, 0.0);
    assertEquals(topLeft, getZoomBias(31));
    long bottomRight = fromUnit(1.0, 1.0);
    assertEquals(bottomRight, getZoomBias(31) + ((1L << 62) - 1));

    // Painstakingly calculated by hand...
    long inTheMiddle = fromUnit(2.0 / 3.0, 1.0 / 3.0);
    assertEquals(2, getAncestor(inTheMiddle, 30));
    assertEquals(11, getAncestor(inTheMiddle, 29));
    assertEquals(46, getAncestor(inTheMiddle, 28));
    assertEquals(187, getAncestor(inTheMiddle, 27));
    assertEquals(750, getAncestor(inTheMiddle, 26));
    assertEquals(3003, getAncestor(inTheMiddle, 25));
  }

  private void checkGetUnitQuadMiddle(double x, double y, long zQuad) {
    UnitPoint result = getMiddle(zQuad);
    assertEquals(x, result.getX(), 1e-9);
    assertEquals(y, result.getY(), 1e-9);
  }

  public void testGetUnitQuadMiddle() {
    checkGetUnitQuadMiddle(0.5, 0.5, 0);
    checkGetUnitQuadMiddle(0.25, 0.25, 1);
    checkGetUnitQuadMiddle(0.75, 0.25, 2);
    checkGetUnitQuadMiddle(0.25, 0.75, 3);
    checkGetUnitQuadMiddle(0.75, 0.75, 4);

    long inTheMiddle = fromUnit(0.1, 0.4);
    checkGetUnitQuadMiddle(0.1, 0.4, inTheMiddle);

    Random random = new Random(423452134);
    for (int i = 0; i < 1024; i++) {
      double x = random.nextDouble();
      double y = random.nextDouble();
      long zQuad = fromUnit(x, y);
      checkGetUnitQuadMiddle(x, y, zQuad);
    }
  }

  public void testGetUnitQuadLength() {
    assertEquals(1.0, getLength(0));
    assertEquals(0.5, getLength(1));
    assertEquals(0.25, getLength(2));
    assertEquals(0.0625, getLength(4));
    assertEquals(0.00390625, getLength(8));
    assertTrue(getLength(31) > 0.0);
  }

  public void testGetDescendancy() {
    assertEquals(1, getDescendancy(9, 1));
    assertEquals(2, getDescendancy(10, 1));
    assertEquals(3, getDescendancy(11, 1));
    assertEquals(4, getDescendancy(12, 1));
    assertEquals(11, getDescendancy(11, 2));
  }

  /**
   * Returns a random z-quad at the highest zoom level.
   */
  static long nextPreciseZQuad(Random random) {
    double x = random.nextDouble();
    double y = random.nextDouble();
    return fromUnit(x, y);
  }

  /**
   * Returns a z-quad at the given zoom level.
   */
  static long nextZQuad(Random random, int zoomLevel) {
    long point = nextPreciseZQuad(random);
    return getAncestor(point, ZQuad.kMaxZoomLevel - zoomLevel);
  }

  public void testGetDescendant() {
    Random random = new Random(2345234);
    for (int i = 0; i < 65536; i++) {
      long value = nextPreciseZQuad(random);
      long descendancy = getDescendancy(value, 10);
      long ancestor = getAncestor(value, 10);
      assertTrue(slowContains(ancestor, value));
      long back = getDescendant(ancestor, descendancy, 10);
      assertEquals(value, back);
    }
  }

  /**
   * Given an ancestor and a descendant, returns the child of the ancestor which
   * is an ancestor of the descendant.
   */
  private static long getChildThatsAncestorOf(long ancestor, long descendant) {
    int ancestorZoom = getZoomLevel(ancestor);
    int descendantZoom = getZoomLevel(descendant);
    assertTrue(ancestorZoom < descendantZoom);
    long child = ZQuad.getAncestor(descendant, descendantZoom - ancestorZoom - 1);
    assertEquals(ancestor, getAncestor(child, 1));
    return child;
  }

  public void testRandomLeastCommonAncestor() {
    Random random = new Random(234234);
    for (int i = 0; i < 65536; i++) {
      // Generate a random ancestor. The random values we'll find an ancestor
      // of will be within this one, otherwise the ancestor will usually be
      // everything.
      int randomAncestorZoom = random.nextInt(10);
      long randomAncestor = nextZQuad(random, randomAncestorZoom);
      // Generate two random descendants within the ancestor.
      long aDescendancy = nextZQuad(random, random.nextInt(ZQuad.kMaxZoomLevel - randomAncestorZoom));
      long aQuad = getDescendant(randomAncestor, aDescendancy);
      long bDescendancy = nextZQuad(random, random.nextInt(ZQuad.kMaxZoomLevel - randomAncestorZoom));
      long bQuad = getDescendant(randomAncestor, bDescendancy);
      // Test that the result is indeed an ancestor of both descendants.
      long ancestor = ZQuad.getLeastCommonAncestor(aQuad, bQuad);
      assertTrue(ZQuad.isAncestor(ancestor, aQuad));
      assertTrue(ZQuad.isAncestor(ancestor, bQuad));
      // If the ancestor is strictly above each descendant, check that it is the
      // least one, that is, there is no child that is also an ancestor.
      if (ancestor != aQuad) {
        long aAncestor = getChildThatsAncestorOf(ancestor, aQuad);
        assertFalse(ZQuad.isAncestor(aAncestor, bQuad));
      }
      if (ancestor != bQuad) {
        long bAncestor = getChildThatsAncestorOf(ancestor, bQuad);
        assertFalse(ZQuad.isAncestor(bAncestor, aQuad));
      }
    }
  }

  public void testStringConversion() {
    long maxQuad = ZQuad.getZoomBias(8);
    Map<String, Long> strings = new HashMap<String, Long>();
    for (long chunk = 0; chunk < maxQuad; chunk++) {
      ChunkString chunkStr = ZQuad.getChunkString(chunk, ZQuad.getZoomLevel(chunk));
      assertEquals(chunk, chunkStr.getChunk());
      String str = chunkStr.toString();
      assertEquals(4, str.length());
      assertFalse(strings.containsKey(str));
      strings.put(str, chunk);
      ChunkString parsed = ZQuad.parseChunkString(str);
      parsed.validate = false;
      assertEquals(chunk, parsed.getChunk());
    }
    int membersSeen = 0;
    for (int order = 0; order < 2; order++) {
      boolean vowelFirst = (order == 0);
      for (int iv0 = 0; iv0 < 6; iv0++) {
        char v0 = ZQuad.VOWELS.charAt(iv0);
        for (int ic0 = 0; ic0 < 20; ic0++) {
          char c0 = ZQuad.CONSONANTS.charAt(ic0);
          for (int iv1 = 0; iv1 < 6; iv1++) {
            char v1 = ZQuad.VOWELS.charAt(iv1);
            for (int ic1 = 0; ic1 < 20; ic1++) {
              char c1 = ZQuad.CONSONANTS.charAt(ic1);
              String str;
              if (vowelFirst) {
                str = "" + v0 + c0 + v1 + c1;
              } else {
                str = "" + c0 + v0 + c1 + v1;
              }
              ChunkString parsed = ZQuad.parseChunkString(str);
              long value;
              if (parsed == null) {
                value = -1;
              } else {
                parsed.validate = false;
                value = parsed.getChunk();
              }
              if (strings.containsKey(str)) {
                membersSeen++;
                assertEquals((long) strings.get(str), parsed.getChunk());
              } else {
                assertEquals(-1, value);
              }
            }
          }
        }
      }
    }
    assertEquals(strings.size(), membersSeen);
  }

  private static List<UnitLatLng> getMatches(Map<String, UnitLatLng> quads, String str) {
    List<UnitLatLng> result = new ArrayList<UnitLatLng>();
    Pattern pattern = Pattern.compile(str);
    for (Map.Entry<String, UnitLatLng> entry : quads.entrySet()) {
      Matcher matcher = pattern.matcher(entry.getKey());
      if (matcher.matches())
        result.add(entry.getValue());
    }
    return result;
  }

  private static class MatchEntry {

    private final int x;
    private final int y;
    private int count = 0;

    public MatchEntry(int x, int y) {
      this.x = x;
      this.y = y;
    }

    @Override
    public int hashCode() {
      return (x << 16) ^ y;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      } else if (!(obj instanceof MatchEntry)) {
        return false;
      }
      MatchEntry that = (MatchEntry) obj;
      return that.x == this.x && that.y == this.y;
    }

    @Override
    public String toString() {
      return "(" + x + ", " + y + ")";
    }

  }

  private static void checkPattern(Map<String, UnitLatLng> map, String pattern,
      int power, int expX, int expY, String expected) {
    int resolution = (1 << power);
    int limit = (128 * 128) / (resolution * resolution);
    Map<MatchEntry, MatchEntry> result = new HashMap<MatchEntry, MatchEntry>();
    int minX = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int minY = Integer.MAX_VALUE;
    int maxY = Integer.MIN_VALUE;
    for (UnitLatLng point : getMatches(map, pattern)) {
      int x = (int) Math.floor(point.getUnitLng() * resolution);
      int y = (int) Math.floor(point.getUnitLat() * resolution);
      minX = Math.min(minX, x);
      maxX = Math.max(maxX, x);
      minY = Math.min(minY, y);
      maxY = Math.max(maxY, y);
      MatchEntry entry = new MatchEntry(x, y);
      if (result.containsKey(entry))
        entry = result.get(entry);
      else
        result.put(entry, entry);
      entry.count++;
    }
    String found = "";
    for (int y = minY; y <= maxY; y++) {
      String line = "";
      for (int x = minX; x <= maxX; x++) {
        MatchEntry key = new MatchEntry(x, y);
        int count;
        if (result.containsKey(key)) {
          count = result.get(key).count;
        } else {
          count = 0;
        }
        line += (count == 0) ? " " : ((count < limit) ? "-" : "X");
      }
      if (y > minY)
        found += "\n";
      found += line;
    }
    assertEquals(expX, minX << (7 - power));
    assertEquals(expY, minY << (7 - power));
    assertEquals(expected, found);
  }

  public void testStringShapes() {
    Map<String, UnitLatLng> quads = new HashMap<String, UnitLatLng>();
    for (long quad = ZQuad.getZoomBias(7); quad < ZQuad.getZoomBias(8); quad++) {
      Place place = Place.fromQuad(quad);
      UnitLatLng middle = place.getUnitMiddle();
      String str = ZQuad.toString(quad);
      quads.put(str, middle);
    }

    // Divisions
    String rect1x2 = joinStrings("X", "X");
    checkPattern(quads, "[aeiouy]...", 1, 0, 0, rect1x2);
    checkPattern(quads, ".[aeiouy]..", 1, 64, 0, rect1x2);

    // Western sections
    String rect8x11 = joinStrings(
        "XXXXXXXX", "XXXXXXXX",
        "XXXXXXXX", "XXXXXXXX",
        "XXXXXXXX", "XXXXXXXX",
        "XXXXXXXX", "XXXXXXXX",
        "XXXXXXXX", "XXXXXXXX",
        "XXXXXXXX");
    checkPattern(quads, "a...", 5, 0, 0, rect8x11);
    checkPattern(quads, "e...", 5, 32, 0, rect8x11);
    checkPattern(quads, "i...", 5, 0, 44, rect8x11);
    checkPattern(quads, "o...", 5, 32, 44, rect8x11);
    String rect8x10 = joinStrings(
        "XXXXXXXX", "XXXXXXXX",
        "XXXXXXXX", "XXXXXXXX",
        "XXXXXXXX", "XXXXXXXX",
        "XXXXXXXX", "XXXXXXXX",
        "XXXXXXXX", "XXXXXXXX");
    checkPattern(quads, "u...", 5, 0, 88, rect8x10);
    checkPattern(quads, "y...", 5, 32, 88, rect8x10);

    // Eastern sections.
    checkPattern(quads, ".a..", 5, 64, 0, rect8x11);
    checkPattern(quads, ".e..", 5, 96, 0, rect8x11);
    checkPattern(quads, ".i..", 5, 64, 44, rect8x11);
    checkPattern(quads, ".o..", 5, 96, 44, rect8x11);
    checkPattern(quads, ".u..", 5, 64, 88, rect8x10);
    checkPattern(quads, ".y..", 5, 96, 88, rect8x10);

    // Full regions
    String topLeftRegion = joinStrings(
        "XX",
        "XX",
        "X ");
    checkPattern(quads, "ba..", 5, 64, 0, topLeftRegion);
    String topRightRegion = joinStrings(
        "XX",
        "XX",
        " X");
    checkPattern(quads, "ca..", 5, 72, 0, topRightRegion);
    String rect2x2 = joinStrings("XX", "XX");
    checkPattern(quads, "da..", 5, 68, 8, rect2x2);
    String bottomLeftRegion = joinStrings(
        "X ",
        "XX",
        "XX");
    checkPattern(quads, "fa..", 5, 64, 12, bottomLeftRegion);
    String bottomRightRegion = joinStrings(
        " X",
        "XX",
        "XX");
    checkPattern(quads, "ga..", 5, 72, 12, bottomRightRegion);

    // Bottom-11 regions
    checkPattern(quads, "na..", 5, 64, 24, topLeftRegion);
    checkPattern(quads, "pa..", 5, 72, 24, topRightRegion);
    checkPattern(quads, "qa..", 5, 68, 32, rect2x2);
    String bottomLeftRegion11 = joinStrings(
        "X ",
        "XX");
    checkPattern(quads, "ra..", 5, 64, 36, bottomLeftRegion11);
    String bottomRightRegion11 = joinStrings(
        " X",
        "XX");
    checkPattern(quads, "sa..", 5, 72, 36, bottomRightRegion11);

    // Bottom-10 regions
    checkPattern(quads, "nu..", 5, 64, 112, topLeftRegion);
    checkPattern(quads, "pu..", 5, 72, 112, topRightRegion);
    checkPattern(quads, "qu..", 5, 68, 120, rect2x2);
    String rect1x1 = joinStrings("X");
    checkPattern(quads, "ru..", 5, 64, 124, rect1x1);
    checkPattern(quads, "su..", 5, 76, 124, rect1x1);

    String topLeftGroup = joinStrings(
        "XXXX",
        "XXXX",
        "XXX ",
        "XX  ");
    checkPattern(quads, "ba.a", 7, 64, 0, topLeftGroup);
    String topRightGroup = joinStrings(
        "XXXX",
        "XXXX",
        " XXX",
        "  XX");
    checkPattern(quads, "ba.e", 7, 68, 0, topRightGroup);
    String centerGroup = joinStrings(
        " XX ",
        "XXXX",
        "XXXX",
        " XX ");
    checkPattern(quads, "ba.i", 7, 66, 2, centerGroup);
    String bottomLeftGroupWithOutlier = joinStrings(
        "XX  ",
        "XXX ",
        "XXXX",
        "XXXX",
        "X   ");
    checkPattern(quads, "ba.o", 7, 64, 4, bottomLeftGroupWithOutlier);
    String bottomRightGroup = joinStrings(
        "  XX",
        " XXX",
        "XXXX",
        "XXXX");
    checkPattern(quads, "ba.u", 7, 68, 4, bottomRightGroup);
    String outlierTopLeftGroup = joinStrings(
        " XXX",
        "XXXX",
        "XXXX",
        "XXXX");
    checkPattern(quads, "ba.y", 7, 64, 8, outlierTopLeftGroup);

    checkPattern(quads, "ca.a", 7, 72, 0, topLeftGroup);
    checkPattern(quads, "ca.e", 7, 76, 0, topRightGroup);
    checkPattern(quads, "ca.i", 7, 74, 2, centerGroup);
    String bottomLeftGroup = joinStrings(
        "XX  ",
        "XXX ",
        "XXXX",
        "XXXX");
    checkPattern(quads, "ca.o", 7, 72, 4, bottomLeftGroup);
    String bottomRightGroupWithOutlier = joinStrings(
        "  XX",
        " XXX",
        "XXXX",
        "XXXX",
        "X   ");
    checkPattern(quads, "ca.u", 7, 76, 4, bottomRightGroupWithOutlier);

    checkPattern(quads, "da.a", 7, 68, 8, topLeftGroup);
    checkPattern(quads, "da.e", 7, 72, 8, topRightGroup);
    checkPattern(quads, "da.i", 7, 70, 10, centerGroup);
    checkPattern(quads, "da.o", 7, 68, 12, bottomLeftGroup);
    checkPattern(quads, "da.u", 7, 72, 12, bottomRightGroup);
  }

  private static String joinStrings(String... args) {
    String result = "";
    for (int i = 0; i < args.length; i++) {
      if (i > 0)
        result += "\n";
      result += args[i];
    }
    return result;
  }

}
