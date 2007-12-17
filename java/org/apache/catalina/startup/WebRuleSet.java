/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.catalina.startup;


import java.lang.reflect.Method;
import java.util.ArrayList;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.deploy.ContextHandler;
import org.apache.catalina.deploy.ContextService;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.digester.CallMethodRule;
import org.apache.tomcat.util.digester.CallParamRule;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.Rule;
import org.apache.tomcat.util.digester.RuleSetBase;
import org.apache.tomcat.util.digester.SetNextRule;
import org.xml.sax.Attributes;


/**
 * <p><strong>RuleSet</strong> for processing the contents of a web application
 * deployment descriptor (<code>/WEB-INF/web.xml</code>) resource.</p>
 *
 * @author Craig R. McClanahan
 * @version $Revision$ $Date$
 */

public class WebRuleSet extends RuleSetBase {


    // ----------------------------------------------------- Instance Variables


    /**
     * The matching pattern prefix to use for recognizing our elements.
     */
    protected String prefix = null;
    
    
    /**
     * The <code>SetSessionConfig</code> rule used to parse the web.xml
     */
    protected SetSessionConfig sessionConfig;
    
    
    /**
     * The <code>SetLoginConfig</code> rule used to parse the web.xml
     */
    protected SetLoginConfig loginConfig;

    
    /**
     * The <code>SetJspConfig</code> rule used to parse the web.xml
     */    
    protected SetJspConfig jspConfig;


    // ------------------------------------------------------------ Constructor


    /**
     * Construct an instance of this <code>RuleSet</code> with the default
     * matching pattern prefix.
     */
    public WebRuleSet() {

        this("");

    }


    /**
     * Construct an instance of this <code>RuleSet</code> with the specified
     * matching pattern prefix.
     *
     * @param prefix Prefix for matching pattern rules (including the
     *  trailing slash character)
     */
    public WebRuleSet(String prefix) {

        super();
        this.namespaceURI = null;
        this.prefix = prefix;

    }


    // --------------------------------------------------------- Public Methods


