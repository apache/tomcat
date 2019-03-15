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

package org.apache.tomcat.dbcp.dbcp.datasources;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

import java.util.Hashtable;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

/**
 * A JNDI ObjectFactory which creates <code>SharedPoolDataSource</code>s
 * or <code>PerUserPoolDataSource</code>s
 *
 * @version $Revision: 814246 $ $Date: 2009-09-12 18:44:58 -0400 (Sat, 12 Sep 2009) $
 */
abstract class InstanceKeyObjectFactory
    implements ObjectFactory
{
    private static final Map instanceMap = new HashMap();

    synchronized static String registerNewInstance(InstanceKeyDataSource ds) {
        int max = 0;
        Iterator i = instanceMap.keySet().iterator();
        while (i.hasNext()) {
            Object obj = i.next();
            if (obj instanceof String)
            {
                try {
                    max = Math.max(max, Integer.valueOf((String)obj).intValue());
                }
                catch (NumberFormatException e) {
                    // no sweat, ignore those keys
                }
            }
        }
        String instanceKey = String.valueOf(max + 1);
        // put a placeholder here for now, so other instances will not
        // take our key.  we will replace with a pool when ready.
        instanceMap.put(instanceKey, ds);
        return instanceKey;
    }

    static void removeInstance(String key)
    {
        instanceMap.remove(key);
    }

    /**
     * Close all pools associated with this class.
     */
    public static void closeAll() throws Exception {
        //Get iterator to loop over all instances of this datasource.
        Iterator instanceIterator = instanceMap.entrySet().iterator();
        while (instanceIterator.hasNext()) {
            ((InstanceKeyDataSource)
                ((Map.Entry) instanceIterator.next()).getValue()).close();
        }
        instanceMap.clear();
    }


    /**
     * implements ObjectFactory to create an instance of SharedPoolDataSource
     * or PerUserPoolDataSource
     */
    public Object getObjectInstance(Object refObj, Name name,
                                    Context context, Hashtable env)
        throws IOException, ClassNotFoundException {
        // The spec says to return null if we can't create an instance
        // of the reference
        Object obj = null;
        if (refObj instanceof Reference) {
            Reference ref = (Reference) refObj;
            if (isCorrectClass(ref.getClassName())) {
                RefAddr ra = ref.get("instanceKey");
                if (ra != null && ra.getContent() != null) {
                    // object was bound to jndi via Referenceable api.
                    obj = instanceMap.get(ra.getContent());
                }
                else
                {
                    // tomcat jndi creates a Reference out of server.xml
                    // <ResourceParam> configuration and passes it to an
                    // instance of the factory given in server.xml.
                    String key = null;
                    if (name != null)
                    {
                        key = name.toString();
                        obj = instanceMap.get(key);
                    }
                    if (obj == null)
                    {
                        InstanceKeyDataSource ds = getNewInstance(ref);
                        setCommonProperties(ref, ds);
                        obj = ds;
                        if (key != null)
                        {
                            instanceMap.put(key, ds);
                        }
                    }
                }
            }
        }
        return obj;
    }

    private void setCommonProperties(Reference ref,
                                     InstanceKeyDataSource ikds)
        throws IOException, ClassNotFoundException {

        RefAddr ra = ref.get("dataSourceName");
        if (ra != null && ra.getContent() != null) {
            ikds.setDataSourceName(ra.getContent().toString());
        }

        ra = ref.get("defaultAutoCommit");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultAutoCommit(Boolean.valueOf(
                ra.getContent().toString()).booleanValue());
        }

        ra = ref.get("defaultReadOnly");
        if (ra != null && ra.getContent() != null) {
            ikds.setDefaultReadOnly(Boolean.valueOf(
                ra.getContent().toString()).booleanValue());
        }

        ra = ref.get("description");
        if (ra != null && ra.getContent() != null) {
            ikds.setDescription(ra.getContent().toString());
        }

        ra = ref.get("jndiEnvironment");
        if (ra != null  && ra.getContent() != null) {
            byte[] serialized = (byte[]) ra.getContent();
            ikds.jndiEnvironment =
                (Properties) deserialize(serialized);
        }

        ra = ref.get("loginTimeout");
        if (ra != null && ra.getContent() != null) {
            ikds.setLoginTimeout(
                Integer.parseInt(ra.getContent().toString()));
        }

        ra = ref.get("testOnBorrow");
        if (ra != null && ra.getContent() != null) {
            ikds.setTestOnBorrow(Boolean.valueOf(
                ra.getContent().toString()).booleanValue());
        }

        ra = ref.get("testOnReturn");
        if (ra != null && ra.getContent() != null) {
            ikds.setTestOnReturn(Boolean.valueOf(
                ra.getContent().toString()).booleanValue());
        }

        ra = ref.get("timeBetweenEvictionRunsMillis");
        if (ra != null && ra.getContent() != null) {
            ikds.setTimeBetweenEvictionRunsMillis(
                Integer.parseInt(ra.getContent().toString()));
        }

        ra = ref.get("numTestsPerEvictionRun");
        if (ra != null && ra.getContent() != null) {
            ikds.setNumTestsPerEvictionRun(
                Integer.parseInt(ra.getContent().toString()));
        }

        ra = ref.get("minEvictableIdleTimeMillis");
        if (ra != null && ra.getContent() != null) {
            ikds.setMinEvictableIdleTimeMillis(
                Integer.parseInt(ra.getContent().toString()));
        }

        ra = ref.get("testWhileIdle");
        if (ra != null && ra.getContent() != null) {
            ikds.setTestWhileIdle(Boolean.valueOf(
                ra.getContent().toString()).booleanValue());
        }

        ra = ref.get("validationQuery");
        if (ra != null && ra.getContent() != null) {
            ikds.setValidationQuery(ra.getContent().toString());
        }
    }


    /**
     * @return true if and only if className is the value returned
     * from getClass().getName().toString()
     */
    protected abstract boolean isCorrectClass(String className);

    /**
     * Creates an instance of the subclass and sets any properties
     * contained in the Reference.
     */
    protected abstract InstanceKeyDataSource getNewInstance(Reference ref)
        throws IOException, ClassNotFoundException;

    /**
     * used to set some properties saved within a Reference
     */
    protected static final Object deserialize(byte[] data)
        throws IOException, ClassNotFoundException {
        ObjectInputStream in = null;
        try {
            in = new ObjectInputStream(new ByteArrayInputStream(data));
            return in.readObject();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ex) {
                }
            }
        }
    }
}

