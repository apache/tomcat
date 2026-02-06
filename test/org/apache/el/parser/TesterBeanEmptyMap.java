package org.apache.el.parser;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TesterBeanEmptyMap extends HashMap<Object, Map<String, Object>> {

    @Override public Map<String, Object> get(Object key) {
        return Collections.emptyMap();
    }
}
