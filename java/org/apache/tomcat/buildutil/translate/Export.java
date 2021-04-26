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
 * Generates a single properties file per language for import into a translation
 * tool.
 */
public class Export {

    private static final Map<String,Properties> translations = new HashMap<>();

    public static void main(String... args) throws IOException {
        File root = new File(".");
        for (String dir : Constants.SEARCH_DIRS) {
            File directory = new File(dir);
            Utils.processDirectory(root, directory, translations);
        }

        outputTranslations();
    }


    private static void outputTranslations() {

        File storageDir = new File(Constants.STORAGE_DIR);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        for (Map.Entry<String,Properties> translationEntry : translations.entrySet()) {
             Utils.export(translationEntry.getKey(), translationEntry.getValue(), storageDir);
        }
    }
}

