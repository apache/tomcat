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

    private static final String defaultSkip;
    private static final String defaultScan;
    private static final Set<String> defaultSkipSet = new HashSet<>();
    private static final Set<String> defaultScanSet = new HashSet<>();
    private static final boolean defaultSkipAll;

    static {
        // Initialize defaults. There are no setter methods for them.
        defaultSkip = System.getProperty(Constants.SKIP_JARS_PROPERTY);
        populateSetFromAttribute(defaultSkip, defaultSkipSet);
        defaultScan = System.getProperty(Constants.SCAN_JARS_PROPERTY);
        populateSetFromAttribute(defaultScan, defaultScanSet);
        defaultSkipAll = (defaultSkipSet.contains("*") || defaultSkipSet.contains("*.jar")) && defaultScanSet.isEmpty();
    }

    private String tldSkip;
    private String tldScan;
    private final Set<String> tldSkipSet;
    private final Set<String> tldScanSet;
    private boolean defaultTldScan = true;

    private String pluggabilitySkip;
    private String pluggabilityScan;
    private final Set<String> pluggabilitySkipSet;
    private final Set<String> pluggabilityScanSet;
    private boolean defaultPluggabilityScan = true;

    /**
     * This is the standard implementation of {@link JarScanFilter}. By default,
     * the following filtering rules are used:
     * <ul>
     * <li>JARs that match neither the skip nor the scan list will be included
     *     in scan results.</li>
     * <li>JARs that match the skip list but not the scan list will be excluded
     *     from scan results.</li>
     * <li>JARs that match the scan list will be included from scan results.
     *     </li>
     * </ul>
     * The default skip list and default scan list are obtained from the system
     * properties {@link Constants#SKIP_JARS_PROPERTY} and
     * {@link Constants#SCAN_JARS_PROPERTY} respectively. These default values
     * may be over-ridden for the {@link JarScanType#TLD} and
     * {@link JarScanType#PLUGGABILITY} scans. The filtering rules may also be
     * modified for these scan types using {@link #setDefaultTldScan(boolean)}
     * and {@link #setDefaultPluggabilityScan(boolean)}. If set to
     * <code>false</code>, the following filtering rules are used for associated
     * type:
     * <ul>
     * <li>JARs that match neither the skip nor the scan list will be excluded
     *     from scan results.</li>
     * <li>JARs that match the scan list but not the skip list will be included
     *     in scan results.</li>
     * <li>JARs that match the skip list will be excluded from scan results.
     *     </li>
     * </ul>
     */
    public StandardJarScanFilter() {
        tldSkip = defaultSkip;
        tldSkipSet = new HashSet<>(defaultSkipSet);
        tldScan = defaultScan;
        tldScanSet = new HashSet<>(defaultScanSet);
        pluggabilitySkip = defaultSkip;
        pluggabilitySkipSet = new HashSet<>(defaultSkipSet);
        pluggabilityScan = defaultScan;
        pluggabilityScanSet = new HashSet<>(defaultScanSet);
    }


    public String getTldSkip() {
        return tldSkip;
    }


    public void setTldSkip(String tldSkip) {
        this.tldSkip = tldSkip;
        Lock writeLock = configurationLock.writeLock();
        writeLock.lock();
        try {
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
        writeLock.lock();
        try {
            populateSetFromAttribute(tldScan, tldScanSet);
        } finally {
            writeLock.unlock();
        }
    }


    public boolean isSkipAll() {
        return defaultSkipAll;
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
        writeLock.lock();
        try {
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
        writeLock.lock();
        try {
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
        Lock readLock = configurationLock.readLock();
        readLock.lock();
        try {
            final boolean defaultScan;
            final Set<String> toSkip;
            final Set<String> toScan;
            switch (jarScanType) {
                case TLD: {
                    defaultScan = defaultTldScan;
                    toSkip = tldSkipSet;
                    toScan = tldScanSet;
                    break;
                }
                case PLUGGABILITY: {
                    defaultScan = defaultPluggabilityScan;
                    toSkip = pluggabilitySkipSet;
                    toScan = pluggabilityScanSet;
                    break;
                }
                case OTHER:
                default: {
                    defaultScan = true;
                    toSkip = defaultSkipSet;
                    toScan = defaultScanSet;
                }
            }
            if (defaultScan) {
                if (Matcher.matchName(toSkip, jarName)) {
                    if (Matcher.matchName(toScan, jarName)) {
                        return true;
                    } else {
                        return false;
                    }
                }
                return true;
            } else {
                if (Matcher.matchName(toScan, jarName)) {
                    if (Matcher.matchName(toSkip, jarName)) {
                        return false;
                    } else {
                        return true;
                    }
                }
                return false;
            }
        } finally {
            readLock.unlock();
        }
    }

    private static void populateSetFromAttribute(String attribute, Set<String> set) {
        set.clear();
        if (attribute != null) {
            StringTokenizer tokenizer = new StringTokenizer(attribute, ",");
            while (tokenizer.hasMoreElements()) {
                String token = tokenizer.nextToken().trim();
                if (token.length() > 0) {
                    set.add(token);
                }
            }
        }
    }
}
