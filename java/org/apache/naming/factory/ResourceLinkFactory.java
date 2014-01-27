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


package org.apache.naming.factory;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import org.apache.naming.ResourceLinkRef;


/**
 * <p>Object factory for resource links.</p>
 * 
 * @author Remy Maucherat
 */
public class ResourceLinkFactory
    implements ObjectFactory {


    // ----------------------------------------------------------- Constructors


    // ------------------------------------------------------- Static Variables


    /**
     * Global naming context.
     */
    private static Context globalContext = null;


    // --------------------------------------------------------- Public Methods


    /**
     * Set the global context (note: can only be used once).
     * 
     * @param newGlobalContext new global context value
     */
    public static void setGlobalContext(Context newGlobalContext) {
        globalContext = newGlobalContext;
    }


    // -------------------------------------------------- ObjectFactory Methods


    /**
     * Create a new DataSource instance.
     * 
     * @param obj The reference object describing the DataSource
     */
    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
                                    Hashtable<?,?> environment)
        throws NamingException {
        
        if (!(obj instanceof ResourceLinkRef))
            return null;

        // Can we process this request?
        Reference ref = (Reference) obj;

        // Read the global ref addr
        String globalName = null;
        RefAddr refAddr = ref.get(ResourceLinkRef.GLOBALNAME);
        if (refAddr != null) {
            globalName = refAddr.getContent().toString();
            Object result = null;
            result = globalContext.lookup(globalName);
            // FIXME: Check type
            return result;
        }

        return (null);

        
    }


}
