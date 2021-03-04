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
package org.apache.catalina.ha.deploy;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;
import org.apache.catalina.startup.HostConfig;

/**
 * The <b>BundleWatcher </b> watches the deployDir for changes made to the
 * directory (adding new Bundle files-&gt;deploy or remove Bundle files-&gt;undeploy)
 * and notifies a listener of the changes made.
 *
 * @author Peter Rossbach
 * @version 1.1
 */
public class BundleWatcher {

    /*--Static Variables----------------------------------------*/
    private static final Log log = LogFactory.getLog(BundleWatcher.class);
    private static final StringManager sm = StringManager.getManager(BundleWatcher.class);

    /*--Instance Variables--------------------------------------*/
    /**
     * Directory to watch for bundle files
     */
    protected final File watchDir;

    /**
     * Parent to be notified of changes
     */
    protected final FileChangeListener listener;

    /**
     * Currently deployed files
     */
    protected final Map<String, BundleInfo> currentStatus = new HashMap<>();

    /*--Constructor---------------------------------------------*/

    public BundleWatcher(FileChangeListener listener, File watchDir) {
        this.listener = listener;
        this.watchDir = watchDir;
    }

    /*--Logic---------------------------------------------------*/

    /**
     * check for modification and send notification to listener
     */
    public void check() {
        if (log.isDebugEnabled())
            log.debug(sm.getString("bundleWatcher.checkingBundles", watchDir));
        File[] list = watchDir.listFiles(new BundleFilter());
        if (list == null) {
            log.warn(sm.getString("bundleWatcher.cantListWatchDir",
                                  watchDir));

            list = new File[0];
        }
        //first make sure all the files are listed in our current status
        for (File file : list) {
            if (!file.exists())
                log.warn(sm.getString("bundleWatcher.listedFileDoesNotExist",
                        file, watchDir));

            addBundleInfo(file);
        }

        // Check all the status codes and update the FarmDeployer
        for (Iterator<Map.Entry<String, BundleInfo>> i =
                currentStatus.entrySet().iterator(); i.hasNext();) {
            Map.Entry<String,BundleInfo> entry = i.next();
            BundleInfo info = entry.getValue();
            if(log.isTraceEnabled())
                log.trace(sm.getString("bundleWatcher.checkingBundle",
                                       info.getBundle()));
            int check = info.check();
            if (check == 1) {
                listener.fileModified(info.getBundle());
            } else if (check == -1) {
                listener.fileRemoved(info.getBundle());
                //no need to keep in memory
                i.remove();
            }
            if(log.isTraceEnabled())
                log.trace(sm.getString("bundleWatcher.checkBundleResult",
                                       Integer.valueOf(check),
                                       info.getBundle()));
        }

    }

    /**
     * add cluster bundle to the watcher state
     * @param bundlefile The Bundle to add
     */
    protected void addBundleInfo(File bundlefile) {
        BundleInfo info = currentStatus.get(bundlefile.getAbsolutePath());
        if (info == null) {
            info = new BundleInfo(bundlefile);
            info.setLastState(-1); //assume file is non existent
            currentStatus.put(bundlefile.getAbsolutePath(), info);
        }
    }

    /**
     * clear watcher state
     */
    public void clear() {
        currentStatus.clear();
    }


    /*--Inner classes-------------------------------------------*/

    /**
     * File name filter for bundle files
     */
    protected static class BundleFilter implements java.io.FilenameFilter {
        @Override
        public boolean accept(File path, String name) {
            if (name == null)
                return false;
            return HostConfig.isValidExtension(name);
        }
    }

    /**
     * File information on existing Bundle files
     */
    protected static class BundleInfo {
        protected final File bundle;

        protected long lastChecked = 0;

        protected long lastState = 0;

        public BundleInfo(File bundle) {
            this.bundle = bundle;
            this.lastChecked = bundle.lastModified();
            if (!bundle.exists())
                lastState = -1;
        }

        public boolean modified() {
            return bundle.exists() && bundle.lastModified() > lastChecked;
        }

        public boolean exists() {
            return bundle.exists();
        }

        /**
         * Returns 1 if the file has been added/modified, 0 if the file is
         * unchanged and -1 if the file has been removed
         *
         * @return int 1=file added; 0=unchanged; -1=file removed
         */
        public int check() {
            //file unchanged by default
            int result = 0;

            if (modified()) {
                //file has changed - timestamp
                result = 1;
                lastState = result;
            } else if ((!exists()) && (!(lastState == -1))) {
                //file was removed
                result = -1;
                lastState = result;
            } else if ((lastState == -1) && exists()) {
                //file was added
                result = 1;
                lastState = result;
            }
            this.lastChecked = System.currentTimeMillis();
            return result;
        }

        public File getBundle() {
            return bundle;
        }

        @Override
        public int hashCode() {
            return bundle.getAbsolutePath().hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof BundleInfo) {
                BundleInfo wo = (BundleInfo) other;
                return wo.getBundle().equals(getBundle());
            } else {
                return false;
            }
        }

        protected void setLastState(int lastState) {
            this.lastState = lastState;
        }

    }

}