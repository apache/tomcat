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
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

/**
 * Ant task that submits a file to the Symantec code-signing service.
 */
public class SignCode extends Task {

    private final List<FileSet> filesets = new ArrayList<>();


    public void addFileset(FileSet fileset) {
        filesets.add(fileset);
    }


    @Override
    public void execute() throws BuildException {

        List<File> filesToSign = new ArrayList<>();

        // Process the filesets and populate the list of files that need to be
        // signed.
        for (FileSet fileset : filesets) {
            DirectoryScanner ds = fileset.getDirectoryScanner(getProject());
            File basedir = ds.getBasedir();
            String[] files = ds.getIncludedFiles();
            if (files.length > 0) {
                for (int i = 0; i < files.length; i++) {
                    File file = new File(basedir, files[i]);
                    filesToSign.add(file);
                    log("TODO: Sign " + file.getAbsolutePath());
                }
            }
        }
    }
}
