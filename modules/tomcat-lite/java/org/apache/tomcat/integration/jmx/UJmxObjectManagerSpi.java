/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.tomcat.integration.jmx;


import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.tomcat.integration.DynamicObject;
import org.apache.tomcat.integration.ObjectManager;

/**
 * Send all registered JMX objects and properties as JSON.
 * 
 * Based on JMXProxy servlet, but:
 * - Async handler instead of servlet - so it works with 'raw' connector
 * - doesn't use JMX - integrates with the ObjectManager ( assuming OM 
 * provies a list of managed objects )
 * - all the reflection magic from modeler is implemented here.
 *
 * @author Costin Manolache
 */
public class UJmxObjectManagerSpi extends ObjectManager {

    private static Logger log = Logger.getLogger(UJmxObjectManagerSpi.class.getName());
    
    private ObjectManager om;
    
    Map<Class, DynamicObject> types = new HashMap<Class, DynamicObject>();
    
    Map<String, Object> objects = new HashMap();
    
    @Override
    public void bind(String name, Object o) {
        if (objects.get(name) != null) {
            log.warning("Duplicated name " + name);
        }
        objects.put(name, o);
    }

    @Override
    public void unbind(String name) {
        objects.remove(name);
    }
    
    // Dynamic
    public void setObjectManager(ObjectManager om) {
        this.om = om;
    }
    
    DynamicObject getClassInfo(Class beanClass) {
        if (types.get(beanClass) != null) {
            return types.get(beanClass);
        }
        DynamicObject res = new DynamicObject(beanClass);
        types.put(beanClass, res);
        return res;
    }
}