    /**
     * <p>Add the set of Rule instances defined in this RuleSet to the
     * specified <code>Digester</code> instance, associating them with
     * our namespace URI (if any).  This method should only be called
     * by a Digester instance.</p>
     *
     * @param digester Digester instance to which the new Rule instances
     *  should be added.
     */
    public void addRuleInstances(Digester digester) {
        sessionConfig = new SetSessionConfig();
        jspConfig = new SetJspConfig();
        loginConfig = new SetLoginConfig();
        
        digester.addRule(prefix + "web-app",
                         new SetPublicIdRule("setPublicId"));
        digester.addRule(prefix + "web-app",
                         new IgnoreAnnotationsRule());

        digester.addCallMethod(prefix + "web-app/context-param",
                               "addParameter", 2);
        digester.addCallParam(prefix + "web-app/context-param/param-name", 0);
        digester.addCallParam(prefix + "web-app/context-param/param-value", 1);

        digester.addCallMethod(prefix + "web-app/display-name",
                               "setDisplayName", 0);

        digester.addRule(prefix + "web-app/distributable",
                         new SetDistributableRule());

        configureNamingRules(digester);

        digester.addObjectCreate(prefix + "web-app/error-page",
                                 "org.apache.catalina.deploy.ErrorPage");
        digester.addSetNext(prefix + "web-app/error-page",
                            "addErrorPage",
                            "org.apache.catalina.deploy.ErrorPage");

        digester.addCallMethod(prefix + "web-app/error-page/error-code",
                               "setErrorCode", 0);
        digester.addCallMethod(prefix + "web-app/error-page/exception-type",
                               "setExceptionType", 0);
        digester.addCallMethod(prefix + "web-app/error-page/location",
                               "setLocation", 0);

        digester.addObjectCreate(prefix + "web-app/filter",
                                 "org.apache.catalina.deploy.FilterDef");
        digester.addSetNext(prefix + "web-app/filter",
                            "addFilterDef",
                            "org.apache.catalina.deploy.FilterDef");

        digester.addCallMethod(prefix + "web-app/filter/description",
                               "setDescription", 0);
        digester.addCallMethod(prefix + "web-app/filter/display-name",
                               "setDisplayName", 0);
        digester.addCallMethod(prefix + "web-app/filter/filter-class",
                               "setFilterClass", 0);
        digester.addCallMethod(prefix + "web-app/filter/filter-name",
                               "setFilterName", 0);
        digester.addCallMethod(prefix + "web-app/filter/large-icon",
                               "setLargeIcon", 0);
        digester.addCallMethod(prefix + "web-app/filter/small-icon",
                               "setSmallIcon", 0);

        digester.addCallMethod(prefix + "web-app/filter/init-param",
                               "addInitParameter", 2);
        digester.addCallParam(prefix + "web-app/filter/init-param/param-name",
                              0);
        digester.addCallParam(prefix + "web-app/filter/init-param/param-value",
                              1);

        digester.addObjectCreate(prefix + "web-app/filter-mapping",
                                 "org.apache.catalina.deploy.FilterMap");
        digester.addSetNext(prefix + "web-app/filter-mapping",
                                 "addFilterMap",
                                 "org.apache.catalina.deploy.FilterMap");

        digester.addCallMethod(prefix + "web-app/filter-mapping/filter-name",
                               "setFilterName", 0);
        digester.addCallMethod(prefix + "web-app/filter-mapping/servlet-name",
                               "addServletName", 0);
        digester.addCallMethod(prefix + "web-app/filter-mapping/url-pattern",
                               "addURLPattern", 0);

        digester.addCallMethod(prefix + "web-app/filter-mapping/dispatcher",
                               "setDispatcher", 0);

         digester.addCallMethod(prefix + "web-app/listener/listener-class",
                                "addApplicationListener", 0);
         
        digester.addRule(prefix + "web-app/jsp-config",
                         jspConfig);
        
        digester.addCallMethod(prefix + "web-app/jsp-config/jsp-property-group/url-pattern",
                               "addJspMapping", 0);

        digester.addCallMethod(prefix + "web-app/listener/listener-class",
                               "addApplicationListener", 0);
        
        digester.addRule(prefix + "web-app/login-config",
                         loginConfig);

        digester.addObjectCreate(prefix + "web-app/login-config",
                                 "org.apache.catalina.deploy.LoginConfig");
        digester.addSetNext(prefix + "web-app/login-config",
                            "setLoginConfig",
                            "org.apache.catalina.deploy.LoginConfig");

        digester.addCallMethod(prefix + "web-app/login-config/auth-method",
                               "setAuthMethod", 0);
        digester.addCallMethod(prefix + "web-app/login-config/realm-name",
                               "setRealmName", 0);
        digester.addCallMethod(prefix + "web-app/login-config/form-login-config/form-error-page",
                               "setErrorPage", 0);
        digester.addCallMethod(prefix + "web-app/login-config/form-login-config/form-login-page",
                               "setLoginPage", 0);

        digester.addCallMethod(prefix + "web-app/mime-mapping",
                               "addMimeMapping", 2);
        digester.addCallParam(prefix + "web-app/mime-mapping/extension", 0);
        digester.addCallParam(prefix + "web-app/mime-mapping/mime-type", 1);


        digester.addObjectCreate(prefix + "web-app/security-constraint",
                                 "org.apache.catalina.deploy.SecurityConstraint");
        digester.addSetNext(prefix + "web-app/security-constraint",
                            "addConstraint",
                            "org.apache.catalina.deploy.SecurityConstraint");

        digester.addRule(prefix + "web-app/security-constraint/auth-constraint",
                         new SetAuthConstraintRule());
        digester.addCallMethod(prefix + "web-app/security-constraint/auth-constraint/role-name",
                               "addAuthRole", 0);
        digester.addCallMethod(prefix + "web-app/security-constraint/display-name",
                               "setDisplayName", 0);
        digester.addCallMethod(prefix + "web-app/security-constraint/user-data-constraint/transport-guarantee",
                               "setUserConstraint", 0);

        digester.addObjectCreate(prefix + "web-app/security-constraint/web-resource-collection",
                                 "org.apache.catalina.deploy.SecurityCollection");
        digester.addSetNext(prefix + "web-app/security-constraint/web-resource-collection",
                            "addCollection",
                            "org.apache.catalina.deploy.SecurityCollection");
        digester.addCallMethod(prefix + "web-app/security-constraint/web-resource-collection/http-method",
                               "addMethod", 0);
        digester.addCallMethod(prefix + "web-app/security-constraint/web-resource-collection/url-pattern",
                               "addPattern", 0);
        digester.addCallMethod(prefix + "web-app/security-constraint/web-resource-collection/web-resource-name",
                               "setName", 0);

        digester.addCallMethod(prefix + "web-app/security-role/role-name",
                               "addSecurityRole", 0);

        digester.addRule(prefix + "web-app/servlet",
                         new WrapperCreateRule());
        digester.addSetNext(prefix + "web-app/servlet",
                            "addChild",
                            "org.apache.catalina.Container");

        digester.addCallMethod(prefix + "web-app/servlet/init-param",
                               "addInitParameter", 2);
        digester.addCallParam(prefix + "web-app/servlet/init-param/param-name",
                              0);
        digester.addCallParam(prefix + "web-app/servlet/init-param/param-value",
                              1);

        digester.addCallMethod(prefix + "web-app/servlet/jsp-file",
                               "setJspFile", 0);
        digester.addCallMethod(prefix + "web-app/servlet/load-on-startup",
                               "setLoadOnStartupString", 0);
        digester.addCallMethod(prefix + "web-app/servlet/run-as/role-name",
                               "setRunAs", 0);

        digester.addCallMethod(prefix + "web-app/servlet/security-role-ref",
                               "addSecurityReference", 2);
        digester.addCallParam(prefix + "web-app/servlet/security-role-ref/role-link", 1);
        digester.addCallParam(prefix + "web-app/servlet/security-role-ref/role-name", 0);

        digester.addCallMethod(prefix + "web-app/servlet/servlet-class",
                              "setServletClass", 0);
        digester.addCallMethod(prefix + "web-app/servlet/servlet-name",
                              "setName", 0);

        digester.addRule(prefix + "web-app/servlet-mapping",
                               new CallMethodMultiRule("addServletMapping", 2, 0));
        digester.addCallParam(prefix + "web-app/servlet-mapping/servlet-name", 1);
        digester.addRule(prefix + "web-app/servlet-mapping/url-pattern", new CallParamMultiRule(0));

        digester.addRule(prefix + "web-app/session-config",
                         sessionConfig);
        
        digester.addCallMethod(prefix + "web-app/session-config/session-timeout",
                               "setSessionTimeout", 1,
                               new Class[] { Integer.TYPE });
        digester.addCallParam(prefix + "web-app/session-config/session-timeout", 0);

        digester.addCallMethod(prefix + "web-app/taglib",
                               "addTaglib", 2);
        digester.addCallParam(prefix + "web-app/taglib/taglib-location", 1);
        digester.addCallParam(prefix + "web-app/taglib/taglib-uri", 0);

        digester.addCallMethod(prefix + "web-app/welcome-file-list/welcome-file",
                               "addWelcomeFile", 0);

        digester.addCallMethod(prefix + "web-app/locale-encoding-mapping-list/locale-encoding-mapping",
                              "addLocaleEncodingMappingParameter", 2);
        digester.addCallParam(prefix + "web-app/locale-encoding-mapping-list/locale-encoding-mapping/locale", 0);
        digester.addCallParam(prefix + "web-app/locale-encoding-mapping-list/locale-encoding-mapping/encoding", 1);

    }

