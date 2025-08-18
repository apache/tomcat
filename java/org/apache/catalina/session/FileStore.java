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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import jakarta.servlet.ServletContext;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Session;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Concrete implementation of the <b>Store</b> interface that utilizes a file per saved Session in a configured
 * directory. Sessions that are saved are still subject to being expired based on inactivity.
 *
 * @author Craig R. McClanahan
 */
public final class FileStore extends StoreBase {

    private static final Log log = LogFactory.getLog(FileStore.class);
    private static final StringManager sm = StringManager.getManager(FileStore.class);


    // ----------------------------------------------------- Constants

    /**
     * The extension to use for serialized session filenames.
     */
    private static final String FILE_EXT = ".session";


    // ----------------------------------------------------- Instance Variables

    /**
     * The pathname of the directory in which Sessions are stored. This may be an absolute pathname, or a relative path
     * that is resolved against the temporary work directory for this application.
     */
    private String directory = ".";


    /**
     * A File representing the directory in which Sessions are stored.
     */
    private File directoryFile = null;


    /**
     * A map of potential locks to control concurrent read/writes of a session's persistence file.
     */
    private final ConcurrentHashMap<String, UsageCountingReadWriteLock> idLocks = new ConcurrentHashMap();


    /**
     * Name to register for this Store, used for logging.
     */
    private static final String storeName = "fileStore";


    /**
     * Name to register for the background thread.
     */
    private static final String threadName = "FileStore";


    // ------------------------------------------------------------- Properties

    /**
     * @return The directory path for this Store.
     */
    public String getDirectory() {
        return directory;
    }


    /**
     * Set the directory path for this Store.
     *
     * @param path The new directory path
     */
    public void setDirectory(String path) {
        String oldDirectory = this.directory;
        this.directory = path;
        this.directoryFile = null;
        support.firePropertyChange("directory", oldDirectory, this.directory);
    }


    /**
     * @return The thread name for this Store.
     */
    public String getThreadName() {
        return threadName;
    }


    @Override
    public String getStoreName() {
        return storeName;
    }


    @Override
    public int getSize() throws IOException {
        // Acquire the list of files in our storage directory
        File dir = directory();
        if (dir == null) {
            return 0;
        }
        String[] files = dir.list();

        // Figure out which files are sessions
        int keycount = 0;
        if (files != null) {
            for (String file : files) {
                if (file.endsWith(FILE_EXT)) {
                    keycount++;
                }
            }
        }
        return keycount;
    }


    // --------------------------------------------------------- Public Methods

    @Override
    public void clear() throws IOException {
        String[] keys = keys();
        for (String key : keys) {
            remove(key);
        }
    }


    @Override
    public String[] keys() throws IOException {
        // Acquire the list of files in our storage directory
        File dir = directory();
        if (dir == null) {
            return new String[0];
        }
        String[] files = dir.list();

        // Bugzilla 32130
        if (files == null || files.length < 1) {
            return new String[0];
        }

        // Build and return the list of session identifiers
        List<String> list = new ArrayList<>();
        int n = FILE_EXT.length();
        for (String file : files) {
            if (file.endsWith(FILE_EXT)) {
                list.add(file.substring(0, file.length() - n));
            }
        }
        return list.toArray(new String[0]);
    }


    @Override
    public Session load(String id) throws ClassNotFoundException, IOException {
        // Open an input stream to the specified pathname, if any
        File file = file(id);
        if (file == null) {
            return null;
        }

        Context context = getManager().getContext();
        Log contextLog = context.getLogger();

        if (contextLog.isTraceEnabled()) {
            contextLog.trace(sm.getString(getStoreName() + ".loading", id, file.getAbsolutePath()));
        }

        ClassLoader oldThreadContextCL = context.bind(Globals.IS_SECURITY_ENABLED, null);

        try {
            acquireIdReadLock(id);
            if (!file.exists()) {
                return null;
            }

            try (FileInputStream fis = new FileInputStream(file.getAbsolutePath());
                    ObjectInputStream ois = getObjectInputStream(fis)) {
                StandardSession session = (StandardSession) manager.createEmptySession();
                session.readObjectData(ois);
                session.setManager(manager);
                return session;
            } catch (FileNotFoundException e) {
                if (contextLog.isDebugEnabled()) {
                    contextLog.debug(sm.getString("fileStore.noFile", id, file.getAbsolutePath()));
                }
                return null;
            }
        } finally {
            releaseIdReadLock(id);
            context.unbind(Globals.IS_SECURITY_ENABLED, oldThreadContextCL);
        }
    }


    @Override
    public void remove(String id) throws IOException {
        File file = file(id);
        if (file == null) {
            return;
        }
        if (manager.getContext().getLogger().isTraceEnabled()) {
            manager.getContext().getLogger()
                    .trace(sm.getString(getStoreName() + ".removing", id, file.getAbsolutePath()));
        }

        try{
            acquireIdWriteLock(id);
            if (file.exists() && !file.delete()) {
                throw new IOException(sm.getString("fileStore.deleteSessionFailed", file));
            }
        } finally {
            releaseIdWriteLock(id);
        }
    }


