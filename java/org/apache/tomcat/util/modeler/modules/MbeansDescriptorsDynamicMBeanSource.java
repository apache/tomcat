package org.apache.tomcat.util.modeler.modules;

import java.util.ArrayList;
import java.util.List;

import javax.management.DynamicMBean;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tomcat.util.modeler.AttributeInfo;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.OperationInfo;
import org.apache.tomcat.util.modeler.ParameterInfo;
import org.apache.tomcat.util.modeler.Registry;


/** Extract metadata from a dynamic mbean.
 * Used to wrap a dynamic mbean in order to implement persistence.
 * 
 * This is really an ugly asspect of the JMX spec - we need to convery 
 * from normal metainfo to model metainfo. The info is the same, but
 * they use a different class. Just like the DOM spec - where all implementations
 * get an order of unneeded complexity from the various types. 
 * 
 */ 
public class MbeansDescriptorsDynamicMBeanSource extends ModelerSource
{
    private static Log log = LogFactory.getLog(MbeansDescriptorsDynamicMBeanSource.class);

    Registry registry;
    String location;
    String type;
    Object source;
    List mbeans=new ArrayList();

    public void setRegistry(Registry reg) {
        this.registry=reg;
    }

    public void setLocation( String loc ) {
        this.location=loc;
    }

    /** Used if a single component is loaded
     *
     * @param type
     */
    public void setType( String type ) {
       this.type=type;
    }

    public void setSource( Object source ) {
        this.source=source;
    }

    public List loadDescriptors( Registry registry, String location,
                                 String type, Object source)
            throws Exception
    {
        setRegistry(registry);
        setLocation(location);
        setType(type);
        setSource(source);
        execute();
        return mbeans;
    }

    public void execute() throws Exception {
        if( registry==null ) registry=Registry.getRegistry();
        try {
            ManagedBean managed=createManagedBean(registry, null, source, type);
            if( managed==null ) return;
            managed.setName( type );
            
            mbeans.add(managed);

        } catch( Exception ex ) {
            log.error( "Error reading descriptors ", ex);
        }
    }



    // ------------ Implementation for non-declared introspection classes


    /**
     * XXX Find if the 'className' is the name of the MBean or
     *       the real class ( I suppose first )
     * XXX Read (optional) descriptions from a .properties, generated
     *       from source
     * XXX Deal with constructors
     *
     */
    public ManagedBean createManagedBean(Registry registry, String domain,
                                         Object realObj, String type)
    {
        if( ! ( realObj instanceof DynamicMBean )) {
            return null;
        }
        DynamicMBean dmb=(DynamicMBean)realObj;
        
        ManagedBean mbean= new ManagedBean();
        
        MBeanInfo mbi=dmb.getMBeanInfo();
        
        try {
            MBeanAttributeInfo attInfo[]=mbi.getAttributes();
            for( int i=0; i<attInfo.length; i++ ) {
                MBeanAttributeInfo mai=attInfo[i];
                String name=mai.getName();

                AttributeInfo ai=new AttributeInfo();
                ai.setName( name );

                ai.setType( mai.getType());
                ai.setReadable( mai.isReadable());
                ai.setWriteable( mai.isWritable());
                                                
                mbean.addAttribute(ai);
            }

            MBeanOperationInfo opInfo[]=mbi.getOperations();
            for( int i=0; i<opInfo.length; i++ ) {
                MBeanOperationInfo moi=opInfo[i];
                OperationInfo op=new OperationInfo();

                op.setName(moi.getName());
                op.setReturnType(moi.getReturnType());
                
                MBeanParameterInfo parms[]=moi.getSignature();
                for(int j=0; j<parms.length; j++ ) {
                    ParameterInfo pi=new ParameterInfo();
                    pi.setType(parms[i].getType());
                    pi.setName(parms[i].getName());
                    op.addParameter(pi);
                }
                mbean.addOperation(op);
            }

            if( log.isDebugEnabled())
                log.debug("Setting name: " + type );

            mbean.setName( type );

            return mbean;
        } catch( Exception ex ) {
            ex.printStackTrace();
            return null;
        }
    }

}