    protected void configureNamingRules(Digester digester) {
        //ejb-local-ref
        digester.addObjectCreate(prefix + "web-app/ejb-local-ref",
                                 "org.apache.catalina.deploy.ContextLocalEjb");
        digester.addRule(prefix + "web-app/ejb-local-ref",
                new SetNextNamingRule("addLocalEjb",
                            "org.apache.catalina.deploy.ContextLocalEjb"));

        digester.addCallMethod(prefix + "web-app/ejb-local-ref/description",
                               "setDescription", 0);
        digester.addCallMethod(prefix + "web-app/ejb-local-ref/ejb-link",
                               "setLink", 0);
        digester.addCallMethod(prefix + "web-app/ejb-local-ref/ejb-ref-name",
                               "setName", 0);
        digester.addCallMethod(prefix + "web-app/ejb-local-ref/ejb-ref-type",
                               "setType", 0);
        digester.addCallMethod(prefix + "web-app/ejb-local-ref/local",
                               "setLocal", 0);
        digester.addCallMethod(prefix + "web-app/ejb-local-ref/local-home",
                               "setHome", 0);
        configureInjectionRules(digester, "web-app/ejb-local-ref/");

        //ejb-ref
        digester.addObjectCreate(prefix + "web-app/ejb-ref",
                                 "org.apache.catalina.deploy.ContextEjb");
        digester.addRule(prefix + "web-app/ejb-ref",
                new SetNextNamingRule("addEjb",
                            "org.apache.catalina.deploy.ContextEjb"));

        digester.addCallMethod(prefix + "web-app/ejb-ref/description",
                               "setDescription", 0);
        digester.addCallMethod(prefix + "web-app/ejb-ref/ejb-link",
                               "setLink", 0);
        digester.addCallMethod(prefix + "web-app/ejb-ref/ejb-ref-name",
                               "setName", 0);
        digester.addCallMethod(prefix + "web-app/ejb-ref/ejb-ref-type",
                               "setType", 0);
        digester.addCallMethod(prefix + "web-app/ejb-ref/home",
                               "setHome", 0);
        digester.addCallMethod(prefix + "web-app/ejb-ref/remote",
                               "setRemote", 0);
        configureInjectionRules(digester, "web-app/ejb-ref/");

        //env-entry
        digester.addObjectCreate(prefix + "web-app/env-entry",
                                 "org.apache.catalina.deploy.ContextEnvironment");
        digester.addRule(prefix + "web-app/env-entry",
                new SetNextNamingRule("addEnvironment",
                            "org.apache.catalina.deploy.ContextEnvironment"));

        digester.addCallMethod(prefix + "web-app/env-entry/description",
                               "setDescription", 0);
        digester.addCallMethod(prefix + "web-app/env-entry/env-entry-name",
                               "setName", 0);
        digester.addCallMethod(prefix + "web-app/env-entry/env-entry-type",
                               "setType", 0);
        digester.addCallMethod(prefix + "web-app/env-entry/env-entry-value",
                               "setValue", 0);
        configureInjectionRules(digester, "web-app/env-entry/");

        //resource-env-ref
        digester.addObjectCreate(prefix + "web-app/resource-env-ref",
            "org.apache.catalina.deploy.ContextResourceEnvRef");
        digester.addRule(prefix + "web-app/resource-env-ref",
                    new SetNextNamingRule("addResourceEnvRef",
                        "org.apache.catalina.deploy.ContextResourceEnvRef"));

        digester.addCallMethod(prefix + "web-app/resource-env-ref/resource-env-ref-name",
                "setName", 0);
        digester.addCallMethod(prefix + "web-app/resource-env-ref/resource-env-ref-type",
                "setType", 0);
        configureInjectionRules(digester, "web-app/ejb-local-ref/");

        //message-destination
        digester.addObjectCreate(prefix + "web-app/message-destination",
                                 "org.apache.catalina.deploy.MessageDestination");
        digester.addSetNext(prefix + "web-app/message-destination",
                            "addMessageDestination",
                            "org.apache.catalina.deploy.MessageDestination");

        digester.addCallMethod(prefix + "web-app/message-destination/description",
                               "setDescription", 0);
        digester.addCallMethod(prefix + "web-app/message-destination/display-name",
                               "setDisplayName", 0);
        digester.addCallMethod(prefix + "web-app/message-destination/icon/large-icon",
                               "setLargeIcon", 0);
        digester.addCallMethod(prefix + "web-app/message-destination/icon/small-icon",
                               "setSmallIcon", 0);
        digester.addCallMethod(prefix + "web-app/message-destination/message-destination-name",
                               "setName", 0);

        //message-destination-ref
        digester.addObjectCreate(prefix + "web-app/message-destination-ref",
                                 "org.apache.catalina.deploy.MessageDestinationRef");
        digester.addSetNext(prefix + "web-app/message-destination-ref",
                            "addMessageDestinationRef",
                            "org.apache.catalina.deploy.MessageDestinationRef");

        digester.addCallMethod(prefix + "web-app/message-destination-ref/description",
                               "setDescription", 0);
        digester.addCallMethod(prefix + "web-app/message-destination-ref/message-destination-link",
                               "setLink", 0);
        digester.addCallMethod(prefix + "web-app/message-destination-ref/message-destination-ref-name",
                               "setName", 0);
        digester.addCallMethod(prefix + "web-app/message-destination-ref/message-destination-type",
                               "setType", 0);
        digester.addCallMethod(prefix + "web-app/message-destination-ref/message-destination-usage",
                               "setUsage", 0);

        configureInjectionRules(digester, "web-app/message-destination-ref/");

        //resource-ref
        digester.addObjectCreate(prefix + "web-app/resource-ref",
                                 "org.apache.catalina.deploy.ContextResource");
        digester.addRule(prefix + "web-app/resource-ref",
                new SetNextNamingRule("addResource",
                            "org.apache.catalina.deploy.ContextResource"));

        digester.addCallMethod(prefix + "web-app/resource-ref/description",
                               "setDescription", 0);
        digester.addCallMethod(prefix + "web-app/resource-ref/res-auth",
                               "setAuth", 0);
        digester.addCallMethod(prefix + "web-app/resource-ref/res-ref-name",
                               "setName", 0);
        digester.addCallMethod(prefix + "web-app/resource-ref/res-sharing-scope",
                               "setScope", 0);
        digester.addCallMethod(prefix + "web-app/resource-ref/res-type",
                               "setType", 0);
        configureInjectionRules(digester, "web-app/resource-ref/");

        //service-ref
        digester.addObjectCreate(prefix + "web-app/service-ref",
                                 "org.apache.catalina.deploy.ContextService");
        digester.addRule(prefix + "web-app/service-ref",
                         new SetNextNamingRule("addService",
                         "org.apache.catalina.deploy.ContextService"));

        digester.addCallMethod(prefix + "web-app/service-ref/description",
                               "setDescription", 0);
        digester.addCallMethod(prefix + "web-app/service-ref/display-name",
                               "setDisplayname", 0);
        digester.addCallMethod(prefix + "web-app/service-ref/icon",
                               "setIcon", 0);
        digester.addCallMethod(prefix + "web-app/service-ref/service-ref-name",
                               "setName", 0);
        digester.addCallMethod(prefix + "web-app/service-ref/service-interface",
                               "setType", 0);
        digester.addCallMethod(prefix + "web-app/service-ref/wsdl-file",
                               "setWsdlfile", 0);
        digester.addCallMethod(prefix + "web-app/service-ref/jaxrpc-mapping-file",
                               "setJaxrpcmappingfile", 0);
        digester.addRule(prefix + "web-app/service-ref/service-qname", new ServiceQnameRule());

        digester.addRule(prefix + "web-app/service-ref/port-component-ref",
                               new CallMethodMultiRule("addPortcomponent", 2, 1));
        digester.addCallParam(prefix + "web-app/service-ref/port-component-ref/service-endpoint-interface", 0);
        digester.addRule(prefix + "web-app/service-ref/port-component-ref/port-component-link", new CallParamMultiRule(1));

        digester.addObjectCreate(prefix + "web-app/service-ref/handler",
                                 "org.apache.catalina.deploy.ContextHandler");
        digester.addRule(prefix + "web-app/service-ref/handler",
                         new SetNextRule("addHandler",
                         "org.apache.catalina.deploy.ContextHandler"));

        digester.addCallMethod(prefix + "web-app/service-ref/handler/handler-name",
                               "setName", 0);
        digester.addCallMethod(prefix + "web-app/service-ref/handler/handler-class",
                               "setHandlerclass", 0);

        digester.addCallMethod(prefix + "web-app/service-ref/handler/init-param",
                               "setProperty", 2);
        digester.addCallParam(prefix + "web-app/service-ref/handler/init-param/param-name",
                              0);
        digester.addCallParam(prefix + "web-app/service-ref/handler/init-param/param-value",
                              1);

        digester.addRule(prefix + "web-app/service-ref/handler/soap-header", new SoapHeaderRule());

        digester.addCallMethod(prefix + "web-app/service-ref/handler/soap-role",
                               "addSoapRole", 0);
        digester.addCallMethod(prefix + "web-app/service-ref/handler/port-name",
                               "addPortName", 0);
        configureInjectionRules(digester, "web-app/service-ref/");


    }

