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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.Timestamp;

import org.apache.tomcat.util.res.StringManager;

/**
 * Move server.xml or context.xml as backup
 *
 * TODO Get Encoding from Registry
 */
public class StoreFileMover {

    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    private String filename = "conf/server.xml";

    private String encoding = "UTF-8";

    private String basename = System.getProperty("catalina.base");

    private File configOld;

    private File configNew;

    private File configSave;

    /**
     * @return Returns the configNew.
     */
    public File getConfigNew() {
        return configNew;
    }

    /**
     * @return Returns the configOld.
     */
    public File getConfigOld() {
        return configOld;
    }

    /**
     * @return Returns the configSave.
     */
    public File getConfigSave() {
        return configSave;
    }

    /**
     * @return Returns the basename.
     */
    public String getBasename() {
        return basename;
    }

    /**
     * @param basename
     *            The basename to set.
     */
    public void setBasename(String basename) {
        this.basename = basename;
    }

    /**
     * @return The file name
     */
    public String getFilename() {
        return filename;
    }

    /**
     * @param string The file name
     */
    public void setFilename(String string) {
        filename = string;
    }

    /**
     * @return The encoding
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * @param string The encoding
     */
    public void setEncoding(String string) {
        encoding = string;
    }

    /**
     * Calculate file objects for the old and new configuration files.
     * @param basename The base path
     * @param encoding The encoding of the file
     * @param filename The file name
     */
    public StoreFileMover(String basename, String filename, String encoding) {
        setBasename(basename);
        setEncoding(encoding);
        setFilename(filename);
        init();
    }

    /**
     * Calculate file objects for the old and new configuration files.
     */
    public StoreFileMover() {
        init();
    }

    /**
     * Generate the Filename to new with TimeStamp.
     */
    public void init() {
        String configFile = getFilename();
        configOld = new File(configFile);
        if (!configOld.isAbsolute()) {
            configOld = new File(getBasename(), configFile);
        }
        configNew = new File(configFile + ".new");
        if (!configNew.isAbsolute()) {
            configNew = new File(getBasename(), configFile + ".new");
        }
        if (!configNew.getParentFile().exists()) {
            if (!configNew.getParentFile().mkdirs()) {
                throw new IllegalStateException(sm.getString("storeFileMover.directoryCreationError", configNew));
            }
        }
        String sb = getTimeTag();
        configSave = new File(configFile + sb);
        if (!configSave.isAbsolute()) {
            configSave = new File(getBasename(), configFile + sb);
        }
    }

    /**
     * Shuffle old-&gt;save and new-&gt;old.
     *
     * @throws IOException a file operation error occurred
     */
    public void move() throws IOException {
        if (configOld.renameTo(configSave)) {
            if (!configNew.renameTo(configOld)) {
                configSave.renameTo(configOld);
                throw new IOException(sm.getString("storeFileMover.renameError",
                        configNew.getAbsolutePath(), configOld.getAbsolutePath()));
            }
        } else {
            if (!configOld.exists()) {
                if (!configNew.renameTo(configOld)) {
                    throw new IOException(sm.getString("storeFileMover.renameError",
                            configNew.getAbsolutePath(), configOld.getAbsolutePath()));
                }
            } else {
                throw new IOException(sm.getString("storeFileMover.renameError",
                        configOld.getAbsolutePath(), configSave.getAbsolutePath()));
            }
        }
    }

    /**
     * Open an output writer for the new configuration file.
     *
     * @return The writer
     * @throws IOException Failed opening a writer to the new file
     */
    public PrintWriter getWriter() throws IOException {
        return new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(configNew), getEncoding()));
    }

    /**
     * Time value for backup yyyy-mm-dd.hh-mm-ss.
     *
     * @return The time
     */
    protected String getTimeTag() {
        String ts = (new Timestamp(System.currentTimeMillis())).toString();
        //        yyyy-mm-dd hh:mm:ss
        //        0123456789012345678
        StringBuffer sb = new StringBuffer(".");
        sb.append(ts.substring(0, 10));
        sb.append('.');
        sb.append(ts.substring(11, 13));
        sb.append('-');
        sb.append(ts.substring(14, 16));
        sb.append('-');
        sb.append(ts.substring(17, 19));
        return sb.toString();
    }

}
