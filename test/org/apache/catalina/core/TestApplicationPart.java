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
package org.apache.catalina.core;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.http.fileupload.disk.DiskFileItem;
import org.apache.tomcat.util.http.fileupload.util.FileItemHeadersImpl;

public class TestApplicationPart {

    private static ApplicationPart createPart(String contentDisposition) {
        DiskFileItem fileItem = new DiskFileItem("field", "text/plain", false, "name", 102400, null);
        FileItemHeadersImpl headers = new FileItemHeadersImpl();
        headers.addHeader("Content-Disposition", contentDisposition);
        fileItem.setHeaders(headers);
        return new ApplicationPart(fileItem, null);
    }


    @Test
    public void testSubmittedFileNameUnterminatedQuotedString() {
        // An escaped closing quote makes the quoted-string invalid.
        // getSubmittedFileName() must not diverge (return null) from the
        // classifier used by the multipart parser (which yields "abc\").
        ApplicationPart part = createPart("form-data; name=\"x\"; filename=\"abc\\\"");
        Assert.assertEquals("abc\\", part.getSubmittedFileName());
    }
}
