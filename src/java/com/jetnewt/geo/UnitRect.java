package com.jetnewt.geo;
/**
 * A double-valued rectangle within the unit square.
 */
public class UnitRect {

  private final double top;
  private final double left;
  private final double bottom;
  private final double right;

  /**
   * There is no uniquely most obvious way to construct a rect, it both makes
   * sense to use boundaries and corner + extent so this constructor is private
   * so you have to explicitly use the constructor functions below.
   */
  private UnitRect(double top, double left, double bottom, double right) {
    assert 0 <= top && top <= 1;
    assert 0 <= left && left <= 1;
    assert 0 <= bottom && bottom <= 1;
    assert 0 <= right && right <= 1;
    this.top = top;
    this.left = left;
    this.bottom = bottom;
    this.right = right;
  }

  public double getTop() {
    return this.top;
  }

  public double getLeft() {
    return this.left;
  }

  public double getBottom() {
    return this.bottom;
  }

  public double getRight() {
    return this.right;
  }

  public double getWidth() {
    return this.right - this.left;
  }

  public double getHeight() {
    return this.bottom - this.top;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!(obj instanceof UnitRect))
      return false;
    UnitRect that = (UnitRect) obj;
    return equals(that, 0.0);
  }

  /**
   * Returns true if this and the given rect are at most delta apart on any
   * of the boundaries.
   */
  private boolean equals(UnitRect that, double delta) {
    return Math.abs(this.top - that.top) <= delta
        && Math.abs(this.left - that.left) <= delta
        && Math.abs(this.bottom - that.bottom) <= delta
        && Math.abs(this.right - that.right) <= delta;
  }

  /**
   * Is the given unit point within this rect?
   */
  public boolean contains(UnitPoint p) {
    return (this.top <= p.getY()) && (this.left <= p.getX())
        && (p.getY() <= this.bottom) && (p.getX() <= this.right);
  }

  /**
   * Returns a rect that lies on the given boundaries.
   */
  public static UnitRect fromBounds(double top, double left, double bottom, double right) {
    return new UnitRect(top, left, bottom, right);
  }

  /**
   * Returns a rect whose top left corner is at the given position and which
   * has the given extent.
   */
  public static UnitRect fromCorner(double top, double left, double width, double height) {
    return fromBounds(top, left, top + height, left + width);
  }

}
