/*
 */
package org.apache.tomcat.lite.coyote;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.coyote.ActionCode;
import org.apache.coyote.ActionHook;
import org.apache.coyote.Adapter;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.tomcat.integration.ObjectManager;
import org.apache.tomcat.lite.Connector;
import org.apache.tomcat.lite.ServletRequestImpl;
import org.apache.tomcat.lite.ServletResponseImpl;
import org.apache.tomcat.lite.TomcatLite;
import org.apache.tomcat.util.net.SocketStatus;

public class CoyoteHttp implements Adapter, Connector {

    //private TomcatLite lite;
    CoyoteServer coyote;
    private TomcatLite lite;

    public CoyoteHttp() {
    }


    @Override
    public void finishResponse(HttpServletResponse res) throws IOException {
        ((ServletResponseImpl) res).getCoyoteResponse().finish();
    }


    public void recycle(HttpServletRequest req, HttpServletResponse res) {
    
    }
    
    public void setPort(int port) {
        if (getConnectors() != null) {
            coyote.setPort(port);
        }
    }
    
    @Override
    public void setDaemon(boolean b) {
        if (getConnectors() != null) {
            coyote.setDaemon(b);
        }
    }


    @Override
    public void start() {
        if (getConnectors() != null) {
            try {
                coyote.init();
                coyote.start();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }



    @Override
    public void stop() {
        if (coyote != null) {
            try {
                coyote.stop();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }        
    }
    
    @Override
    public void initRequest(HttpServletRequest hreq, HttpServletResponse hres) {
        ServletRequestImpl req = (ServletRequestImpl) hreq;
        ServletResponseImpl res = (ServletResponseImpl) hres;
        
        Request creq = new Request();
        res.setCoyoteResponse(new Response());
        res.getCoyoteResponse().setRequest(creq);
        res.getCoyoteResponse().setHook(new ActionHook() {
          public void action(ActionCode actionCode, 
                             Object param) {
          }
        });
        
        req.setCoyoteRequest(creq);
        
        res.setConnector();
        
    }
      
    // Coyote-specific hooking.
    // This could be moved out to a separate class, TomcatLite can 
    // work without it.
    public CoyoteServer getConnectors() {
        if (coyote == null) {
            coyote = new CoyoteServer();
            coyote.addAdapter("/", this);        
        }
        return coyote;
    }

    public void setConnectors(CoyoteServer server) {
        this.coyote = server;
        coyote.addAdapter("/", this);        
    }

    @Override
    public void service(Request req, Response res) throws Exception {
        // find the facades
        ServletRequestImpl sreq = (ServletRequestImpl) req.getNote(CoyoteServer.ADAPTER_REQ_NOTE);
        ServletResponseImpl sres = (ServletResponseImpl) res.getNote(CoyoteServer.ADAPTER_RES_NOTE);
        if (sreq == null) {
          sreq = new ServletRequestImpl();
          sres = sreq.getResponse();
          
          sreq.setCoyoteRequest(req);
          sres.setCoyoteResponse(res);
          
          req.setNote(CoyoteServer.ADAPTER_REQ_NOTE, sreq);
          res.setNote(CoyoteServer.ADAPTER_RES_NOTE, sres);
          
          sres.setConnector();
          
        }
        
        lite.service(sreq, sres);
        
        if (res.getNote(CoyoteServer.COMET_RES_NOTE) == null) {
          if (!sres.isCommitted()) {
              res.sendHeaders();
          }
          sres.getOutputBuffer().flush();
          res.finish();

          sreq.recycle();
          sres.recycle();
        }
    }
    
    // ---- Coyote ---
    
    @Override
    public boolean event(Request req, Response res, SocketStatus status)
        throws Exception {
      return false;
    }


    public void setTomcatLite(TomcatLite lite) {
        this.lite = lite;
    }


    @Override
    public void setObjectManager(ObjectManager objectManager) {
        getConnectors();
        coyote.setObjectManager(objectManager);
    }


    
}
