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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Generates a single properties file per language for import into a translation
 * tool.
 */
public class Export {

    private static final Map<String,Properties> translations = new HashMap<>();

    public static void main(String... args) {
        for (String dir : Constants.SEARCH_DIRS) {
            processRoot(dir);
        }

        outputTranslations();
    }


    private static void processRoot(String dir) {
        // Run from within IDE so working dir is root of project.
        File root = new File(dir);

        // Assumes no l18n files directly in roots
        for (File f : root.listFiles()) {
            if (f.isDirectory()) {
                processDirectory(f);
            }
        }
    }


    private static void processDirectory(File dir) {
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                processDirectory(f);
            } else if (f.isFile()) {
                processFile(f);
            }
        }
    }


    private static void processFile(File f) {
        String name = f.getName();

        // non-l10n files
        if (!name.startsWith(Constants.L10N_PREFIX)) {
            return;
        }

        // Determine language
        String language = Utils.getLanguage(name);

        String keyPrefix = getKeyPrefix(f);
        Properties props = Utils.load(f);

        // Create a Map for the language if one does not exist.
        Properties translation = translations.get(language);
        if (translation == null) {
            translation = new Properties();
            translations.put(language, translation);
        }

        // Add the properties from this file to the combined file, prefixing the
        // key with the package name to ensure uniqueness.
        for (Object obj : props.keySet()) {
            String key = (String) obj;
            String value = props.getProperty(key);

            translation.put(keyPrefix + key, value);
        }
    }


    private static String getKeyPrefix(File f) {
        File wd = new File(".");
        String prefix = f.getParentFile().getAbsolutePath();
        prefix = prefix.substring(wd.getAbsolutePath().length() - 1);
        prefix = prefix.replace(File.separatorChar, '.');
        prefix = prefix + Constants.END_PACKAGE_MARKER;
        return prefix;
    }


    private static void outputTranslations() {

        File storageDir = new File(Constants.STORAGE_DIR);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        for (Map.Entry<String,Properties> translationEntry : translations.entrySet()) {
            Properties translation = translationEntry.getValue();

            String language = translationEntry.getKey();

            File out = new File(storageDir, Constants.L10N_PREFIX + language + Constants.L10N_SUFFIX);
            try (FileOutputStream fos = new FileOutputStream(out);
                    Writer w = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                String[] keys = translation.keySet().toArray(new String[0]);
                Arrays.sort(keys);
                for (Object key : keys) {
                    w.write(key + "=" + Utils.formatValue(translation.getProperty((String) key)) + "\n");
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }
}