    protected void configureInjectionRules(Digester digester, String base) {

        digester.addCallMethod(prefix + base + "injection-target", "addInjectionTarget", 2);
        digester.addCallParam(prefix + base + "injection-target/injection-target-class", 0);
        digester.addCallParam(prefix + base + "injection-target/injection-target-name", 1);

    }


    /**
     * Reset counter used for validating the web.xml file.
     */
    public void recycle(){
        jspConfig.isJspConfigSet = false;
        sessionConfig.isSessionConfigSet = false;
        loginConfig.isLoginConfigSet = false;
    }
}


// ----------------------------------------------------------- Private Classes


/**
 * Rule to check that the <code>login-config</code> is occuring 
 * only 1 time within the web.xml
 */
final class SetLoginConfig extends Rule {
    protected boolean isLoginConfigSet = false;
    public SetLoginConfig() {
    }

    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {
        if (isLoginConfigSet){
            throw new IllegalArgumentException(
            "<login-config> element is limited to 1 occurance");
        }
        isLoginConfigSet = true;
    }

}


/**
 * Rule to check that the <code>jsp-config</code> is occuring 
 * only 1 time within the web.xml
 */
final class SetJspConfig extends Rule {
    protected boolean isJspConfigSet = false;
    public SetJspConfig() {
    }

    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {
        if (isJspConfigSet){
            throw new IllegalArgumentException(
            "<jsp-config> element is limited to 1 occurance");
        }
        isJspConfigSet = true;
    }

}


