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

package org.apache.catalina.storeconfig;

import java.io.PrintWriter;

import org.apache.catalina.CredentialHandler;
import org.apache.catalina.realm.NestedCredentialHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Store server.xml Element CredentialHandler
 */
public class CredentialHandlerSF extends StoreFactoryBase {

    private static Log log = LogFactory.getLog(CredentialHandlerSF.class);

    @Override
    public void store(PrintWriter aWriter, int indent, Object aElement)
            throws Exception {
        if (aElement instanceof NestedCredentialHandler) {
            StoreDescription elementDesc = getRegistry().findDescription(
                    aElement.getClass());

            if (elementDesc != null) {
                if (log.isDebugEnabled())
                    log.debug(sm.getString("factory.storeTag",
                            elementDesc.getTag(), aElement));
                getStoreAppender().printIndent(aWriter, indent + 2);
                getStoreAppender().printOpenTag(aWriter, indent + 2, aElement,
                            elementDesc);
                storeChildren(aWriter, indent + 2, aElement, elementDesc);
                getStoreAppender().printIndent(aWriter, indent + 2);
                getStoreAppender().printCloseTag(aWriter, elementDesc);
            } else {
                if (log.isWarnEnabled())
                    log.warn(sm.getString("factory.storeNoDescriptor",
                            aElement.getClass()));
            }
        } else {
            super.store(aWriter, indent, aElement);
        }
    }

    /**
     * Store the specified CredentialHandler properties and child (CredentialHandler)
     *
     * @param aWriter
     *            PrintWriter to which we are storing
     * @param indent
     *            Number of spaces to indent this element
     * @param aCredentialHandler
     *            CredentialHandler whose properties are being stored
     *
     * @exception Exception
     *                if an exception occurs while storing
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aCredentialHandler,
            StoreDescription parentDesc) throws Exception {
        if (aCredentialHandler instanceof NestedCredentialHandler) {
            NestedCredentialHandler nestedCredentialHandler = (NestedCredentialHandler) aCredentialHandler;

            // Store nested <CredentialHandler> element
            CredentialHandler[] credentialHandlers = nestedCredentialHandler.getCredentialHandlers();
            storeElementArray(aWriter, indent, credentialHandlers);
        }
    }

}