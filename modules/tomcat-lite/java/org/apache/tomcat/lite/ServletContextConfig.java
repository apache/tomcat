/**
 * 
 */
package org.apache.tomcat.lite;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * All the data in web.xml should be represented here.
 * 
 * Public fields to make it easy to access it. 
 * Naming should match the web.xml element name.
 * 
 * @author Costin Manolache
 */
public class ServletContextConfig  implements Serializable {
    
    private static final long serialVersionUID = 1728492145981883124L;
    
    public String fileName;
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
    

    public static class FilterData implements Serializable {
        private static final long serialVersionUID = -535820271746973166L;

        public HashMap<String, String> initParams = new HashMap<String, String>();
        public String filterClass;
        public String filterName;
    }
    
    public static class FilterMappingData implements Serializable {
        private static final long serialVersionUID = -4533568066713041994L;
        public String filterName;
        public String urlPattern;
        public String servletName;
        public ArrayList<String> dispatcher = new ArrayList<String>();
    }
    
    public static class EnvEntryData  implements Serializable {
        private static final long serialVersionUID = 7023847615343715257L;
        public String envEntryName;
        public String envEntryType;
        public String envEntryValue;
    }
    
    public static class ServletData implements Serializable {
        private static final long serialVersionUID = -3216904178501185930L;

        public ServletData() {
        }
        public ServletData(String servletName, String servletClass) {
            this.servletClass = servletClass;
            this.servletName = servletName;
        }

        public HashMap<String, String> initParams = new HashMap<String, String>();
        public String servletName;
        public String servletClass;
        public String jspFile;
        public int loadOnStartup = -1;
        public String runAs;
        public HashMap<String, String> securityRoleRef = new HashMap<String, String>(); // roleName -> [roleLink]
        
    }
    
    public static class SecurityConstraintData  implements Serializable {
        private static final long serialVersionUID = -4780214921810871769L;

        public ArrayList<String> roleName = new ArrayList<String>(); //   auth-constraint/role

        public ArrayList<String> webResourceCollection = new ArrayList<String>();
        public String transportGuarantee;
        
    }    
}