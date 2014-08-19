package com.jetnewt.geo;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import junit.framework.TestCase;

public class ZQuadTreeTest extends TestCase {

  private static Place nextPlace(Random random) {
    return Place.fromQuad(ZQuadTest.nextPreciseZQuad(random));
  }

  public void testWithinQuad() {
    Random random = new Random(23142);
    for (int loop = 0; loop < 8; loop++) {
      ZQuadTree<Place> tree = new ZQuadTree<Place>();
      Map<Place, UnitLatLng> all = new HashMap<Place, UnitLatLng>();
      for (int i = 0; i < 16384; i++) {
        Place place = nextPlace(random);
        all.put(place, place.getUnitMiddle());
        tree.add(place.getQuad(), place);
      }
      for (int i = 0; i < 32; i++) {
        Place place = Place.fromQuad(ZQuadTest.nextZQuad(random, 2 + random.nextInt(12)));
        UnitLatLngRect bounds = place.getUnitBounds();
        Set<Place> members = new HashSet<Place>(tree.getWithinQuad(place.getQuad()));
        for (Map.Entry<Place, UnitLatLng> entry : all.entrySet()) {
          boolean isMember = members.contains(entry.getKey());
          UnitLatLng point = entry.getValue();
          assertEquals(bounds.contains(point), isMember);
        }
      }
    }
  }

  /**
   * Comparator that compares places based on the toroid unit distance of their
   * midpoints.
   */
  private static class MiddleComparator implements Comparator<Place> {

    private final UnitPoint center;

    public MiddleComparator(Place center) {
      this.center = ZQuad.getMiddle(center.getQuad());
    }

    @Override
    public int compare(Place p0, Place p1) {
      return Double.compare(dist(p0), dist(p1));
    }

    private double dist(Place place) {
      UnitPoint point = ZQuad.getMiddle(place.getQuad());
      return center.getToroidDistance(point);
    }

  }

  public void testIterateByDistance() {
    Random random = new Random(23142);
    for (int loop = 0; loop < 8; loop++) {
      ZQuadTree<Place> tree = new ZQuadTree<Place>();
      Map<Place, UnitLatLng> all = new HashMap<Place, UnitLatLng>();
      for (int i = 0; i < 1024; i++) {
        Place place = nextPlace(random);
        all.put(place, place.getUnitMiddle());
        tree.add(place.getQuad(), place);
      }
      for (int i = 0; i < 16; i++) {
        Place center = nextPlace(random);
        TreeSet<Place> placesByDistance = new TreeSet<Place>(new MiddleComparator(center));
        for (Map.Entry<Place, UnitLatLng> entry : all.entrySet())
          placesByDistance.add(entry.getKey());
        Iterator<Place> placesInOrder = placesByDistance.iterator();
        for (Place place : tree.getValuesByDistance(center.getQuad()))
          assertEquals(placesInOrder.next(), place);
      }
    }
  }

}
