package org.apache.tomcat.util.modeler.modules;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import javax.management.ObjectName;

import org.apache.tomcat.util.modeler.Registry;

/** Source for descriptor data. More sources can be added.
 *
 */
public class ModelerSource {
    protected Object source;
    protected String location;

    /** Load data, returns a list of items. 
     * 
     * @param registry
     * @param location
     * @param type
     * @param source Introspected object or some other source
     * @throws Exception
     */ 
    public List loadDescriptors( Registry registry, String location,
                                 String type, Object source)
            throws Exception
    {
        // TODO
        return null;
    }
    
    /** Callback from the BaseMBean to notify that an attribute has changed.
     * Can be used to implement persistence.
     * 
     * @param oname
     * @param name
     * @param value
     */ 
    public void updateField( ObjectName oname, String name, 
                             Object value ) {
        // nothing by default 
    }

    public void store() {
        // nothing
    }

    protected InputStream getInputStream() throws IOException {
        if( source instanceof URL ) {
            URL url=(URL)source;
            location=url.toString();
            return url.openStream();
        } else if( source instanceof File ) {
            location=((File)source).getAbsolutePath();
            return new FileInputStream((File)source);            
        } else if( source instanceof String) {
            location=(String)source;
            return new FileInputStream((String)source);            
        } else if( source instanceof InputStream ) {
            return (InputStream)source;
        } 
        return null;
    }

}
