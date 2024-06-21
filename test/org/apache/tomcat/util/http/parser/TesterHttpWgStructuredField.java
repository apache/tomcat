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
package org.apache.tomcat.util.http.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.json.JSONParser;

/*
 * Not run automatically (due to name) as it requires a local git clone of
 * https://github.com/httpwg/structured-field-tests
 */
public class TesterHttpWgStructuredField {

    private static final String testsPath = System.getProperty("user.home") + "/repos/httpwg-sf-tests";


    @Test
    public void test() throws Exception {
        File testDir = new File(testsPath);
        doTestDirectory(testDir);
    }


    private void doTestDirectory(File directory) throws Exception {
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                if (!file.getName().equals("serialisation-tests")) {
                    doTestDirectory(file);
                }
            } else if (file.isFile()) {
                if (file.getName().endsWith(".json")) {
                    doTestFile(file);
                }
            }
        }
    }


    private void doTestFile(File file) throws Exception {
        System.out.println(file.getAbsolutePath());

        try (FileInputStream fis = new FileInputStream(file)) {
            JSONParser parser = new JSONParser(fis);
            List<Object> array = parser.parseArray();
            for (Object obj : array) {
                if (obj instanceof Map) {
                    doTestMap((Map<?,?>) obj);
                } else {
                    Assert.fail();
                }
            }
        }
    }


    private void doTestMap(Map<?,?> map) throws Exception {
        String name = (String) map.get("name");
        @SuppressWarnings("unchecked")
        List<String> rawLines = (List<String>) map.get("raw");
        String headerType = (String) map.get("header_type");
        Boolean mustFail = ((Boolean) map.get("must_fail"));
        if (mustFail == null) {
            mustFail = Boolean.FALSE;
        }
        Boolean canFail = ((Boolean) map.get("can_fail"));
        if (canFail == null) {
            canFail = Boolean.FALSE;
        }
        String raw = StringUtils.join(rawLines);
        /*
         * The simple JSON parser may not be handling escape sequences
         * correctly.
         */
        String unescaped = raw.replace("\\\"", "\"");
        unescaped = unescaped.replace("\\b", "\u0008");
        unescaped = unescaped.replace("\\t", "\t");
        unescaped = unescaped.replace("\\n", "\n");
        unescaped = unescaped.replace("\\f", "\u000c");
        unescaped = unescaped.replace("\\r", "\r");
        unescaped = unescaped.replace("\\\\", "\\");
        Reader input = new StringReader(unescaped);

        try {
            switch (headerType) {
            case "item": {
                StructuredField.parseSfItem(input);
                break;
            }
            case "list": {
                StructuredField.parseSfList(input);
                break;
            }
            case "dictionary": {
                StructuredField.parseSfDictionary(input);
                break;
            }
            default:
                System.out.println("Type unsupported " + headerType);
            }
        } catch (Exception e) {
            Assert.assertTrue(name + ": raw [" + unescaped + "]", mustFail.booleanValue() || canFail.booleanValue());
            return;
        }
        Assert.assertFalse(name + ": raw [" + unescaped + "]", mustFail.booleanValue());
    }
}
