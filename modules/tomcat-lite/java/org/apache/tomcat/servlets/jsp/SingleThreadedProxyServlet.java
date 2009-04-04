/*
 */
package org.apache.tomcat.servlets.jsp;

import java.util.Stack;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** For SingleThreadedServlet support. 
 * 
 * This is container independent.
 * 
 * Will maintain a pool of servlets, etc 
 * 
 * @author Costin Manolache
 */
public class SingleThreadedProxyServlet extends HttpServlet {

    private Class classClass = null;
    private transient boolean singleThreadModel = false;
    /**
     * Stack containing the STM instances.
     */
    private transient Stack instancePool = null;

    /**
     * Extra params: 
     *   - servlet-class - the class of the single-threaded servlet
     *   - 
     * 
     */
    public void init() {
        
    }
    
    public void service(HttpServletRequest req, HttpServletResponse res) throws ServletException {
        synchronized (instancePool) {
            if (instancePool.isEmpty()) {
                try {
                    Servlet newServlet = null; // loadServlet();

                    // todo: should we init each of them ?
                    
                    newServlet.service(req, res);
                    
                    
                } catch (Throwable e) {
                    throw new ServletException("allocate ",
                            e);
                }
            }
            Servlet s = (Servlet) instancePool.pop();
        }

        
    }
}
