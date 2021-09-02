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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.regex.Pattern;

public class Utils {

    private static final Pattern ESCAPE_LEADING_SPACE = Pattern.compile("^(\\s)", Pattern.MULTILINE);

    // Package private so it is visible to tests
    static final String PADDING = "POEDITOR_EXPORT_PADDING_DO_NOT_DELETE";

    private Utils() {
        // Utility class. Hide default constructor.
    }


    static String getLanguage(String name) {
        return name.substring(Constants.L10N_PREFIX.length(), name.length() - Constants.L10N_SUFFIX.length());
    }


    static Properties load(File f) {
        Properties props = new Properties();

        try (FileInputStream fis = new FileInputStream(f);
                Reader r = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            props.load(r);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return props;
    }


    static String formatValueImport(String in) {
        String result;

        if (in.startsWith(PADDING)) {
            result = in.substring(PADDING.length());
        } else {
            result = in;
        }

        return formatValueCommon(result);
    }


    /*
     * Common formatting to convert a String for storage as a value in a
     * property file.
     */
    static String formatValueCommon(String in) {
        String result = in.replace("\n", "\\n\\\n");
        if (result.endsWith("\\n\\\n")) {
            result = result.substring(0, result.length() - 2);
        }

        result = ESCAPE_LEADING_SPACE.matcher(result).replaceAll("\\\\$1");

        result = result.replaceAll("\t", "\\t");

        return result;
    }
}
