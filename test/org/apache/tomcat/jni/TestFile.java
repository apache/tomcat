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

/*
 * @deprecated  The scope of the APR/Native Library will be reduced in Tomcat
 *              10.1.x onwards to only those components required to provide
 *              OpenSSL integration with the NIO and NIO2 connectors.
 */
@Deprecated
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
                String.format("\n  pool : %d", Long.valueOf(fileInfo.pool)) +
                String.format("\n  valid : %d", Integer.valueOf(fileInfo.valid)) +
                String.format("\n  protection : %d", Integer.valueOf(fileInfo.protection)) +
                String.format("\n  filetype : %d", Integer.valueOf(fileInfo.filetype)) +
                String.format("\n  user : %d", Integer.valueOf(fileInfo.user)) +
                String.format("\n  group : %d", Integer.valueOf(fileInfo.group)) +
                String.format("\n  inode : %d", Integer.valueOf(fileInfo.inode)) +
                String.format("\n  device : %d", Integer.valueOf(fileInfo.device)) +
                String.format("\n  nlink : %d", Integer.valueOf(fileInfo.nlink)) +
                String.format("\n  size : %d", Long.valueOf(fileInfo.size)) +
                String.format("\n  csize : %d", Long.valueOf(fileInfo.csize)) +
                String.format("\n  atime : %d", Long.valueOf(fileInfo.atime)) +
                String.format("\n  mtime : %d", Long.valueOf(fileInfo.mtime)) +
                String.format("\n  ctime : %d", Long.valueOf(fileInfo.ctime)) +
                String.format("\n  fname : %s", fileInfo.fname) +
                String.format("\n  name : %s", fileInfo.name) +
                String.format("\n  filehand : %d", Long.valueOf(fileInfo.filehand)) +
                "\n}";
        return result;
    }
}