/*  Licensed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.tomcat.lite.http.services;

import java.io.IOException;
import java.util.logging.Logger;

import org.apache.tomcat.lite.http.Http11Connection;
import org.apache.tomcat.lite.http.HttpChannel;
import org.apache.tomcat.lite.http.HttpRequest;
import org.apache.tomcat.lite.http.HttpResponse;
import org.apache.tomcat.lite.http.HttpChannel.HttpService;
import org.apache.tomcat.lite.io.IOBuffer;

/**
 * Response is plain/text, copy of the received request
 */
public class EchoCallback implements HttpService {
    Logger log = Logger.getLogger("coyote.static");

    String contentType = "text/plain";


    public EchoCallback() {
    }

    @Override
    public void service(HttpRequest req, HttpResponse res) throws IOException {
        HttpChannel sproc = req.getHttpChannel();
        res.setStatus(200);
        res.setContentType(contentType);

        IOBuffer tmp = new IOBuffer(null);
        Http11Connection.serialize(req, tmp);

        sproc.getOut().append("REQ HEAD:\n");
        sproc.getOut().append(tmp.readAll(null));
        IOBuffer reqBuf = sproc.getOut();

        reqBuf.append("\nCONTENT_LENGTH:")
            .append(Long.toString(req.getContentLength()))
            .append("\n");
//
//        sproc.release();
    }


}