/**
 * Rule to check that the <code>session-config</code> is occuring 
 * only 1 time within the web.xml
 */
final class SetSessionConfig extends Rule {
    protected boolean isSessionConfigSet = false;
    public SetSessionConfig() {
    }

    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {
        if (isSessionConfigSet){
            throw new IllegalArgumentException(
            "<session-config> element is limited to 1 occurance");
        }
        isSessionConfigSet = true;
    }

}

/**
 * A Rule that calls the <code>setAuthConstraint(true)</code> method of
 * the top item on the stack, which must be of type
 * <code>org.apache.catalina.deploy.SecurityConstraint</code>.
 */

final class SetAuthConstraintRule extends Rule {

    public SetAuthConstraintRule() {
    }

    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {
        SecurityConstraint securityConstraint =
            (SecurityConstraint) digester.peek();
        securityConstraint.setAuthConstraint(true);
        if (digester.getLogger().isDebugEnabled()) {
            digester.getLogger()
               .debug("Calling SecurityConstraint.setAuthConstraint(true)");
        }
    }

}


/**
 * Class that calls <code>setDistributable(true)</code> for the top object
 * on the stack, which must be a <code>org.apache.catalina.Context</code>.
 */

final class SetDistributableRule extends Rule {

    public SetDistributableRule() {
    }

    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {
        Context context = (Context) digester.peek();
        context.setDistributable(true);
        if (digester.getLogger().isDebugEnabled()) {
            digester.getLogger().debug
               (context.getClass().getName() + ".setDistributable( true)");
        }
    }

}


