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
package org.apache.tomcat.lite.http.services;

import java.io.IOException;

import org.apache.tomcat.lite.http.HttpRequest;
import org.apache.tomcat.lite.http.HttpResponse;
import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.proxy.StaticContentService;

/**
 * Test adapters that sleeps, for load test and for encoding.
 * REQUIRES THREAD POOL
 */
public class SleepCallback extends StaticContentService {
  long t1;
  long t2;
  long t3;
  long t4;

  public SleepCallback() {
  }

  public SleepCallback sleep(long t1, long t2, long t3,
                            long t4) {
    this.t1 = t1;
    this.t2 = t2;
    this.t3 = t3;
    this.t4 = t4;
    return this;
  }

  public SleepCallback sleep(long t1) {
    return sleep(t1, t1, t1, t1);
  }

  @Override
  public void service(HttpRequest httpReq, HttpResponse res) throws IOException {
      // TODO: blocking ! needs thread pool !
    try {

        Thread.currentThread().sleep(t1);
        res.setStatus(200);
        if (!chunked) {
            res.setContentLength(mb.remaining() * 2);
        }
        res.setContentType(contentType);

        res.flush();

        Thread.currentThread().sleep(t2);

        res.getBody().queue(BBuffer.wrapper(mb, 0, mb.remaining()));
        res.flush();

        //res.action(ActionCode.ACTION_CLIENT_FLUSH, res);

        Thread.currentThread().sleep(t3);

        res.getBody().queue(BBuffer.wrapper(mb, 0, mb.remaining()));
        res.flush();

        Thread.currentThread().sleep(t4);
    } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
    }
  }

}