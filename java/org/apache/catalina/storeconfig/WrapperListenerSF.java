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

import org.apache.catalina.core.StandardContext;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class WrapperListenerSF extends StoreFactoryBase {
    private static Log log = LogFactory.getLog(WrapperListenerSF.class);

    /*
     * Store nested Element Value Arrays
     *
     * @see org.apache.catalina.config.IStoreFactory#store(java.io.PrintWriter,
     *      int, java.lang.Object)
     */
    @Override
    public void store(PrintWriter aWriter, int indent, Object aElement)
            throws Exception {
        if (aElement instanceof StandardContext) {
            StoreDescription elementDesc = getRegistry().findDescription(
                    aElement.getClass().getName() + ".[WrapperListener]");
            String[] listeners = ((StandardContext) aElement)
                    .findWrapperListeners();
            if (elementDesc != null) {
                if (log.isDebugEnabled()) {
                    log.debug("store " + elementDesc.getTag() + "( " + aElement
                            + " )");
                }
                getStoreAppender().printTagArray(aWriter, "WrapperListener",
                        indent, listeners);
            }
        } else {
            log.warn("Descriptor for element" + aElement.getClass()
                    + ".[WrapperListener] not configured!");
        }
    }
}