    @Override
    public void save(Session session) throws IOException {
        // Open an output stream to the specified pathname, if any
        File file = file(session.getIdInternal());
        if (file == null) {
            return;
        }
        if (manager.getContext().getLogger().isTraceEnabled()) {
            manager.getContext().getLogger()
                    .trace(sm.getString(getStoreName() + ".saving", session.getIdInternal(), file.getAbsolutePath()));
        }

        try {
            acquireIdWriteLock(session.getIdInternal());
            try (FileOutputStream fos = new FileOutputStream(file.getAbsolutePath());
                    ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(fos))) {
                ((StandardSession) session).writeObjectData(oos);
            }
        } finally {
            releaseIdWriteLock(session.getIdInternal());
        }
    }


    // -------------------------------------------------------- Private Methods

    /**
     * Return a File object representing the pathname to our session persistence directory, if any. The directory will
     * be created if it does not already exist.
     */
    private File directory() throws IOException {
        if (this.directory == null) {
            return null;
        }
        if (this.directoryFile != null) {
            // NOTE: Race condition is harmless, so do not synchronize
            return this.directoryFile;
        }
        File file = new File(this.directory);
        if (!file.isAbsolute()) {
            Context context = manager.getContext();
            ServletContext servletContext = context.getServletContext();
            File work = (File) servletContext.getAttribute(ServletContext.TEMPDIR);
            file = new File(work, this.directory);
        }
        if (!file.exists() || !file.isDirectory()) {
            if (!file.delete() && file.exists()) {
                throw new IOException(sm.getString("fileStore.deleteFailed", file));
            }
            if (!file.mkdirs() && !file.isDirectory()) {
                throw new IOException(sm.getString("fileStore.createFailed", file));
            }
        }
        this.directoryFile = file;
        return file;
    }


    /**
     * Return a File object representing the pathname to our session persistence file, if any.
     *
     * @param id The ID of the Session to be retrieved. This is used in the file naming.
     */
    private File file(String id) throws IOException {
        File storageDir = directory();
        if (storageDir == null) {
            return null;
        }

        String filename = id + FILE_EXT;
        File file = new File(storageDir, filename);
        File canonicalFile = file.getCanonicalFile();

        // Check the file is within the storage directory
        if (!canonicalFile.toPath().startsWith(storageDir.getCanonicalFile().toPath())) {
            log.warn(sm.getString("fileStore.invalid", file.getPath(), id));
            return null;
        }

        return canonicalFile;
    }


    /**
     * Acquire and create if necessary a readlock for a given session id.
     *
     * @param id The ID of the Session.
     */
    private void acquireIdReadLock(String id) {
        idLocks.compute(id,
                (k, v) -> v == null ? new UsageCountingReadWriteLock() : v).lockRead();
    }


    /**
     * Release a readlock for a given session id.
     *
     * @param id The ID of the Session.
     */
    private void releaseIdReadLock(String id) {
        idLocks.computeIfPresent(id,
                (k, v) -> v.releaseRead() == 0 ? null : v);
    }


    /**
     * Acquire and create if necessary a writelock for a given session id.
     *
     * @param id The ID of the Session.
     */
    private void acquireIdWriteLock(String id) {
        idLocks.compute(id,
                (k, v) -> v == null ? new UsageCountingReadWriteLock() : v).lockWrite();
    }


    /**
     * Release a writelock for a given session id.
     *
     * @param id The ID of the Session.
     */
    private void releaseIdWriteLock(String id) {
        idLocks.computeIfPresent(id,
                (k, v) -> v.releaseWrite() == 0 ? null : v);
    }


    /*
     * The FileStore uses a per session ReentrantReadWriteLock to ensure that only one write (from a remove or save)
     * occurs to a session persistence file at a time and not during any reads of the file (from a load call).  This
     * is to protect from concurrency issues that may arise, particularly if using a PersistentValve. To limit the
     * size of the session ID to lock map, the locks are created when required and destroyed (made eligible for GC)
     * as soon as they are not required.
     */
    private static class UsageCountingReadWriteLock {
        private final AtomicLong usageCount = new AtomicLong(0);
        private final ReentrantReadWriteLock lock;

        private UsageCountingReadWriteLock() {
            lock = new ReentrantReadWriteLock();
        }

        private void lockRead() {
            usageCount.incrementAndGet();
            lock.readLock().lock();
        }

        private long releaseRead() {
            lock.readLock().unlock();
            return usageCount.decrementAndGet();
        }

        private void lockWrite() {
            usageCount.incrementAndGet();
            lock.writeLock().lock();
        }

        private long releaseWrite() {
            lock.writeLock().unlock();
            return usageCount.decrementAndGet();
        }
    }
}
