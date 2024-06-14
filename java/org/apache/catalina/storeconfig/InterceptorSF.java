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

import org.apache.catalina.tribes.ChannelInterceptor;
import org.apache.catalina.tribes.group.interceptors.StaticMembershipInterceptor;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Generate Interceptor Element
 */
public class InterceptorSF extends StoreFactoryBase {

    private static Log log = LogFactory.getLog(InterceptorSF.class);

    @Override
    public void store(PrintWriter aWriter, int indent, Object aElement) throws Exception {
        if (aElement instanceof StaticMembershipInterceptor) {
            StoreDescription elementDesc = getRegistry().findDescription(aElement.getClass());

            if (elementDesc != null) {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("factory.storeTag", elementDesc.getTag(), aElement));
                }
                getStoreAppender().printIndent(aWriter, indent + 2);
                getStoreAppender().printOpenTag(aWriter, indent + 2, aElement, elementDesc);
                storeChildren(aWriter, indent + 2, aElement, elementDesc);
                getStoreAppender().printIndent(aWriter, indent + 2);
                getStoreAppender().printCloseTag(aWriter, elementDesc);
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(sm.getString("factory.storeNoDescriptor", aElement.getClass()));
                }
            }
        } else {
            super.store(aWriter, indent, aElement);
        }
    }

    /**
     * Store the specified Interceptor child.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aInterceptor, StoreDescription parentDesc)
            throws Exception {
        if (aInterceptor instanceof StaticMembershipInterceptor) {
            ChannelInterceptor interceptor = (ChannelInterceptor) aInterceptor;
            // Store nested <Member> elements
            storeElementArray(aWriter, indent + 2, interceptor.getMembers());
        }
    }
}