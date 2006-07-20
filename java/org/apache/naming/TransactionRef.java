/*
 * Copyright 1999,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */ 


package org.apache.naming;

import javax.naming.Context;
import javax.naming.Reference;

/**
 * Represents a reference address to a transaction.
 *
 * @author Remy Maucherat
 * @version $Revision: 303133 $ $Date: 2004-08-29 18:46:15 +0200 (dim., 29 août 2004) $
 */

public class TransactionRef
    extends Reference {


    // -------------------------------------------------------------- Constants


    /**
     * Default factory for this reference.
     */
    public static final String DEFAULT_FACTORY = 
        org.apache.naming.factory.Constants.DEFAULT_TRANSACTION_FACTORY;


    // ----------------------------------------------------------- Constructors


    /**
     * Resource Reference.
     */
    public TransactionRef() {
        this(null, null);
    }


    /**
     * Resource Reference.
     *
     * @param factory The factory class
     * @param factoryLocation The factory location
     */
    public TransactionRef(String factory, String factoryLocation) {
        super("javax.transaction.UserTransaction", factory, factoryLocation);
    }


    // ----------------------------------------------------- Instance Variables


    // ------------------------------------------------------ Reference Methods


    /**
     * Retrieves the class name of the factory of the object to which this 
     * reference refers.
     */
    public String getFactoryClassName() {
        String factory = super.getFactoryClassName();
        if (factory != null) {
            return factory;
        } else {
            factory = System.getProperty(Context.OBJECT_FACTORIES);
            if (factory != null) {
                return null;
            } else {
                return DEFAULT_FACTORY;
            }
        }
    }


    // ------------------------------------------------------------- Properties


}
