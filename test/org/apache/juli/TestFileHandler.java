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

package org.apache.juli;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestFileHandler {

    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    private static final String PREFIX_1 = "localhost.";
    private static final String PREFIX_2 = "test.";
    private static final String PREFIX_3 = "";
    private static final String SUFIX_1 = ".log";
    private static final String SUFIX_2 = ".txt";

    private File logsDir;

    @Before
    public void setUp() throws Exception {
        logsDir = new File(TEMP_DIR, "generated-" + System.currentTimeMillis());
        logsDir.mkdirs();

        generateLogFiles(logsDir, PREFIX_1, SUFIX_2, 3);
        generateLogFiles(logsDir, PREFIX_2, SUFIX_1, 3);
        generateLogFiles(logsDir, PREFIX_3, SUFIX_1, 3);

        String date = LocalDateTime.now().minusDays(3).toString();
        File file = new File(logsDir, PREFIX_1 + date + SUFIX_1);
        file.createNewFile();

    }

    @After
    public void tearDown() {
        for (String file : logsDir.list()) {
            new File(file).delete();
        }
        logsDir.delete();
    }

    @SuppressWarnings("unused")
    @Test
    public void testCleanOnInitOneHandler() throws Exception {
        generateLogFiles(logsDir, PREFIX_1, SUFIX_1, 3);

        FileHandler handler = new FileHandler(logsDir.getAbsolutePath(), PREFIX_1, SUFIX_1, 2);

        Thread.sleep(1000);

        assertTrue(logsDir.list().length == 13);
    }

    @SuppressWarnings("unused")
    @Test
    public void testCleanOnInitMultipleHandlers() throws Exception {
        generateLogFiles(logsDir, PREFIX_1, SUFIX_1, 3);

        FileHandler handler1 = new FileHandler(logsDir.getAbsolutePath(), PREFIX_1, SUFIX_1, 2);
        FileHandler handler2 = new FileHandler(logsDir.getAbsolutePath(), PREFIX_1, SUFIX_2, 2);
        FileHandler handler3 = new FileHandler(logsDir.getAbsolutePath(), PREFIX_2, SUFIX_1, 2);
        FileHandler handler4 = new FileHandler(logsDir.getAbsolutePath(), PREFIX_3, SUFIX_1, 2);

        Thread.sleep(1000);

        assertTrue(logsDir.list().length == 13);
    }

    @SuppressWarnings("unused")
    @Test
    public void testCleanDisabled() throws Exception {
        generateLogFiles(logsDir, PREFIX_1, SUFIX_1, 3);

        FileHandler handler = new FileHandler(logsDir.getAbsolutePath(), PREFIX_1, SUFIX_1, -1);

        Thread.sleep(1000);

        assertTrue(logsDir.list().length == 14);
    }

    private void generateLogFiles(File dir, String prefix, String sufix, int amount)
            throws IOException {
        for (int i = 0; i < amount; i++) {
            String date = LocalDate.now().minusDays(i + 1).toString().substring(0, 10);
            File file = new File(dir, prefix + date + sufix);
            file.createNewFile();
        }
    }
}
