package com.jetnewt.geo;
/**
 * Utility that maps unit values (values between 0 and 1) to and from ordinates
 * within some other symmetrict interval around 0.
 */
public class OrdinateConverter {

  private final double span;
  private final double width;
  private final double sign;

  public OrdinateConverter(double span) {
    this.span = Math.abs(span);
    this.width = (this.span * 2);
    this.sign = this.span / span;
  }

  public double toUnit(double value) {
    assert Math.abs(value) <= this.span;
    return (this.span + (this.sign * value)) / this.width;
  }

  public double toUnit(int degs, int mins, double secs, boolean isForward) {
    double value = degs + (mins / 60.0) + (secs / 3600.0);
    return toUnit(isForward ? value : -value);
  }

  public double fromUnit(double value) {
    assert 0.0 <= value;
    assert value <= 1.0;
    return ((value * this.width) - this.span) / this.sign;
  }

  public int unitToDegrees(double value) {
    return (int) Math.abs(fromUnit(value));
  }

  public int unitToMinutes(double value) {
    return ((int) (Math.abs(fromUnit(value)) * 60.0)) % 60;
  }

  public double unitToSeconds(double value) {
    return (Math.abs(fromUnit(value)) * 3600.0) % 60.0;
  }

}
