package com.jetnewt.geo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

public class ZQuadTree<T> {

  int steps = 0;

  /**
   * The number of sub-nodes each junction node can have.
   */
  private static final int kJunctionChildCount = 4;

  /**
   * A tree leaf that holds a value and the quad that indicates the value's
   * location.
   */
  private static class Leaf<T> {
    public final long quad;
    public final T value;
    public Leaf(long quad, T value) {
      this.quad = quad;
      this.value = value;
    }
  }

  /**
   * A quad tree node. A node has four different modes: empty, singleton,
   * small, junction. An empty node is self-explanatory. A node with a single
   * element stores the element directly. A small node has a few elements which
   * are stored in an array. A junction has more elements none of which are
   * stored directly in the node, instead they are stored in child nodes.
   */
  private static class Node<T> {

    private static final Node<?> kEmpty = new Node<Object>(0);
    private static final Leaf<?>[] kNoLeaves = new Leaf<?>[0];

    private final long quad;
    private int size = 0;
    private Object data = kNoLeaves;

    public Node(long quad) {
      this.quad = quad;
    }

    /**
     * Add the given leaf to this node.
     */
    public void add(ZQuadTree<?> tree, Leaf<?> leaf) {
      if (this.size < tree.splitThreshold) {
        // With fewer than the threshold elements we store them directly in
        // an array in the data field.
        Leaf<?>[] leaves;
        if (this.size == 0) {
          // If this is currently a singleton we have to switch representation.
          leaves = new Leaf<?>[tree.splitThreshold];
          this.data = leaves;
        } else {
          // This was a small node already so it has the right representation.
          leaves = (Leaf<?>[]) this.data;
        }
        leaves[this.size] = leaf;
      } else {
        // This has grown larger than what we're willing to store in a small
        // node.
        if (this.size == tree.splitThreshold) {
          // First time this happens we have to switch representation.
          Leaf<?>[] oldValues = (Leaf<?>[]) this.data;
          this.data = new Node<?>[kJunctionChildCount];
          for (int i = 0; i < this.size; i++)
            addToJunctionChild(tree, oldValues[i]);
        }
        addToJunctionChild(tree, leaf);
      }
      this.size++;
    }

    /**
     * Add a leaf to the appropriate child of this junction.
     */
    private void addToJunctionChild(ZQuadTree<?> tree, Leaf<?> leaf) {
      Node<?>[] children = (Node<?>[]) this.data;
      // Determine which child node this leaf belongs to.
      long destinationChild = ZQuad.getChildThatsAncestorOf(this.quad, leaf.quad);
      // Get the child's scalar index from 0 to 3.
      int index = getChildIndex(leaf.quad);
      if (children[index] == null)
        // Create the child node if it doesn't already exist.
        children[index] = new Node<T>(destinationChild);
      children[index].add(tree, leaf);
    }

    private int getChildIndex(long descendent) {
      // Determine which child node the descendant belongs to.
      long destinationChild = ZQuad.getChildThatsAncestorOf(this.quad, descendent);
      // What is the descendancy of that child relative to this one?
      long descendancy = ZQuad.getDescendancy(destinationChild, 1);
      // The scalar component is what we're looking for.
      return (int) ZQuad.quadToScalar(descendancy, 1);
    }

    /**
     * Given the quad of a descendant, adds all the values in this node that
     * lie within the descendant.
     */
    private void addDescendantsWithinQuad(ZQuadTree<?> tree, long quad,
        ArrayList<Object> elms) {
      Node<?> closest = findClosestAncestor(tree, quad);
      if (closest.quad == quad) {
        closest.addAllMembers(tree, elms);
      } else {
        assert closest.size <= tree.splitThreshold;
        Leaf<?>[] children = (Leaf<?>[]) closest.data;
        for (int i = 0; i < closest.size; i++) {
          addIfDescendant(children[i], quad, elms);
        }
      }
    }

    /**
     * Finds the node within this tree that is the closest ancestor to the
     * given quad. That is, either an ancestor which is not a junction (so any
     * of the children may be within the quad) or a junction covering exactly
     * the given quad.
     */
    private Node<?> findClosestAncestor(ZQuadTree<?> tree, long quad) {
      assert ZQuad.isAncestor(this.quad, quad);
      if (this.size <= tree.splitThreshold || this.quad == quad) {
        return this;
      } else {
        Node<?>[] children = (Node<?>[]) data;
        for (int i = 0; i < kJunctionChildCount; i++) {
          Node<?> child = children[i];
          if (child != null && ZQuad.isAncestor(child.quad, quad))
            return child.findClosestAncestor(tree, quad);
        }
        return kEmpty;
      }
    }

    /**
     * Add the given leaf if it is a descendant of the given quad.
     */
    private static void addIfDescendant(Leaf<?> leaf, long quad, ArrayList<Object> elms) {
      if (ZQuad.isAncestor(quad, leaf.quad))
        elms.add(leaf.value);
    }

