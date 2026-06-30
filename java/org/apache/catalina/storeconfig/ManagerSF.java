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

import org.apache.catalina.Globals;
import org.apache.catalina.Manager;
import org.apache.catalina.SessionIdGenerator;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.util.SessionIdGeneratorBase;
import org.apache.catalina.util.StandardSessionIdGenerator;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Store server.xml Manager element
 */
public class ManagerSF extends StoreFactoryBase {

    /**
     * Default constructor.
     */
    public ManagerSF() {
    }

    private static final Log log = LogFactory.getLog(ManagerSF.class);

    @Override
    public void store(PrintWriter aWriter, int indent, Object aElement) throws Exception {
        StoreDescription elementDesc = getRegistry().findDescription(aElement.getClass());
        if (elementDesc != null) {
            if (aElement instanceof StandardManager manager) {
                if (!isDefaultManager(manager)) {
                    if (log.isTraceEnabled()) {
                        log.trace(sm.getString("factory.storeTag", elementDesc.getTag(), aElement));
                    }
                    super.store(aWriter, indent, aElement);
                }
            } else {
                super.store(aWriter, indent, aElement);
            }
        } else {
            log.warn(sm.getString("factory.storeNoDescriptor", aElement.getClass()));
        }
    }

    /**
     * Is this an instance of the default <code>Manager</code> configuration, with all-default properties?
     *
     * @param smanager Manager to be tested
     *
     * @return <code>true</code> if this is an instance of the default manager
     */
    protected boolean isDefaultManager(StandardManager smanager) {

        // StandardManager-specific property
        if (smanager.getPathname() != null) {
            return false;
        }

        // ManagerBase properties
        if (smanager.getMaxActiveSessions() != -1) {
            return false;
        }
        if (smanager.getSecureRandomClass() != null) {
            return false;
        }
        if (!SessionIdGeneratorBase.DEFAULT_SECURE_RANDOM_ALGORITHM.equals(smanager.getSecureRandomAlgorithm())) {
            return false;
        }
        if (smanager.getSecureRandomProvider() != null) {
            return false;
        }
        if (smanager.getProcessExpiresFrequency() != 6) {
            return false;
        }
        if (smanager.getSessionAttributeNameFilter() != null) {
            return false;
        }
        if (smanager.getSessionAttributeValueClassNameFilter() != null) {
            return false;
        }
        if (smanager.getWarnOnSessionAttributeFilterFailure()) {
            return false;
        }
        if (smanager.getNotifyBindingListenerOnUnchangedValue()) {
            return false;
        }
        if (!smanager.getNotifyAttributeListenerOnUnchangedValue()) {
            return false;
        }
        if (smanager.getPersistAuthentication()) {
            return false;
        }
        if (smanager.getSessionActivityCheck() != Globals.STRICT_SERVLET_COMPLIANCE) {
            return false;
        }
        if (smanager.getSessionLastAccessAtStart() != Globals.STRICT_SERVLET_COMPLIANCE) {
            return false;
        }
        SessionIdGenerator sessionIdGenerator = smanager.getSessionIdGenerator();
        SessionIdGeneratorBase sigBase = null;
        if (sessionIdGenerator == null || !StandardSessionIdGenerator.class.isInstance(sessionIdGenerator)) {
            return false;
        }
        sigBase = (SessionIdGeneratorBase) sessionIdGenerator;
        if (!"".equals(sigBase.getJvmRoute())) {
            return false;
        }
        if (sigBase.getSecureRandomClass() != null) {
            return false;
        }
        if (!SessionIdGeneratorBase.DEFAULT_SECURE_RANDOM_ALGORITHM.equals(sigBase.getSecureRandomAlgorithm())) {
            return false;
        }
        if (sigBase.getSecureRandomProvider() != null) {
            return false;
        }
        if (sigBase.getSessionIdLength() != 16) {
            return false;
        }

        return true;

    }

    @Override
    public void storeChildren(PrintWriter aWriter, int indent, Object aManager, StoreDescription parentDesc)
            throws Exception {
        if (aManager instanceof Manager manager) {
            // Store nested <SessionIdGenerator> element;
            SessionIdGenerator sessionIdGenerator = manager.getSessionIdGenerator();
            if (sessionIdGenerator != null) {
                storeElement(aWriter, indent, sessionIdGenerator);
            }
        }
    }

}
