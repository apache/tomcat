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
import java.util.Properties;

public class Import {

    public static void main(String... args) throws IOException {
        File root = new File(Constants.STORAGE_DIR);

        for (File f : root.listFiles()) {
            // Not robust but good enough
            if (f.isFile() && f.getName().startsWith(Constants.L10N_PREFIX)) {
                processFile(f);
            }
        }
    }


    @SuppressWarnings("null")
    private static void processFile(File f) throws IOException {
        String language = Utils.getLanguage(f.getName());

        // Skip the original
        if (language.length() == 0) {
            // Comment this line out if the originals need to be imported.
            return;
        }

        Properties props = Utils.load(f);
        Object[] objKeys = props.keySet().toArray();
        Arrays.sort(objKeys);

        String currentPkg = null;
        Writer w = null;
        String currentGroup = "zzz";

        for (Object objKey : objKeys) {
            String key = (String) objKey;
            String value = props.getProperty(key);
            // Skip untranslated values
            if (value.trim().length() == 0) {
                continue;
            }
            CompositeKey cKey = new CompositeKey(key);

            if (!cKey.pkg.equals(currentPkg)) {
                currentPkg = cKey.pkg;
                if (w != null) {
                    w.close();
                }
                File outFile = new File(currentPkg.replace('.', File.separatorChar), Constants.L10N_PREFIX + language + Constants.L10N_SUFFIX);
                FileOutputStream fos = new FileOutputStream(outFile);
                w = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                org.apache.tomcat.buildutil.Utils.insertLicense(w);
            }

            if (!currentGroup.equals(cKey.group)) {
                currentGroup = cKey.group;
                w.write(System.lineSeparator());
            }

            value = Utils.formatValueImport(value);
            value = Utils.fixUnnecessaryEscaping(cKey.key, value);

            w.write(cKey.key + "=" + value);
            w.write(System.lineSeparator());
        }
        if (w != null) {
            w.close();
        }
    }


    private static class CompositeKey {

        public final String pkg;
        public final String key;
        public final String group;

        public CompositeKey(String in) {
            int posPkg = in.indexOf(Constants.END_PACKAGE_MARKER);
            pkg = in.substring(0, posPkg).replace(Constants.JAVA_EE_SUBSTRING, Constants.JAKARTA_EE_SUBSTRING);
            key = in.substring(posPkg + Constants.END_PACKAGE_MARKER.length());
            int posGroup = key.indexOf('.');
            if (posGroup == -1) {
                group = "";
            } else {
                group = key.substring(0, posGroup);
            }
        }
    }
}
