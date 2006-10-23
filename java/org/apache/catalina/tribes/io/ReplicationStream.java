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


package org.apache.catalina.tribes.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;

/**
 * Custom subclass of <code>ObjectInputStream</code> that loads from the
 * class loader for this web application.  This allows classes defined only
 * with the web application to be found correctly.
 *
 * @author Craig R. McClanahan
 * @author Bip Thelin
 * @author Filip Hanik
 * @version $Revision$, $Date$
 */

public final class ReplicationStream extends ObjectInputStream {

    
    /**
     * The class loader we will use to resolve classes.
     */
    private ClassLoader[] classLoaders = null;
    

    /**
     * Construct a new instance of CustomObjectInputStream
     *
     * @param stream The input stream we will read from
     * @param classLoader The class loader used to instantiate objects
     *
     * @exception IOException if an input/output error occurs
     */
    public ReplicationStream(InputStream stream,
                             ClassLoader[] classLoaders)
        throws IOException {

        super(stream);
        this.classLoaders = classLoaders;
    }

    /**
     * Load the local class equivalent of the specified stream class
     * description, by using the class loader assigned to this Context.
     *
     * @param classDesc Class description from the input stream
     *
     * @exception ClassNotFoundException if this class cannot be found
     * @exception IOException if an input/output error occurs
     */
    public Class resolveClass(ObjectStreamClass classDesc)
        throws ClassNotFoundException, IOException {
        String name = classDesc.getName();
        boolean tryRepFirst = name.startsWith("org.apache.catalina.tribes");
        try {
            try
            {
                if ( tryRepFirst ) return findReplicationClass(name);
                else return findExternalClass(name);
            }
            catch ( Exception x )
            {
                if ( tryRepFirst ) return findExternalClass(name);
                else return findReplicationClass(name);
            }
        } catch (ClassNotFoundException e) {
            return super.resolveClass(classDesc);
        }
    }
    
    public Class findReplicationClass(String name)
        throws ClassNotFoundException, IOException {
        Class clazz = Class.forName(name, false, getClass().getClassLoader());
        return clazz;
    }

    public Class findExternalClass(String name) throws ClassNotFoundException  {
        ClassNotFoundException cnfe = null;
        for (int i=0; i<classLoaders.length; i++ ) {
            try {
                Class clazz = Class.forName(name, false, classLoaders[i]);
                return clazz;
            } catch ( ClassNotFoundException x ) {
                cnfe = x;
            } 
        }
        if ( cnfe != null ) throw cnfe;
        else throw new ClassNotFoundException(name);
    }
    
    public void close() throws IOException  {
        this.classLoaders = null;
        super.close();
    }


}
