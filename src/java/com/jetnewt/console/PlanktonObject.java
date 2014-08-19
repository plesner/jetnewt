package com.jetnewt.console;

import java.util.HashMap;
import java.util.Map;
/**
 * Default implementation of a mutable plankton object.
 */
public class PlanktonObject implements IPlanktonObject {

  private final Object type;
  private final Map<Object, Object> fields = new HashMap<Object, Object>();

  public PlanktonObject(Object type) {
    this.type = type;
  }

  public PlanktonObject setField(Object key, Object value) {
    fields.put(key, value);
    return this;
  }

  @Override
  public Object getType() {
    return this.type;
  }

  @Override
  public Map<?, ?> getFields() {
    return this.fields;
  }

}
