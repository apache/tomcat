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
package org.apache.catalina.ha.deploy;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

public class TestFileMessageFactory {

    @Test
    public void testRoundTripSmallerThanReadSize() throws Exception {
        roundTripTest(100);
    }

    @Test
    public void testRoundTripExactMultipleOfReadSize() throws Exception {
        roundTripTest(2 * FileMessageFactory.READ_SIZE);
    }

    @Test
    public void testRoundTripNonExactMultipleOfReadSize() throws Exception {
        roundTripTest(2 * FileMessageFactory.READ_SIZE + 100);
    }

    private void roundTripTest(int size) throws Exception {
        byte[] content = new byte[size];
        new Random(12345L).nextBytes(content);

        File source = File.createTempFile("test-source", ".war");
        File target = File.createTempFile("test-target", ".war");
        try {
            try (FileOutputStream out = new FileOutputStream(source)) {
                out.write(content);
            }

            FileMessageFactory readFactory = FileMessageFactory.getInstance(source, false);
            FileMessageFactory writeFactory = FileMessageFactory.getInstance(target, true);

            FileMessage msg = new FileMessage(null, source.getName(), "/" + source.getName());
            int messageCount = 0;
            boolean complete = false;
            msg = readFactory.readMessage(msg);
            while (msg != null) {
                messageCount++;
                complete = writeFactory.writeMessage(msg);
                msg = readFactory.readMessage(msg);
            }

            int expectedCount = (size + FileMessageFactory.READ_SIZE - 1) / FileMessageFactory.READ_SIZE;
            Assert.assertEquals("Number of messages", expectedCount, messageCount);
            // The transfer must be signalled complete by the last message,
            // otherwise the receiving node never deploys the WAR
            Assert.assertEquals(messageCount > 0, complete);
            Assert.assertArrayEquals("Target file content", content, Files.readAllBytes(target.toPath()));
        } finally {
            if (!source.delete()) {
                System.out.println("Could not delete " + source);
            }
            if (!target.delete()) {
                System.out.println("Could not delete " + target);
            }
        }
    }

    @Test
    public void testTotalNrOfMsgsSetOnFirstMessage() throws Exception {
        int size = FileMessageFactory.READ_SIZE;
        byte[] content = new byte[size];

        File source = File.createTempFile("test-source", ".war");
        try {
            try (FileOutputStream out = new FileOutputStream(source)) {
                out.write(content);
            }

            FileMessageFactory readFactory = FileMessageFactory.getInstance(source, false);
            FileMessage msg = new FileMessage(null, source.getName(), "/" + source.getName());
            msg = readFactory.readMessage(msg);
            Assert.assertEquals("Message number", 1, msg.getMessageNumber());
            Assert.assertEquals("Total number of messages", 1, msg.getTotalNrOfMsgs());
            // One message for one full buffer, so EOF comes next
            Assert.assertNull("No further message", readFactory.readMessage(msg));
        } finally {
            if (!source.delete()) {
                System.out.println("Could not delete " + source);
            }
        }
    }
}