/**
 * Class that calls a property setter for the top object on the stack,
 * passing the public ID of the entity we are currently processing.
 */

final class SetPublicIdRule extends Rule {

    public SetPublicIdRule(String method) {
        this.method = method;
    }

    private String method = null;

    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {

        Context context = (Context) digester.peek(digester.getCount() - 1);
        Object top = digester.peek();
        Class paramClasses[] = new Class[1];
        paramClasses[0] = "String".getClass();
        String paramValues[] = new String[1];
        paramValues[0] = digester.getPublicId();

        Method m = null;
        try {
            m = top.getClass().getMethod(method, paramClasses);
        } catch (NoSuchMethodException e) {
            digester.getLogger().error("Can't find method " + method + " in "
                                       + top + " CLASS " + top.getClass());
            return;
        }

        m.invoke(top, (Object [])paramValues);
        if (digester.getLogger().isDebugEnabled())
            digester.getLogger().debug("" + top.getClass().getName() + "." 
                                       + method + "(" + paramValues[0] + ")");

    }

}


/**
 * A Rule that calls the factory method on the specified Context to
 * create the object that is to be added to the stack.
 */

final class WrapperCreateRule extends Rule {

    public WrapperCreateRule() {
    }

    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {
        Context context =
            (Context) digester.peek(digester.getCount() - 1);
        Wrapper wrapper = context.createWrapper();
        digester.push(wrapper);
        if (digester.getLogger().isDebugEnabled())
            digester.getLogger().debug("new " + wrapper.getClass().getName());
    }

