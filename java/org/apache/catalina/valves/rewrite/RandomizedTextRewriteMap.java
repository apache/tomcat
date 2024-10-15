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

package org.apache.catalina.valves.rewrite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.apache.tomcat.util.file.ConfigFileLoader;
import org.apache.tomcat.util.file.ConfigurationSource.Resource;
import org.apache.tomcat.util.res.StringManager;

/**
 * Implement a map for the txt: and rnd: mod_rewrite capabilities.
 */
public class RandomizedTextRewriteMap implements RewriteMap {

    protected static final StringManager sm = StringManager.getManager(RandomizedTextRewriteMap.class);

    private static final Random random = new Random();
    private final Map<String,String[]> map = new HashMap<>();

    /**
     * Create a map from a text file according to the mod_rewrite syntax.
     *
     * @param txtFilePath the text file path
     * @param useRandom   if the map should produce random results
     */
    public RandomizedTextRewriteMap(String txtFilePath, boolean useRandom) {
        String line;
        try (Resource txtResource = ConfigFileLoader.getSource().getResource(txtFilePath);
                BufferedReader reader = new BufferedReader(new InputStreamReader(txtResource.getInputStream()))) {
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.isEmpty()) {
                    // Ignore comment or empty lines
                    continue;
                }
                String[] keyValuePair = line.split(" ", 2);
                if (keyValuePair.length > 1) {
                    String key = keyValuePair[0];
                    String value = keyValuePair[1];
                    String[] possibleValues = null;
                    if (useRandom && value.contains("|")) {
                        possibleValues = value.split("\\|");
                    } else {
                        possibleValues = new String[1];
                        possibleValues[0] = value;
                    }
                    map.put(key, possibleValues);
                } else {
                    throw new IllegalArgumentException(sm.getString("rewriteMap.txtInvalidLine", line, txtFilePath));
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(sm.getString("rewriteMap.txtReadError", txtFilePath), e);
        }
    }

    @Override
    public String setParameters(String params) {
        throw new IllegalArgumentException(
                StringManager.getManager(RewriteMap.class).getString("rewriteMap.tooManyParameters"));
    }

    @Override
    public String lookup(String key) {
        String[] possibleValues = map.get(key);
        if (possibleValues != null) {
            if (possibleValues.length > 1) {
                return possibleValues[random.nextInt(possibleValues.length)];
            } else {
                return possibleValues[0];
            }
        }
        return null;
    }
}
