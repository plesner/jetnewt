package com.jetnewt.console;

import java.util.Map;
/**
 * An object that can be represented directly in the console.
 */
public interface IPlanktonObject {

  /**
   * Returns a plankton value indicating the type of this object.
   */
  public Object getType();

  /**
   * Returns a mapping from plankton values to plankton values that hold the
   * state of this object.
   */
  public Map<?, ?> getFields();

}