    public void end(String namespace, String name)
        throws Exception {
        Wrapper wrapper = (Wrapper) digester.pop();
        if (digester.getLogger().isDebugEnabled())
            digester.getLogger().debug("pop " + wrapper.getClass().getName());
    }

}


/**
 * A Rule that can be used to call multiple times a method as many times as needed
 * (used for addServletMapping).
 */
final class CallParamMultiRule extends CallParamRule {

    public CallParamMultiRule(int paramIndex) {
        super(paramIndex);
    }

    public void end(String namespace, String name) {
        if (bodyTextStack != null && !bodyTextStack.empty()) {
            // what we do now is push one parameter onto the top set of parameters
            Object parameters[] = (Object[]) digester.peekParams();
            ArrayList params = (ArrayList) parameters[paramIndex];
            if (params == null) {
                params = new ArrayList();
                parameters[paramIndex] = params;
            }
            params.add(bodyTextStack.pop());
        }
    }

}


/**
 * A Rule that can be used to call multiple times a method as many times as needed
 * (used for addServletMapping).
 */
final class CallMethodMultiRule extends CallMethodRule {

    protected int multiParamIndex = 0;
    
    public CallMethodMultiRule(String methodName, int paramCount, int multiParamIndex) {
        super(methodName, paramCount);
        this.multiParamIndex = multiParamIndex;
    }

