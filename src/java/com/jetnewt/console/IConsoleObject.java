package com.jetnewt.console;
/**
 * Marker for a type of object that has some representation on the console.
 */
public interface IConsoleObject {

  /**
   * Return a plankton value that represents this object on the console.
   */
  public Object toPlankton();

}
