package org.apache.tomcat.util.modeler.modules;

import java.util.List;


/**
 * This mbean will load an extended mlet file ( similar in syntax with jboss ).
 * It'll keep track of all attribute changes and update the file when attributes
 * change. 
 */
public interface MbeansSourceMBean 
{
    /** Set the source to be used to load the mbeans
     * 
     * @param source File or URL
     */ 
    public void setSource( Object source );
    
    public Object getSource();
    
    /** Return the list of loaded mbeans names
     * 
     * @return List of ObjectName
     */ 
    public List getMBeans();

    /** Load the mbeans from the source. Called automatically on init() 
     * 
     * @throws Exception
     */ 
    public void load() throws Exception;
    
    /** Call the init method on all mbeans. Will call load if not done already
     * 
     * @throws Exception
     */ 
    public void init() throws Exception;

    /** Save the file.
     */ 
    public void save();
}
