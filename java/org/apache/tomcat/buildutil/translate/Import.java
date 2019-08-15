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

        // Unlike the master branch, don't skip the original so we can import
        // updates to the English translations
        Properties props = Utils.load(f);
        Object[] objKeys = props.keySet().toArray();
        Arrays.sort(objKeys);

        String currentPkg = null;
        Writer w = null;
        String currentGroup = "zzz";

        for (Object objKey : objKeys) {
            String key = (String) objKey;
            CompositeKey cKey = new CompositeKey(key);

            if (!cKey.pkg.equals(currentPkg)) {
                currentPkg = cKey.pkg;
                if (w != null) {
                    w.close();
                }
                File outFile = new File(currentPkg.replace('.', File.separatorChar), Constants.L10N_PREFIX + language + Constants.L10N_SUFFIX);
                FileOutputStream fos = new FileOutputStream(outFile);
                w = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                insertLicense(w);
            }

            if (!currentGroup.equals(cKey.group)) {
                currentGroup = cKey.group;
                w.write(System.lineSeparator());
            }

            w.write(cKey.key + "=" + Utils.formatValue(props.getProperty(key)));
            w.write(System.lineSeparator());
        }
        if (w != null) {
            w.close();
        }
    }


    private static void insertLicense(Writer w) throws IOException {
        w.write("# Licensed to the Apache Software Foundation (ASF) under one or more");
        w.write(System.lineSeparator());
        w.write("# contributor license agreements.  See the NOTICE file distributed with");
        w.write(System.lineSeparator());
        w.write("# this work for additional information regarding copyright ownership.");
        w.write(System.lineSeparator());
        w.write("# The ASF licenses this file to You under the Apache License, Version 2.0");
        w.write(System.lineSeparator());
        w.write("# (the \"License\"); you may not use this file except in compliance with");
        w.write(System.lineSeparator());
        w.write("# the License.  You may obtain a copy of the License at");
        w.write(System.lineSeparator());
        w.write("#");
        w.write(System.lineSeparator());
        w.write("#     http://www.apache.org/licenses/LICENSE-2.0");
        w.write(System.lineSeparator());
        w.write("#");
        w.write(System.lineSeparator());
        w.write("# Unless required by applicable law or agreed to in writing, software");
        w.write(System.lineSeparator());
        w.write("# distributed under the License is distributed on an \"AS IS\" BASIS,");
        w.write(System.lineSeparator());
        w.write("# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.");
        w.write(System.lineSeparator());
        w.write("# See the License for the specific language governing permissions and");
        w.write(System.lineSeparator());
        w.write("# limitations under the License.");
        w.write(System.lineSeparator());
    }
    private static class CompositeKey {

        public final String pkg;
        public final String key;
        public final String group;

        public CompositeKey(String in) {
            int posPkg = in.indexOf(Constants.END_PACKAGE_MARKER);
            pkg = in.substring(0, posPkg);
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
