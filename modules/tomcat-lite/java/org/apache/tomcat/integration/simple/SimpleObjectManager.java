/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.integration.simple;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.tomcat.integration.ObjectManager;
import org.apache.tomcat.util.IntrospectionUtils;

/**
 * This is a very small 'dependency injection'/registry poor-man substitute,
 * based on old tomcat IntrospectionUtils ( which is based on ant ).
 * Alternative would be to just pick one of spring/guice/etc and use it.
 * This class is a bit smaller and should be enough for simple use.
 *
 * How it works:
 *  - when bound, simple properties are injected in the objects using
 *  the old IntrospectionUtils, same as in original Tomcat server.xml
 *
 *  - object creation using class name - properties injected as well.
 *  Similar with how server.xml or ant works.
 *
 *  - it is based on a big Properties file, with command line arguments
 *  merged in.
 *
 * Tomcat doesn't require any of the features - they are just used to
 * allow configuration in 'default' mode, when no other framework is
 * used.
 *
 * See the Spring example for an alternative. I believe most POJO frameworks
 * can be supported.
 *
 * @author Costin Manolache
 */
public class SimpleObjectManager extends ObjectManager {
    static Logger log = Logger.getLogger(SimpleObjectManager.class.getName());

    protected Properties props = new Properties();
    protected Map<String, Object> objects = new HashMap();
    ObjectManager om;

    public SimpleObjectManager() {
        // Register PropertiesSpi
    }

    public SimpleObjectManager(String[] args) {
        this();
        bind("Main.args", args);
    }

    public void loadResource(String res) {
        InputStream in = this.getClass().getClassLoader()
            .getResourceAsStream(res);
        load(in);
    }

    public void register(ObjectManager om) {
        this.om = om;
        super.register(om);
    }

    public ObjectManager getObjectManager() {
        return om;
    }

    public void load(InputStream is) {
        try {
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Error loading default config");
        }
    }

    public Properties getProperties() {
        return props;
    }

    @Override
    public void unbind(String name) {
    }

    @Override
    public void bind(String name, Object o) {
        //log.info("Bound: " + name + " " + o);

        if ("Main.args".equals(name)) {
            try {
                processArgs((String[]) o, props);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // TODO: can I make 'inject' public - Guice seems to
        // support this.
        inject(name, o);
    }

    @Override
    public Object get(String key) {
        // Use same syntax as Spring props.
        String prop = props.getProperty(key + ".(class)");
        if (prop != null) {
            Object res = loadClass(prop);
            inject(key, res);
            return res;
        }

        return null;
    }

    private void inject(String name, Object o) {
        // Simple injection of primitive types
        String pref = name + ".";
        int prefLen = pref.length();

        for (String k: props.stringPropertyNames()) {
            if (k.startsWith(pref)) {
                if (k.endsWith(")")) {
                    continue; // special
                }
                String value = props.getProperty(k);
                value = IntrospectionUtils.replaceProperties(value,
                        props, null);
                String p = k.substring(prefLen);
                int idx = p.indexOf(".");
                if (idx > 0) {
                    // ignore suffix - indexed properties
                    p = p.substring(0, idx);
                }
                IntrospectionUtils.setProperty(o, p, value);
                log.info("Setting: " + name + " " + k + " " + value);
            }
        }
        // We could do cooler things - inject objects, etc.
    }

    private Object loadClass(String className) {
        try {
            Class c = Class.forName(className);
            Object ext = c.newInstance();
            return ext;
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Populate properties based on CLI:
     *  -key value
     *  --key=value
     *
     *  --config=FILE - load a properties file
     *
     * @param args
     * @param p
     * @param meta
     * @return everything after the first non arg not starting with '-'
     * @throws IOException
     */
    public String[] processArgs(String[] args, Properties props)
            throws IOException {

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                arg = arg.substring(2);
            } else if (arg.startsWith("-")) {
                arg = arg.substring(1);
            } else {
                String [] res = new String[args.length - i];
                System.arraycopy(args, i, res, 0, res.length);
                return res;
            }

            String name = arg;
            int eq = arg.indexOf("=");
            String value = null;
            if (eq > 0) {
                name = arg.substring(0, eq);
                value = arg.substring(eq + 1);
            } else {
                i++;
                if (i >= args.length) {
                    throw new RuntimeException("Missing param " + arg);
                }
                value = args[i];
            }

            if ("config".equals(arg)) {
                if (new File(value).exists()) {
                    load(new FileInputStream(value));
                } else {
                    loadResource(value);
                }
            } else {
                props.put(name, value);
            }
        }
        return new String[] {};
    }
}
