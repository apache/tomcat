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
package org.apache.tomcat.lite.http;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import org.apache.tomcat.lite.http.HttpChannel.HttpService;
import org.apache.tomcat.lite.http.HttpChannel.RequestCompleted;
import org.apache.tomcat.lite.io.CBuffer;
import org.apache.tomcat.lite.io.FileConnector;

/**
 * This class has several functions:
 * - maps the request to another HttpService
 * - decide if the request should be run in the selector thread
 * or in a thread pool
 * - finalizes the request ( close / flush )
 * - detects if the request is complete or set callbacks
 * for receive/flush/done.
 *
 */
public class Dispatcher implements HttpService {

    private BaseMapper mapper;
    static boolean debug = false;
    static Logger log = Logger.getLogger("Mapper");
    Executor tp = Executors.newCachedThreadPool();

    public Dispatcher() {
        init();
    }

    protected void init() {
        mapper = new BaseMapper();
    }

    public void runService(HttpChannel ch) {
        runService(ch, true);
    }

    public void runService(HttpChannel ch, boolean recycle) {
        MappingData mapRes = ch.getRequest().getMappingData();
        HttpService h = (HttpService) mapRes.getServiceObject();
        try {
            h.service(ch.getRequest(), ch.getResponse());
            if (!ch.getRequest().isAsyncStarted()) {
                ch.complete();
                if (recycle) {
                    ch.release(); // recycle objects.
                }
            } else {
                // Nothing - complete must be called when done.
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch( Throwable t ) {
            t.printStackTrace();
        }
    }

    @Override
    public void service(HttpRequest httpReq, HttpResponse httpRes) throws IOException {
        service(httpReq, httpRes, false, true);
    }

    /**
     * Process the request/response in the current thread, without
     * release ( recycle ) at the end.
     *
     * For use by tests and/or in-memory running of servlets.
     *
     * If no connection is associated with the request - the
     * output will remain in the out buffer.
     */
    public void run(HttpRequest httpReq, HttpResponse httpRes) throws IOException {
        service(httpReq, httpRes, true, false);
    }


    public void service(HttpRequest httpReq, HttpResponse httpRes, boolean noThread, boolean recycle)
            throws IOException {
        long t0 = System.currentTimeMillis();
        HttpChannel http = httpReq.getHttpChannel();

        http.setCompletedCallback(doneCallback);

        try {
          // compute decodedURI - not done by connector
            MappingData mapRes = httpReq.getMappingData();
            mapRes.recycle();

            mapper.map(httpReq.serverName(),
                  httpReq.decodedURI(), mapRes);

          HttpService h = (HttpService) mapRes.getServiceObject();

          if (h != null) {
              if (debug) {
                  log.info(">>>>>>>> START: " + http.getRequest().method() + " " +
                      http.getRequest().decodedURI() + " " +
                      h.getClass().getSimpleName());
              }

              if (mapRes.service.selectorThread || noThread) {
                  runService(http, recycle);
              } else {
                  tp.execute(httpReq.getHttpChannel().dispatcherRunnable);
              }

          } else {
              httpRes.setStatus(404);
              http.complete();
          }

        } catch (IOException ex) {
            if ("Broken pipe".equals(ex.getMessage())) {
                log.warning("Connection interrupted while writting");
            }
            throw ex;
        } catch( Throwable t ) {
            t.printStackTrace();
            httpRes.setStatus(500);
            http.abort(t);
        }
    }

    private RequestCompleted doneCallback = new RequestCompleted() {
        @Override
        public void handle(HttpChannel client, Object extraData) throws IOException {
            if (debug) {
                log.info("<<<<<<<< DONE: " + client.getRequest().method() + " " +
                        client.getRequest().decodedURI() + " " +
                        client.getResponse().getStatus() + " "
                        );
            }
        }
    };

    public BaseMapper.Context addContext(String hostname, String ctxPath,
            Object ctx, String[] welcomeResources, FileConnector resources,
            HttpService ctxService) {
        return mapper.addContext(hostname, ctxPath, ctx, welcomeResources, resources,
                ctxService);
    }

    public BaseMapper.Context addContext(String ctxPath) {
        return mapper.addContext(null, ctxPath, null, null, null,
                null);
    }

    public void map(CBuffer hostMB, CBuffer urlMB, MappingData md) {
        try {
            mapper.map(hostMB, urlMB, md);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void map(BaseMapper.Context ctx,
            CBuffer uri, MappingData md) {
        try {
            mapper.internalMapWrapper(ctx, uri, md);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void addWrapper(BaseMapper.Context ctx, String path,
            HttpService service) {
        mapper.addWrapper(ctx, path, service);
    }


    public void setDefaultService(HttpService service) {
        BaseMapper.Context mCtx =
            mapper.addContext(null, "/", null, null, null, null);
        mapper.addWrapper(mCtx, "/", service);
    }


}