    /**
     * Adds all leave values within this node to the given array.
     */
    private void addAllMembers(ZQuadTree<?> tree, ArrayList<Object> elms) {
      if (size <= tree.splitThreshold) {
        Leaf<?>[] children = (Leaf<?>[]) data;
        for (int i = 0; i < size; i++)
          elms.add(children[i].value);
      } else {
        Node<?>[] children = (Node<?>[]) data;
        for (int i = 0; i < kJunctionChildCount; i++) {
          Node<?> child = children[i];
          if (child != null)
            child.addAllMembers(tree, elms);
        }
      }
    }

  }

  private final int splitThreshold = 4;
  private final Node<T> root = new Node<T>(ZQuad.kEverything);

  public void add(long quad, T value) {
    Leaf<T> leaf = new Leaf<T>(quad, value);
    root.add(this, leaf);
  }

  /**
   * Returns all the values within this tree that lie within the given quad.
   */
  @SuppressWarnings("unchecked")
  public Collection<T> getWithinQuad(long quad) {
    ArrayList<Object> result = new ArrayList<Object>();
    root.addDescendantsWithinQuad(this, quad, result);
    return (ArrayList<T>) result;
  }

  private static interface IRegion {

    public double getMinDistance();

    public void apply(ValueIterator<?> iter);

  }

  private static class NodeRegion implements IRegion {

    private final double minDistance;
    private final Node<?> node;

    public NodeRegion(double minDistance, Node<?> node) {
      this.minDistance = minDistance;
      this.node = node;
    }

    @Override
    public double getMinDistance() {
      return this.minDistance;
    }

    @Override
    public void apply(ValueIterator<?> iter) {
      if (this.node.size <= iter.tree.splitThreshold) {
        Leaf<?>[] leaves = (Leaf<?>[]) this.node.data;
        for (int i = 0; i < this.node.size; i++)
          iter.enqueueLeaf(leaves[i]);
      } else {
        Node<?>[] children = (Node<?>[]) this.node.data;
        for (int i = 0; i < kJunctionChildCount; i++) {
          if (children[i] != null)
            iter.enqueueNode(children[i]);
        }
      }
    }

  }

  private static class LeafRegion implements IRegion {

    private final double distance;
    private final Leaf<?> leaf;

    public LeafRegion(double distance, Leaf<?> leaf) {
      this.distance = distance;
      this.leaf = leaf;
    }

    @Override
    public double getMinDistance() {
      return this.distance;
    }

    @Override
    public void apply(ValueIterator<?> iter) {
      iter.nextLeaf = leaf;
    }

  }

  private static class RegionComparator implements Comparator<IRegion> {

    @Override
    public int compare(IRegion r1, IRegion r2) {
      return Double.compare(r1.getMinDistance(), r2.getMinDistance());
    }

  }

  public static class ValueIterator<T> implements Iterator<T> {

    private final UnitPoint point;
    private Leaf<?> nextLeaf = null;
    public final PriorityQueue<IRegion> queue = new PriorityQueue<IRegion>(256, new RegionComparator());
    private final ZQuadTree<?> tree;

    public ValueIterator(ZQuadTree<T> tree, long quad) {
      this.point = ZQuad.getMiddle(quad);
      this.tree = tree;
      this.enqueueNode(tree.root);
      this.fillValues();
    }

    private void enqueueNode(Node<?> node) {
      int zoom = ZQuad.getZoomLevel(node.quad);
      UnitPoint topLeft = ZQuad.getTopLeft(node.quad, zoom);
      double length = ZQuad.getLength(zoom);
      double left = topLeft.getX();
      double right = left + length;
      double top = topLeft.getY();
      double bottom = top + length;
      double x = point.getX();
      double y = point.getY();
      double dx, dy;
      if (left <= x && x <= right) {
        dx = 0;
      } else {
        dx = Math.min(UnitPoint.getToroidDelta(left, x),
            UnitPoint.getToroidDelta(right, x));
      }
      if (top <= y && y <= bottom) {
        dy = 0;
      } else {
        dy = Math.min(UnitPoint.getToroidDelta(top, y),
            UnitPoint.getToroidDelta(bottom, y));
      }
      double distance = Math.sqrt(dx * dx + dy * dy);
      queue.add(new NodeRegion(distance, node));
    }

    private void enqueueLeaf(Leaf<?> leaf) {
      UnitPoint middle = ZQuad.getMiddle(leaf.quad);
      double distance = point.getToroidDistance(middle);
      queue.add(new LeafRegion(distance, leaf));
    }

    private void fillValues() {
      while (nextLeaf == null && !queue.isEmpty()) {
        IRegion region = queue.remove();
        region.apply(this);
      }
    }

    @Override
    public boolean hasNext() {
      return nextLeaf != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T next() {
      Object result = this.nextLeaf.value;
      this.nextLeaf = null;
      this.fillValues();
      return (T) result;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

  }

  private Iterator<T> iterateValuesByDistance(long quad) {
    return new ValueIterator<T>(this, quad);
  }

  public Iterable<T> getValuesByDistance(final long quad) {
    return new Iterable<T>() {
      @Override
      public Iterator<T> iterator() {
        return iterateValuesByDistance(quad);
      }
    };
  }

}
