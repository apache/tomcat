/**
 * 
 */
package org.apache.tomcat.servlets.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Struct representation of webapp configuration.
 * 
 * All the data in web.xml, annotations, etc should be represented 
 * here. This class is serializable - but can be saved/loaded as 
 * json or any 'pojo' persistence.
 * 
 * 
 * Public fields to make it easy to access it, we can add accessors.
 * Naming should match the web.xml element name.
 * 
 * @author Costin Manolache
 */
public class ServletContextConfig  implements Serializable {
    
    public static final String SERIALIZED_PATH = "/WEB-INF/deploy_web.ser";
    private static final long serialVersionUID = 1728492145981883124L;
    
    public static final int CURRENT_VERSION = 1;
    
    public int version = CURRENT_VERSION;
    
    /**
     * Main config ( web.xml ) path and timestamp - touch it to reload. 
     */
    public List<String> fileName = new ArrayList<String>();
    public long timestamp;

    public boolean full;
    
    public String displayName;

    public HashMap<String, String> contextParam = new HashMap<String, String>();
    
    public HashMap<String, String> mimeMapping = new HashMap<String, String>(); // extension -> mime-type
    
    public ArrayList<String> listenerClass = new ArrayList<String>();
    
    public ArrayList<String> welcomeFileList = new ArrayList<String>();
    
    // code -> location 
    public HashMap<String, String> errorPageCode= new HashMap<String, String>(); 
    
    // exception -> location
    public HashMap<String, String> errorPageException= new HashMap<String, String>(); 

    public HashMap<String, String> localeEncodingMapping= new HashMap<String, String>(); // locale -> encoding
    
    // public HashMap tagLibs; // uri->location
    // jsp-property-group

    // securityConstraint
    public ArrayList<SecurityConstraintData> securityConstraint = new ArrayList<SecurityConstraintData>();
    
    // loginConfig
    public String authMethod;
    public String realmName;
    public String formLoginPage;
    public String formErrorPage;
    
    public ArrayList<String> securityRole = new ArrayList<String>();
    
    // envEntry
    public ArrayList<EnvEntryData> envEntry = new ArrayList<EnvEntryData>();
    
    // ejbRef
    // ejbLocalRef
    // serviceRef
    // resourceRef
    // resourceEnvRef
    // message-destination
    // message-destinationRef
    public HashMap<String, FilterData> filters = new HashMap<String, FilterData>();
    public HashMap<String, ServletData> servlets = new HashMap<String, ServletData>();

    public int sessionTimeout;
    public boolean distributable;
    
    public HashMap<String, String> servletMapping = new HashMap<String, String>(); // url -> servlet
    public ArrayList<FilterMappingData> filterMappings = new ArrayList<FilterMappingData>();
    public boolean metadataComplete = false;
    

    // Normalized
    public static class FilterMappingData implements Serializable {
        private static final long serialVersionUID = -4533568066713041994L;
        public String filterName;
        
        // Only one of the 2
        public String urlPattern;
        public String servletName;
        
        // REQUEST, FORWARD, INCLUDE, ERROR, ASYNC
        public List<String> dispatcher = new ArrayList<String>();
    }
    
    public static class EnvEntryData  implements Serializable {
        private static final long serialVersionUID = 7023847615343715257L;
        public String envEntryName;
        public String envEntryType;
        public String envEntryValue;
    }
    
    public static class ServiceData implements Serializable {
        public String name;
        public String className;

        public Map<String, String> initParams = new HashMap<String, String>();
        
        public boolean asyncSupported = false;
    }
    
    public static class FilterData extends ServiceData implements Serializable {
        private static final long serialVersionUID = -535820271746973166L;
    }
    
    public static class ServletData extends ServiceData implements Serializable {
        private static final long serialVersionUID = -3216904178501185930L;

        public ServletData() {
        }
        public ServletData(String servletName, String servletClass) {
            this.className = servletClass;
            this.name = servletName;
        }

        public String jspFile;
        public int loadOnStartup = -1;
        public String runAs;
        public Map<String, String> securityRoleRef = new HashMap<String, String>(); // roleName -> [roleLink]
        public boolean multipartConfig = false;
        
        public List<String> declaresRoles = new ArrayList<String>();
        
    }
    
    public static class WebResourceCollectionData implements Serializable {
        public String webResourceName;
        public ArrayList<String> urlPattern = new ArrayList<String>();
        public ArrayList<String> httpMethod = new ArrayList<String>();
    }    
    
    public static class SecurityConstraintData  implements Serializable {
        private static final long serialVersionUID = -4780214921810871769L;

        public ArrayList<String> roleName = new ArrayList<String>(); //   auth-constraint/role

        public ArrayList<WebResourceCollectionData> webResourceCollection = 
            new ArrayList<WebResourceCollectionData>();
        public String transportGuarantee;
        
    }    
}