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
package org.apache.tomcat.lite.coyote;

import java.io.IOException;
import java.util.logging.Logger;

import org.apache.coyote.Adapter;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.UriNormalizer;
import org.apache.tomcat.util.http.mapper.BaseMapper;
import org.apache.tomcat.util.http.mapper.MappingData;
import org.apache.tomcat.util.net.SocketStatus;

/**
 * 
 */
public class MapperAdapter implements Adapter {

    private BaseMapper mapper=new BaseMapper();
    
    static Logger log = Logger.getLogger("Mapper");
    static final int MAP_NOTE = 4;
    
    public MapperAdapter() {
        mapper.setDefaultHostName("localhost");
        mapper.setContext("", new String[] {"index.html"},
            null);
    }

    public MapperAdapter(BaseMapper mapper2) {
        mapper = mapper2;
    }
    
    public static MappingData getMappingData(Request req) {
        MappingData md = (MappingData) req.getNote(MAP_NOTE);
        if (md == null) {
            md = new MappingData();
            req.setNote(MAP_NOTE, md);
        }
        return md;
    }

    /**
     * Copy an array of bytes to a different position. Used during 
     * normalization.
     */
    public static void copyBytes(byte[] b, int dest, int src, int len) {
        for (int pos = 0; pos < len; pos++) {
            b[pos + dest] = b[pos + src];
        }
    }

    
    public void service(Request req, final Response res)
            throws Exception {
        long t0 = System.currentTimeMillis();
        try {
          // compute decodedURI - not done by connector
          UriNormalizer.decodeRequest(req.decodedURI(), req.requestURI(), req.getURLDecoder());
          MappingData mapRes = getMappingData(req);
          mapRes.recycle();
          
          mapper.map(req.requestURI(), mapRes);

          Adapter h=(Adapter)mapRes.wrapper;

          if (h != null) {
              log.info(">>>>>>>> START: " + req.method() + " " + 
                        req.decodedURI() + " " + 
                        h.getClass().getSimpleName());
              h.service( req, res );
          } else {
              res.setStatus(404);
          }
        } catch(IOException ex) {
            throw ex;
        } catch( Throwable t ) {
            t.printStackTrace();
        } finally {
            long t1 = System.currentTimeMillis();
            
            log.info("<<<<<<<< DONE: " + req.method() + " " + 
                    req.decodedURI() + " " + 
                    res.getStatus() + " " + 
                    (t1 - t0));
            
            // Final processing
            // TODO: only if not commet, this doesn't work with the 
            // other connectors since we don't have the info
            // TODO: add this note in the nio/apr connectors
            // TODO: play nice with TomcatLite, other adapters that flush/close
            if (res.getNote(CoyoteServer.COMET_RES_NOTE) == null) {
                MessageWriter mw = MessageWriter.getWriter(req, res, 0);
                mw.flush();
                mw.recycle();
                MessageReader reader = CoyoteUtils.getReader(req);
                reader.recycle();
                res.finish();

                req.recycle();
                res.recycle();
            }
        }
    }

    public BaseMapper getMapper() {
      return mapper;
    }

    public void setDefaultAdapter(Adapter adapter) {
        mapper.addWrapper("/", adapter);
    }
    
    public boolean event(Request req, Response res, boolean error) throws Exception {
        // TODO Auto-generated method stub
        return false;
    }

    public boolean event(Request req, Response res, SocketStatus status)
        throws Exception {
      return false;
    }

}