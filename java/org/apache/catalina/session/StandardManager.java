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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.ServletContext;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.Session;
import org.apache.catalina.util.CustomObjectInputStream;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;

/**
 * Standard implementation of the <b>Manager</b> interface that provides simple session persistence across restarts of
 * this component (such as when the entire server is shut down and restarted, or when a particular web application is
 * reloaded.
 * <p>
 * <b>IMPLEMENTATION NOTE</b>: Correct behavior of session storing and reloading depends upon external calls to the
 * <code>start()</code> and <code>stop()</code> methods of this class at the correct times.
 *
 * @author Craig R. McClanahan
 */
public class StandardManager extends ManagerBase {

    private final Log log = LogFactory.getLog(StandardManager.class); // must not be static

    // ----------------------------------------------------- Instance Variables

    /**
     * The descriptive name of this Manager implementation (for logging).
     */
    protected static final String name = "StandardManager";


    /**
     * Path name of the disk file in which active sessions are saved when we stop, and from which these sessions are
     * loaded when we start. A <code>null</code> value indicates that no persistence is desired. If this pathname is
     * relative, it will be resolved against the temporary working directory provided by our context, available via the
     * <code>jakarta.servlet.context.tempdir</code> context attribute.
     */
    protected String pathname = null;


    // ------------------------------------------------------------- Properties

    @Override
    public String getName() {
        return name;
    }


    /**
     * @return The session persistence pathname, if any.
     */
    public String getPathname() {
        return pathname;
    }


    /**
     * Set the session persistence pathname to the specified value. If no persistence support is desired, set the
     * pathname to <code>null</code>.
     *
     * @param pathname New session persistence pathname
     */
    public void setPathname(String pathname) {
        String oldPathname = this.pathname;
        this.pathname = pathname;
        support.firePropertyChange("pathname", oldPathname, this.pathname);
    }


    // --------------------------------------------------------- Public Methods

    @Override
    public void load() throws ClassNotFoundException, IOException {
        if (log.isTraceEnabled()) {
            log.trace("Start: Loading persisted sessions");
        }

        // Initialize our internal data structures
        sessions.clear();

        // Open an input stream to the specified pathname, if any
        File file = file();
        if (file == null) {
            return;
        }
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("standardManager.loading", pathname));
        }
        Loader loader = null;
        ClassLoader classLoader = null;
        Log logger = null;
        try (FileInputStream fis = new FileInputStream(file.getAbsolutePath());
                BufferedInputStream bis = new BufferedInputStream(fis)) {
            Context c = getContext();
            loader = c.getLoader();
            logger = c.getLogger();
            if (loader != null) {
                classLoader = loader.getClassLoader();
            }
            if (classLoader == null) {
                classLoader = getClass().getClassLoader();
            }

            // Load the previously unloaded active sessions
            synchronized (sessions) {
                try (ObjectInputStream ois = new CustomObjectInputStream(bis, classLoader, logger,
                        getSessionAttributeValueClassNamePattern(), getWarnOnSessionAttributeFilterFailure())) {
                    Integer count = (Integer) ois.readObject();
                    int n = count.intValue();
                    if (log.isTraceEnabled()) {
                        log.trace("Loading " + n + " persisted sessions");
                    }
                    for (int i = 0; i < n; i++) {
                        StandardSession session = getNewSession();
                        session.readObjectData(ois);
                        session.setManager(this);
                        sessions.put(session.getIdInternal(), session);
                        session.activate();
                        if (!session.isValidInternal()) {
                            // If session is already invalid,
                            // expire session to prevent memory leak.
                            session.setValid(true);
                            session.expire();
                        }
                    }
                } finally {
                    // Delete the persistent storage file
                    if (file.exists()) {
                        if (!file.delete()) {
                            log.warn(sm.getString("standardManager.deletePersistedFileFail", file));
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("standardManager.noFile", file.getAbsolutePath()));
            }
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("Finish: Loading persisted sessions");
        }
    }


    @Override
    public void unload() throws IOException {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("standardManager.unloading.debug"));
        }

        if (sessions.isEmpty()) {
            log.debug(sm.getString("standardManager.unloading.nosessions"));
            return; // nothing to do
        }

        // Open an output stream to the specified pathname, if any
        File file = file();
        if (file == null) {
            return;
        }
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("standardManager.unloading", pathname));
        }

        // Keep a note of sessions that are expired
        List<StandardSession> list = new ArrayList<>();

        try (FileOutputStream fos = new FileOutputStream(file.getAbsolutePath());
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {

            synchronized (sessions) {
                if (log.isTraceEnabled()) {
                    log.trace("Unloading " + sessions.size() + " sessions");
                }
                // Write the number of active sessions, followed by the details
                oos.writeObject(Integer.valueOf(sessions.size()));
                for (Session s : sessions.values()) {
                    StandardSession session = (StandardSession) s;
                    list.add(session);
                    session.passivate();
                    session.writeObjectData(oos);
                }
            }
        }

        // Expire all the sessions we just wrote
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("standardManager.expiringSessions", Integer.toString(list.size())));
        }
        for (StandardSession session : list) {
            try {
                session.expire(false);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
            } finally {
                session.recycle();
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("Unloading complete");
        }
    }


    /**
     * Start this component and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error that prevents this component from being
     *                                   used
     */
    @Override
    protected void startInternal() throws LifecycleException {

        super.startInternal();

        // Load unloaded sessions, if any
        try {
            load();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("standardManager.managerLoad"), t);
        }

        setState(LifecycleState.STARTING);
    }


    /**
     * Stop this component and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error that prevents this component from being
     *                                   used
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        if (log.isTraceEnabled()) {
            log.trace("Stopping");
        }

        setState(LifecycleState.STOPPING);

        // Write out sessions
        try {
            unload();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("standardManager.managerUnload"), t);
        }

        // Expire all active sessions
        Session sessions[] = findSessions();
        for (Session session : sessions) {
            try {
                if (session.isValid()) {
                    session.expire();
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
            } finally {
                // Measure against memory leaking if references to the session
                // object are kept in a shared field somewhere
                session.recycle();
            }
        }

        // Require a new random number generator if we are restarted
        super.stopInternal();
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * Return a File object representing the pathname to our persistence file, if any.
     *
     * @return the file
     */
    protected File file() {
        if (pathname == null || pathname.length() == 0) {
            return null;
        }
        File file = new File(pathname);
        if (!file.isAbsolute()) {
            Context context = getContext();
            ServletContext servletContext = context.getServletContext();
            File tempdir = (File) servletContext.getAttribute(ServletContext.TEMPDIR);
            if (tempdir != null) {
                file = new File(tempdir, pathname);
            }
        }
        return file;
    }
}
