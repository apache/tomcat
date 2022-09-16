/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.tomcat.buildutil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

/**
 * Ant task to assist with repeatable builds.
 * <p>
 * While originally written to address an issue with Javadoc output, this task
 * takes a generic approach that could be used with any archive. The task takes
 * a set of zip (or jar, war etc) files as its input and sets the last modified
 * time of every file in the archive to be the same as the last modified time
 * of the archive.
 */
public class RepeatableArchive extends Task {

    private final List<FileSet> filesets = new ArrayList<>();

    private String datetime;
    private String pattern;

    /**
     * Sets the files to be processed
     *
     * @param fs The fileset to be processed.
     */
    public void addFileset(FileSet fs) {
        filesets.add(fs);
    }


    public void setDatetime(String datetime) {
        this.datetime = datetime;
    }


    public void setPattern(String pattern) {
        this.pattern = pattern;
    }


    @Override
    public void execute() throws BuildException {

        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        Date date;
        try {
            date = sdf.parse(datetime);
        } catch (ParseException e) {
            throw new BuildException(e);
        }

        byte[] buf = new byte[8192];
        FileTime lastModified = FileTime.fromMillis(date.getTime());

        for (FileSet fs : filesets) {
            DirectoryScanner ds = fs.getDirectoryScanner(getProject());
            File basedir = ds.getBasedir();
            String[] files = ds.getIncludedFiles();
            for (String file : files) {
                File archive = new File(basedir, file);
                File oldArchive = new File(basedir, file + ".old");

                try {
                    Files.move(archive.toPath(), oldArchive.toPath(), StandardCopyOption.ATOMIC_MOVE);

                    try (ZipFile oldZipFile = new ZipFile(oldArchive);
                            ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(archive))) {

                        Enumeration<? extends ZipEntry> oldEntries = oldZipFile.entries();
                        while (oldEntries.hasMoreElements()) {
                            ZipEntry oldEntry = oldEntries.nextElement();

                            ZipEntry entry = new ZipEntry(oldEntry.getName());
                            entry.setLastModifiedTime(lastModified);

                            zipOut.putNextEntry(entry);

                            InputStream is = oldZipFile.getInputStream(oldEntry);

                            int numRead;
                            while ((numRead = is.read(buf)) >= 0) {
                                zipOut.write(buf, 0, numRead);
                            }
                        }
                    }

                    if (!archive.setLastModified(lastModified.toMillis())) {
                        throw new BuildException("setLastModified failed for [" + archive.getAbsolutePath() + "]");
                    }
                    Files.delete(oldArchive.toPath());
                } catch (IOException ioe) {
                    throw new BuildException(ioe);
                }
            }
        }
    }
}
