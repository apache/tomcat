/*
 */
package org.apache.tomcat.test.watchdog;

import java.util.Hashtable;

/**
 * Extracted from IntrospectionHelper - a simple utility class to
 * do ant style ${property} replacements on a string, using a map
 * holding properties. Also allows a hook for dynamic, on-demand
 * properties.
 *
 * @author Costin Manolache
 */
public class AntProperties {
    public static interface PropertySource {
        public String getProperty(String key);
    }

    /**
     * Replace ${NAME} with the property value
     */
    public static String replaceProperties(String value,
            Hashtable<Object,Object> staticProp, PropertySource dynamicProp[]) {
        if (value.indexOf("$") < 0) {
            return value;
        }
        StringBuffer sb = new StringBuffer();
        int prev = 0;
        // assert value!=nil
        int pos;
        while ((pos = value.indexOf("$", prev)) >= 0) {
            if (pos > 0) {
                sb.append(value.substring(prev, pos));
            }
            if (pos == (value.length() - 1)) {
                sb.append('$');
                prev = pos + 1;
            } else if (value.charAt(pos + 1) != '{') {
                sb.append('$');
                prev = pos + 1; // XXX
            } else {
                int endName = value.indexOf('}', pos);
                if (endName < 0) {
                    sb.append(value.substring(pos));
                    prev = value.length();
                    continue;
                }
                String n = value.substring(pos + 2, endName);
                String v = null;
                if (staticProp != null) {
                    v = (String) staticProp.get(n);
                }
                if (v == null && dynamicProp != null) {
                    for (int i = 0; i < dynamicProp.length; i++) {
                        v = dynamicProp[i].getProperty(n);
                        if (v != null) {
                            break;
                        }
                    }
                }
                if (v == null)
                    v = "${" + n + "}";

                sb.append(v);
                prev = endName + 1;
            }
        }
        if (prev < value.length())
            sb.append(value.substring(prev));
        return sb.toString();
    }


}
