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

import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.integration.ObjectManager;


/** 
 * Simple example of embeding coyote servlet.
 * 
 */
public class CoyoteServer  {
  protected int port = 8800;
  protected boolean daemon = false;

  /**
   * Note indicating the response is COMET. 
   */
  public static final int COMET_RES_NOTE = 2;
  public static final int COMET_REQ_NOTE = 2;
  
  public static final int ADAPTER_RES_NOTE = 1;    
  public static final int ADAPTER_REQ_NOTE = 1;    
  
  protected ProtocolHandler proto;

  protected Adapter adapter = new MapperAdapter();
  protected int maxThreads = 20;
  boolean started = false;
  boolean async = false; // use old nio connector
  
  protected ObjectManager om;
  
  public CoyoteServer() {  
  }
  
  public void setObjectManager(ObjectManager om) {
      this.om = om;
  }
  
  /** 
   * Add an adapter. If more than the 'default' adapter is
   * added, a MapperAdapter will be inserted.
   * 
   * @param path Use "/" for the default.
   * @param adapter
   */
  public void addAdapter(String path, Adapter added) {
      if ("/".equals(path)) {
          ((MapperAdapter) adapter).setDefaultAdapter(added);        
      } else {
          ((MapperAdapter) adapter).getMapper().addWrapper(path, added);
      }
  }
  
  /**
   */
  public void run() {
      try {
          init();
          start();
      } catch(IOException ex) {
          ex.printStackTrace();
      }
  }

  public void setDaemon(boolean b) {
    daemon = b;
  }
  
  public void init() {
    //JdkLoggerConfig.loadCustom();
    om.bind("CoyoteServer:" + "CoyoteServer-" + port, 
            this);
    om.bind("CoyoteAdapter", adapter);
  }

  protected void initAdapters() {
      if (proto == null) {
          addProtocolHandler(port, daemon);
      }
    // adapter = ...
    // Adapter secondaryadapter = ...
    //registry.registerComponent(secondaryadapter, ":name=adapter", null);
  }

  public void stop() throws Exception {
    if (!started) {
      return;
    }
    proto.destroy();
    started = false;
  }
  
  /**
   *  Simple CLI support - arg is a path:className pair.
   */
  public void setAdapter(String arg)  {
    String[] pathClass = arg.split(":", 2);
    try {
      Class c = Class.forName(pathClass[1]);
      Adapter a = (Adapter) c.newInstance();
      addAdapter(pathClass[0],a);
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }
  
  public void setPort(int port) {
    if (proto != null) {
        proto.setAttribute("port", Integer.toString(port));
    }
    this.port = port;
  }
  
  public void setConnector(ProtocolHandler h) {
      this.proto = h;
      h.setAttribute("port", Integer.toString(port));

      om.bind("ProtocolHandler:" + "ep-" + port, proto);
  }
  
  public void addProtocolHandler(int port, boolean daemon) {
      Http11NioProtocol proto = new Http11NioProtocol();
      proto.setCompression("on");
      proto.setCompressionMinSize(32);
      proto.setPort(port);
      proto.getEndpoint().setDaemon(daemon);
      CoyoteServer server = this;
      server.setConnector(proto);
      server.setPort(port);
      server.setDaemon(daemon);
  }
  
  public void addProtocolHandler(ProtocolHandler proto, 
                                 int port, boolean daemon) {
      CoyoteServer server = this;
      server.setConnector(proto);
      server.setPort(port);
      server.setDaemon(daemon);
  }
  

  
  public void start() throws IOException {
    try {
      if (started) {
        return;
      }
      initAdapters();

      // not required - should run fine without a connector.
      if (proto != null) {
          proto.setAdapter(adapter);
      
          proto.init();
          proto.start();
      }
      
      started = true;
    } catch (Throwable e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }
  
  public boolean getStarted() {
    return started;
  } 
}