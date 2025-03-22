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
package org.apache.naming;

import java.io.Serial;

/**
 * Represents a reference address to a transaction.
 *
 * @author Remy Maucherat
 */
public class TransactionRef extends AbstractRef {

    @Serial
    private static final long serialVersionUID = 1L;


    /**
     * Default factory for this reference.
     */
    public static final String DEFAULT_FACTORY =
            org.apache.naming.factory.Constants.DEFAULT_TRANSACTION_FACTORY;


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
        super("jakarta.transaction.UserTransaction", factory, factoryLocation);
    }


    @Override
    protected String getDefaultFactoryClassName() {
        return DEFAULT_FACTORY;
    }
}
