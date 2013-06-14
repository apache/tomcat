/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.scan;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.tomcat.JarScanFilter;
import org.apache.tomcat.JarScanType;
import org.apache.tomcat.util.file.Matcher;

public class StandardJarScanFilter implements JarScanFilter {

    private final ReadWriteLock configurationLock =
            new ReentrantReadWriteLock();

    private String defaultSkip;
    private String defaultScan;
    private Set<String[]> defaultSkipSet = new HashSet<>();
    private Set<String[]> defaultScanSet = new HashSet<>();

    private String tldSkip;
    private String tldScan;
    private Set<String[]> tldSkipSet = new HashSet<>();
    private Set<String[]> tldScanSet = new HashSet<>();
    private boolean defaultTldScan = true;

    private String pluggabilitySkip;
    private String pluggabilityScan;
    private Set<String[]> pluggabilitySkipSet = new HashSet<>();
    private Set<String[]> pluggabilityScanSet = new HashSet<>();
    private boolean defaultPluggabilityScan = true;

    public StandardJarScanFilter() {
        // Set the defaults from the system properties
        defaultSkip = System.getProperty(Constants.SKIP_JARS_PROPERTY);
        populateSetFromAttribute(defaultSkip, defaultSkipSet);
        defaultScan = System.getProperty(Constants.SCAN_JARS_PROPERTY);
        populateSetFromAttribute(defaultScan, defaultScanSet);
        tldSkip = System.getProperty(Constants.SKIP_JARS_PROPERTY);
        populateSetFromAttribute(tldSkip, tldSkipSet);
        tldScan = System.getProperty(Constants.SCAN_JARS_PROPERTY);
        populateSetFromAttribute(tldScan, tldScanSet);
        pluggabilitySkip = System.getProperty(Constants.SKIP_JARS_PROPERTY);
        populateSetFromAttribute(pluggabilitySkip, pluggabilitySkipSet);
        pluggabilityScan = System.getProperty(Constants.SCAN_JARS_PROPERTY);
        populateSetFromAttribute(pluggabilityScan, pluggabilityScanSet);
    }


    public String getTldSkip() {
        return tldSkip;
    }


    public void setTldSkip(String tldSkip) {
        this.tldSkip = tldSkip;
        Lock writeLock = configurationLock.writeLock();
        try {
            writeLock.lock();
            populateSetFromAttribute(tldSkip, tldSkipSet);
        } finally {
            writeLock.unlock();
        }
    }


    public String getTldScan() {
        return tldScan;
    }


    public void setTldScan(String tldScan) {
        this.tldScan = tldScan;
        Lock writeLock = configurationLock.writeLock();
        try {
            writeLock.lock();
            populateSetFromAttribute(tldScan, tldScanSet);
        } finally {
            writeLock.unlock();
        }
    }


    public boolean isDefaultTldScan() {
        return defaultTldScan;
    }


    public void setDefaultTldScan(boolean defaultTldScan) {
        this.defaultTldScan = defaultTldScan;
    }


    public String getPluggabilitySkip() {
        return pluggabilitySkip;
    }


    public void setPluggabilitySkip(String pluggabilitySkip) {
        this.pluggabilitySkip = pluggabilitySkip;
        Lock writeLock = configurationLock.writeLock();
        try {
            writeLock.lock();
            populateSetFromAttribute(pluggabilitySkip, pluggabilitySkipSet);
        } finally {
            writeLock.unlock();
        }
    }


    public String getPluggabilityScan() {
        return pluggabilityScan;
    }


    public void setPluggabilityScan(String pluggabilityScan) {
        this.pluggabilityScan = pluggabilityScan;
        Lock writeLock = configurationLock.writeLock();
        try {
            writeLock.lock();
            populateSetFromAttribute(pluggabilityScan, pluggabilityScanSet);
        } finally {
            writeLock.unlock();
        }
    }


    public boolean isDefaultPluggabilityScan() {
        return defaultPluggabilityScan;
    }


    public void setDefaultPluggabilityScan(boolean defaultPluggabilityScan) {
        this.defaultPluggabilityScan = defaultPluggabilityScan;
    }


    @Override
    public boolean check(JarScanType jarScanType, String jarName) {
        boolean defaultScan;
        Set<String[]> toSkip = new HashSet<>();
        Set<String[]> toScan = new HashSet<>();

        Lock readLock = configurationLock.readLock();
        try  {
            readLock.lock();
            switch (jarScanType) {
                case TLD: {
                    defaultScan = defaultTldScan;
                    toSkip.addAll(tldSkipSet);
                    toScan.addAll(tldScanSet);
                    break;
                }
                case PLUGGABILITY: {
                    defaultScan = defaultPluggabilityScan;
                    toSkip.addAll(pluggabilitySkipSet);
                    toScan.addAll(pluggabilityScanSet);
                    break;
                }
                case OTHER:
                default: {
                    defaultScan = true;
                    toSkip.addAll(defaultSkipSet);
                    toScan.addAll(defaultScanSet);
                }
            }
        } finally {
            readLock.unlock();
        }

        if (defaultScan) {
            if (Matcher.matchPath(toSkip, jarName)) {
                if (Matcher.matchPath(toScan, jarName)) {
                    return true;
                } else {
                    return false;
                }
            }
            return true;
        } else {
            if (Matcher.matchPath(toScan, jarName)) {
                if (Matcher.matchPath(toSkip, jarName)) {
                    return false;
                } else {
                    return true;
                }
            }
            return false;
        }
    }

    private void populateSetFromAttribute(String attribute, Set<String[]> set) {
        set.clear();
        if (attribute != null) {
            StringTokenizer tokenizer = new StringTokenizer(attribute, ",");
            while (tokenizer.hasMoreElements()) {
                set.add(Matcher.tokenizePathAsArray(tokenizer.nextToken()));
            }
        }
    }
}
