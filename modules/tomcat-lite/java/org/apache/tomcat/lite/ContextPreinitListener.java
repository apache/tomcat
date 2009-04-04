/*
 */
package org.apache.tomcat.lite;

import javax.servlet.ServletContext;

/**
 * Tomcat-lite specific interface ( could be moved to addons ).
 * This class will be called before initialization - implementations
 * can add servlets, filters, etc. In particular web.xml parsing
 * is done implementing this interface. 
 * 
 * On a small server you could remove web.xml support to reduce 
 * footprint, and either hardcode this class or use properties.
 * Same if you already use a framework and you inject settings
 * or use framework's registry (OSGI).
 * 
 * @author Costin Manolache
 */
public interface ContextPreinitListener {

    public void preInit(ServletContext ctx);
}
