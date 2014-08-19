package com.jetnewt.console;

import java.util.Collection;
import java.util.Map;

public class Console {

  private static void writeJson(Object val, StringBuffer buf) {
    if (val instanceof IConsoleObject) {
      IConsoleObject loggable = (IConsoleObject) val;
      writeJson(loggable.toPlankton(), buf);
    } else if (val instanceof Number) {
      Number num = (Number) val;
      if (val instanceof Double || val instanceof Float) {
        buf.append((double) num.doubleValue());
      } else {
        buf.append((long) num.longValue());
      }
    } else if (val instanceof CharSequence) {
      buf.append("\"" + val + "\"");
    } else if (val instanceof Collection<?>) {
      Collection<?> coll = (Collection<?>) val;
      buf.append("[");
      boolean isFirst = true;
      for (Object elm : coll) {
        if (isFirst) {
          isFirst = false;
        } else {
          buf.append(", ");
        }
        writeJson(elm, buf);
      }
      buf.append("]");
    } else if (val instanceof Map<?, ?>) {
      Map<?, ?> map = (Map<?, ?>) val;
      buf.append("{");
      boolean isFirst = true;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (isFirst) {
          isFirst = false;
        } else {
          buf.append(", ");
        }
        writeJson(entry.getKey(), buf);
        buf.append(": ");
        writeJson(entry.getValue(), buf);
      }
      buf.append("}");
    } else if (val instanceof IPlanktonObject) {
      IPlanktonObject obj = (IPlanktonObject) val;
      buf.append("{\"type\": ");
      writeJson(obj.getType(), buf);
      for (Map.Entry<?, ?> entry : obj.getFields().entrySet()) {
        buf.append(", ");
        writeJson(entry.getKey(), buf);
        buf.append(": ");
        writeJson(entry.getValue(), buf);
      }
      buf.append("}");
    } else {
      writeJson(val.toString(), buf);
    }
  }

  public static void println(Object variant) {
    StringBuffer buf = new StringBuffer();
    buf.append("-=#");
    writeJson(variant, buf);
    buf.append("#=-");
    System.out.println(buf.toString());
  }

}
