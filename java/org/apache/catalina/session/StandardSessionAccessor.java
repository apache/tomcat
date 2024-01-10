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
package org.apache.catalina.session;

import java.io.IOException;
import java.util.function.Consumer;

import jakarta.servlet.http.HttpSession;

import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.res.StringManager;

public class StandardSessionAccessor implements HttpSession.Accessor {

    private static final Log log = LogFactory.getLog(StandardSessionAccessor.class);
    private static final StringManager sm = StringManager.getManager(StandardSessionAccessor.class);

    private final Manager manager;
    private final String id;


    public StandardSessionAccessor(Manager manager, String id) {
        if (manager == null) {
            throw new IllegalStateException(sm.getString("standardSessionAccessor.nullManager"));
        }
        if (id == null) {
            throw new IllegalStateException(sm.getString("standardSessionAccessor.nullId"));
        }
        this.manager = manager;
        this.id = id;
    }


    @Override
    public void access(Consumer<HttpSession> sessionConsumer) {

        Session session;
        try {
            session = manager.findSession(id);
        } catch (IOException e) {
            throw new IllegalStateException(sm.getString("standardSessionAccessor.access.ioe", id));
        }

        if (session == null || !session.isValid()) {
            // Should never be null but include it here just in case
            throw new IllegalStateException(sm.getString("standardSessionAccessor.access.invalid", id));
        }

        session.access();
        try {
            sessionConsumer.accept(session.getSession());
        } finally {
            try {
                session.endAccess();
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.warn(sm.getString("standardSessionAccessor.access.end"), t);
            }
        }
    }
}
