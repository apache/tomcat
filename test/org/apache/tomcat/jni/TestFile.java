/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.jni;

import org.junit.Assert;
import org.junit.Test;


public class TestFile extends AbstractJniTest {

    @Test
    public void testInfoGet() throws Exception {
        String testFile = "test/org/apache/tomcat/jni/TestFile.java";
        java.io.File file = new java.io.File(testFile);
        Assert.assertTrue("File " + testFile + " does not exist!", file.exists());

        Library.initialize(null);
        long pool = Pool.create(0L);
        int openFlags = File.APR_FOPEN_READ | File.APR_FOPEN_BUFFERED | File.APR_FOPEN_XTHREAD;
        int openPermissions = File.APR_FPROT_OS_DEFAULT;
        int statFlags = File.APR_FINFO_MIN;
        long fd = File.open(testFile, openFlags, openPermissions, pool);
        FileInfo fileInfo = new FileInfo();
        for (int i = 0; i < 100000; i++) {
            org.apache.tomcat.jni.File.infoGet(fileInfo, statFlags, fd);
            @SuppressWarnings("unused")
            String info = inspectFileInfo(fileInfo);
        }
    }

    public static String inspectFileInfo(FileInfo fileInfo) {
        String result = fileInfo.toString() + " : {" +
                String.format("\n  pool : %d", fileInfo.pool) +
                String.format("\n  valid : %d", fileInfo.valid) +
                String.format("\n  protection : %d", fileInfo.protection) +
                String.format("\n  filetype : %d", fileInfo.filetype) +
                String.format("\n  user : %d", fileInfo.user) +
                String.format("\n  group : %d", fileInfo.group) +
                String.format("\n  inode : %d", fileInfo.inode) +
                String.format("\n  device : %d", fileInfo.device) +
                String.format("\n  nlink : %d", fileInfo.nlink) +
                String.format("\n  size : %d", fileInfo.size) +
                String.format("\n  csize : %d", fileInfo.csize) +
                String.format("\n  atime : %d", fileInfo.atime) +
                String.format("\n  mtime : %d", fileInfo.mtime) +
                String.format("\n  ctime : %d", fileInfo.ctime) +
                String.format("\n  fname : %s", fileInfo.fname) +
                String.format("\n  name : %s", fileInfo.name) +
                String.format("\n  filehand : %d", fileInfo.filehand) +
                "\n}";
        return result;
    }
}