    public void end() throws Exception {

        // Retrieve or construct the parameter values array
        Object parameters[] = null;
        if (paramCount > 0) {
            parameters = (Object[]) digester.popParams();
        } else {
            super.end();
        }
        
        ArrayList multiParams = (ArrayList) parameters[multiParamIndex];
        
        // Construct the parameter values array we will need
        // We only do the conversion if the param value is a String and
        // the specified paramType is not String. 
        Object paramValues[] = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            if (i != multiParamIndex) {
                // convert nulls and convert stringy parameters 
                // for non-stringy param types
                if(parameters[i] == null || (parameters[i] instanceof String 
                        && !String.class.isAssignableFrom(paramTypes[i]))) {
                    paramValues[i] =
                        IntrospectionUtils.convert((String) parameters[i], paramTypes[i]);
                } else {
                    paramValues[i] = parameters[i];
                }
            }
        }

        // Determine the target object for the method call
        Object target;
        if (targetOffset >= 0) {
            target = digester.peek(targetOffset);
        } else {
            target = digester.peek(digester.getCount() + targetOffset);
        }

        if (target == null) {
            StringBuffer sb = new StringBuffer();
            sb.append("[CallMethodRule]{");
            sb.append("");
            sb.append("} Call target is null (");
            sb.append("targetOffset=");
            sb.append(targetOffset);
            sb.append(",stackdepth=");
            sb.append(digester.getCount());
            sb.append(")");
            throw new org.xml.sax.SAXException(sb.toString());
        }
        
        if (multiParams == null) {
            paramValues[multiParamIndex] = null;
            Object result = IntrospectionUtils.callMethodN(target, methodName,
                    paramValues, paramTypes);   
            return;
        }
        
        for (int j = 0; j < multiParams.size(); j++) {
            Object param = multiParams.get(j);
            if(param == null || (param instanceof String 
                    && !String.class.isAssignableFrom(paramTypes[multiParamIndex]))) {
                paramValues[multiParamIndex] =
                    IntrospectionUtils.convert((String) param, paramTypes[multiParamIndex]);
            } else {
                paramValues[multiParamIndex] = param;
            }
            Object result = IntrospectionUtils.callMethodN(target, methodName,
                    paramValues, paramTypes);   
        }
        
    }

}



/**
 * A Rule that check if the annotations have to be loaded.
 * 
 */

final class IgnoreAnnotationsRule extends Rule {

    public IgnoreAnnotationsRule() {
    }

    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {
        Context context = (Context) digester.peek(digester.getCount() - 1);
        String value = attributes.getValue("metadata-complete");
        if ("true".equals(value)) {
            context.setIgnoreAnnotations(true);
        }
        if (digester.getLogger().isDebugEnabled()) {
            digester.getLogger().debug
                (context.getClass().getName() + ".setIgnoreAnnotations( " +
                    context.getIgnoreAnnotations() + ")");
        }
    }

}

/**
 * A Rule that sets soap headers on the ContextHandler.
 * 
 */
final class SoapHeaderRule extends Rule {

    public SoapHeaderRule() {
    }

    public void body(String text)
        throws Exception {
        String namespaceuri = null;
        String localpart = text;
        int colon = text.indexOf(':');
        if (colon >= 0) {
            String prefix = text.substring(0,colon);
            namespaceuri = digester.findNamespaceURI(prefix);
            localpart = text.substring(colon+1);
        }
        ContextHandler contextHandler = (ContextHandler)digester.peek();
        contextHandler.addSoapHeaders(localpart,namespaceuri);
    }
}

/**
 * A Rule that sets service qname on the ContextService.
 * 
 */
final class ServiceQnameRule extends Rule {

    public ServiceQnameRule() {
    }

    public void body(String text)
        throws Exception {
        String namespaceuri = null;
        String localpart = text;
        int colon = text.indexOf(':');
        if (colon >= 0) {
            String prefix = text.substring(0,colon);
            namespaceuri = digester.findNamespaceURI(prefix);
            localpart = text.substring(colon+1);
        }
        ContextService contextService = (ContextService)digester.peek();
        contextService.setServiceqnameLocalpart(localpart);
        contextService.setServiceqnameNamespaceURI(namespaceuri);
    }
}

