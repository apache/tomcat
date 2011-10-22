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
package org.apache.tomcat.lite.proxy;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Logger;

import org.apache.tomcat.lite.http.HttpRequest;
import org.apache.tomcat.lite.http.HttpResponse;
import org.apache.tomcat.lite.http.HttpChannel.HttpService;
import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.BBucket;
import org.apache.tomcat.lite.io.IOBuffer;

/*
 *
 * Serve static content, from memory.
 */
public class StaticContentService implements HttpService  {
    protected Logger log = Logger.getLogger("coyote.static");
    protected BBucket mb;

    protected boolean chunked = false;
    int code = 200;

    protected String contentType = "text/plain";


    public StaticContentService() {
    }

    /**
     * Used for testing chunked encoding.
     * @return
     */
    public StaticContentService chunked() {
      chunked = true;
      return this;
    }

    public StaticContentService setData(byte[] data) {
        mb = BBuffer.wrapper(data, 0, data.length);
        return this;
    }

    public StaticContentService setStatus(int status) {
        this.code = status;
        return this;
    }

    public StaticContentService withLen(int len) {
        byte[] data = new byte[len];
        for (int i = 0; i < len; i++) {
          data[i] = 'A';
        }
        mb = BBuffer.wrapper(data, 0, data.length);
        return this;
      }


    public StaticContentService setData(CharSequence data) {
      try {
          IOBuffer tmp = new IOBuffer(null);
          tmp.append(data);
          mb = tmp.readAll(null);
      } catch (IOException e) {
      }
      return this;
    }

    public StaticContentService setContentType(String ct) {
      this.contentType = ct;
      return this;
    }

    public void setFile(String path) {
      try {
        FileInputStream fis = new FileInputStream(path);
        BBuffer bc = BBuffer.allocate(4096);

        byte b[] = new byte[4096];
        int rd = 0;
        while ((rd = fis.read(b)) > 0) {
            bc.append(b, 0, rd);
        }
        mb = bc;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void service(HttpRequest httpReq, HttpResponse res) throws IOException {

        res.setStatus(code);

          if (!chunked) {
            res.setContentLength(mb.remaining());
          }
          res.setContentType(contentType);

          int len = mb.remaining();
          int first = 0;

          if (chunked) {
              first = len / 2;
              res.getBody()
                  .queue(BBuffer.wrapper(mb, 0, first));
              res.flush();
          }

          res.getBody().queue(BBuffer.wrapper(mb, 0, len - first));
    }
}