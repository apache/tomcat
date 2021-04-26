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
package org.apache.tomcat.buildutil.translate;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Base class providing common implementation for back-port utilities.
 */
public abstract class BackportBase {

    protected final Map<String,Properties> sourceTranslations = new HashMap<>();
    protected final Map<String,Properties> targetTranslations = new HashMap<>();
    protected final File targetRoot;
    protected final Properties sourceEnglish;
    protected final Properties targetEnglish;
    protected final File storageDir;

    protected BackportBase(String... args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Missing back-port target");
        }
        targetRoot = new File(args[0]);

        if (!targetRoot.isDirectory()) {
            throw new IllegalArgumentException("Back-port target not a directory");
        }

        File sourceRoot = new File(".");
        for (String dir : Constants.SEARCH_DIRS) {
            File directory = new File(dir);
            Utils.processDirectory(sourceRoot, directory, sourceTranslations);
        }

        for (String dir : Constants.SEARCH_DIRS) {
            File directory = new File(targetRoot, dir);
            Utils.processDirectory(targetRoot, directory, targetTranslations);
        }

        sourceEnglish = sourceTranslations.get("");
        targetEnglish = targetTranslations.get("");

        storageDir = new File(targetRoot, Constants.STORAGE_DIR);
    }

    protected abstract void execute() throws IOException;
}
