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
package org.apache.catalina.valves;

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

public class TestExtendedAccessLogValveOpen {

    /*
     * The base class tolerates a failure to open the log file and leaves the writer null. Opening a log file under a
     * path that cannot exist as a directory must not throw and must leave the writer null so that log writing is
     * skipped, matching the behaviour of the base class.
     */
    @Test
    public void testOpenFailureLeavesWriterNull() throws IOException {
        File notADirectory = File.createTempFile("tomcat-not-a-directory", ".txt");
        notADirectory.deleteOnExit();

        ExtendedAccessLogValve valve = new ExtendedAccessLogValve();
        valve.setDirectory(new File(notADirectory, "logs").getPath());

        // Must not throw, since the base class open() tolerates the failure
        valve.open();

        Assert.assertNull("The writer must be null after a failed open", valve.writer);
    }
}
