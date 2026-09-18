/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.manager2;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serial;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.Cluster;
import org.apache.catalina.Container;
import org.apache.catalina.ContainerServlet;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Executor;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Loader;
import org.apache.catalina.Manager;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Realm;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.SessionIdGenerator;
import org.apache.catalina.Valve;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.core.StandardThreadExecutor;
import org.apache.catalina.core.StandardWrapper;
import org.apache.catalina.deploy.NamingResourcesImpl;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.ha.ClusterDeployer;
import org.apache.catalina.ha.ClusterListener;
import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.ClusterValve;
import org.apache.catalina.ha.tcp.SimpleTcpCluster;
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.realm.CombinedRealm;
import org.apache.catalina.realm.MemoryRealm;
import org.apache.catalina.realm.RealmBase;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.startup.Bootstrap;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.HostConfig;
import org.apache.catalina.storeconfig.IStoreFactory;
import org.apache.catalina.storeconfig.StandardContextSF;
import org.apache.catalina.storeconfig.StoreConfig;
import org.apache.catalina.storeconfig.StoreDescription;
import org.apache.catalina.storeconfig.StoreFileMover;
import org.apache.catalina.storeconfig.StoreLoader;
import org.apache.catalina.storeconfig.XMLFormatPreserver;
import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelInterceptor;
import org.apache.catalina.tribes.ChannelReceiver;
import org.apache.catalina.tribes.ChannelSender;
import org.apache.catalina.tribes.ManagedChannel;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipService;
import org.apache.catalina.tribes.group.GroupChannel;
import org.apache.catalina.tribes.group.interceptors.MessageDispatchInterceptor;
import org.apache.catalina.tribes.group.interceptors.StaticMembershipInterceptor;
import org.apache.catalina.tribes.group.interceptors.TcpFailureDetector;
import org.apache.catalina.tribes.membership.McastService;
import org.apache.catalina.tribes.membership.StaticMember;
import org.apache.catalina.tribes.membership.StaticMembershipService;
import org.apache.catalina.tribes.transport.AbstractSender;
import org.apache.catalina.tribes.transport.MultiPointSender;
import org.apache.catalina.tribes.transport.ReceiverBase;
import org.apache.catalina.tribes.transport.ReplicationTransmitter;
import org.apache.catalina.util.ContextName;
import org.apache.catalina.util.LifecycleBase;
import org.apache.catalina.util.SessionIdGeneratorBase;
import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.UpgradeProtocol;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.coyote.http2.Http2Protocol;
import org.apache.tomcat.util.descriptor.web.ContextEjb;
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.ContextLocalEjb;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.apache.tomcat.util.descriptor.web.ContextResourceEnvRef;
import org.apache.tomcat.util.descriptor.web.ContextResourceLink;
import org.apache.tomcat.util.descriptor.web.ContextService;
import org.apache.tomcat.util.descriptor.web.ResourceBase;
import org.apache.tomcat.util.http.CookieProcessor;
import org.apache.tomcat.util.http.CookieProcessorBase;
import org.apache.tomcat.util.json.JSONParser;
import org.apache.tomcat.util.modeler.AttributeInfo;
import org.apache.tomcat.util.modeler.ManagedBean;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;


/**
 * The Manager2 configuration API. Exposes the complete component tree of the Catalina {@code Server} (services,
 * engines, hosts, contexts, wrappers, valves, connectors, executors, listeners, host aliases, realms, TLS host
 * configurations, the upgrade protocols of a connector, the context sub components manager, session id generator,
 * resources, loader and cookie processor, and the JNDI naming resources of the server and of the contexts), allows
 * reading and updating the descriptor defined attributes of any component, adding and removing child components,
 * starting, stopping and restarting any component that implements {@code Lifecycle}, and persisting the live state to
 * {@code conf/server.xml} through the storeconfig mechanism.
 * <p>
 * Changes are applied to the running server immediately. Persisting (store) rewrites {@code conf/server.xml} from the
 * live state and keeps a timestamped backup of the previous file.
 * <p>
 * <b>Lifecycle operations.</b> Not every change takes effect until the affected component is restarted (which
 * attributes apply live is not documented), so the API offers explicit lifecycle operations: {@code start}, {@code
 * stop} and {@code restart} (a stop followed by a start) for every component that implements {@code Lifecycle}. A
 * {@code start} or {@code stop} of a component that would interrupt access to this web application (the server itself,
 * the service, engine, host or context that route it, the connector that serves it and the wrappers of the context it
 * runs in) is refused (403 {@code SELF_COMPONENT}): the request would lose its way back. A {@code restart} of such a
 * component is still allowed: the client's connection may be interrupted during the operation, but the component is
 * running again at the end and the client reconnects (re-logging in when the admin session was reset by the restart).
 * <p>
 * <b>Contexts keep their storage location</b> when the configuration is persisted, mirroring regular StoreConfig
 * behavior: a context that is backed by its own configuration file (its {@code META-INF/context.xml} or
 * {@code conf/Catalina/.../context.xml}) is written back to that file, and a context defined inline in
 * {@code server.xml} stays inline. A context restarts when its own file is (re)written, so a save that rewrites the
 * file of this web application restarts the manager and resets the admin session; the UI warns about this before the
 * save. The store preview is read-only: it reports the resulting {@code server.xml} and the external context files that
 * would be rewritten, without touching the configuration on disk.
 * <p>
 * <b>Node addressing.</b> Every node of the tree carries a path based {@code id} made of named segments (URL encoded,
 * where a {@code /} inside a value - e.g. in a context path - is written as {@code +}) and, for components that have no
 * name (valves, connectors, listeners), a positional index:
 * 
 * <pre>
 *   server
 *   server/service/{name}
 *   server/service/{s}/engine/{name}
 *   server/service/{s}/engine/{e}/host/{name}
 *   server/service/{s}/engine/{e}/host/{h}/alias/{alias}
 *   server/service/{s}/engine/{e}/host/{h}/context/{path}
 *   server/service/{s}/engine/{e}/host/{h}/context/{p}/wrapper/{name}
 *   server/service/{s}/engine/{e}/host/{h}/context/{p}/valve/{index}
 *   server/service/{s}/engine/{e}/host/{h}/context/{p}/manager/0
 *   server/service/{s}/engine/{e}/host/{h}/context/{p}/manager/0/sessionIdGenerator/0
 *   server/service/{s}/engine/{e}/host/{h}/context/{p}/resources/0
 *   server/service/{s}/engine/{e}/host/{h}/context/{p}/loader/0
 *   server/service/{s}/engine/{e}/host/{h}/context/{p}/cookieProcessor/0
 *   server/service/{s}/engine/{e}/host/{h}/context/{p}/namingResources/0
 *   server/namingResources/0
 *   server/namingResources/0/resource/{name}
 *   server/namingResources/0/resourceLink/{name}
 *   server/namingResources/0/resourceEnvRef/{name}
 *   server/namingResources/0/environment/{name}
 *   server/namingResources/0/ejb/{name}
 *   server/namingResources/0/localEjb/{name}
 *   server/namingResources/0/serviceRef/{name}
 *   server/service/{s}/engine/{e}/host/{h}/context/{p}/namingResources/0/resource/{name}
 *   ...
 *   server/service/{s}/engine/{e}/host/{h}/valve/{index}
 *   server/service/{s}/engine/{e}/valve/{index}
 *   server/service/{s}/engine/{e}/listener/{index}
 *   server/service/{s}/connector/{index}
 *   server/service/{s}/connector/{i}/sslHostConfig/{hostName}
 *   server/service/{s}/connector/{i}/sslHostConfig/{h}/certificate/{index}
 *   server/service/{s}/connector/{i}/upgradeProtocol/{index}
 *   server/service/{s}/executor/{name}
 *   server/service/{s}/valve/{index}
 *   server/service/{s}/listener/{index}
 *   server/valve/{index}
 *   server/listener/{index}
 * </pre>
 * 
 * The tree endpoint generates these ids; clients only echo them back.
 * <p>
 * <b>Attributes.</b> The property list of a node is derived from the modeler MBean descriptor of the component's class
 * (the same contract that defines the {@code Catalina:*} MBeans). The TLS components ({@code SSLHostConfig} and
 * {@code SSLHostConfigCertificate}), the context sub components ({@code WebappLoader}, {@code CookieProcessorBase}
 * subclasses and {@code SessionIdGeneratorBase} subclasses), the HTTP/2 upgrade protocol
 * ({@code org.apache.coyote.http2.Http2Protocol}) and the JNDI entry nodes have no (complete) modeler descriptor; their
 * editable attribute list is defined explicitly by this servlet. Only attributes that map to a simple UI type (boolean,
 * integral, string, string array) are editable; everything else is reported read-only.
 * <p>
 * <b>Context sub components.</b> A running context always has exactly one manager, one resource root, one loader and
 * one cookie processor (the defaults are created at context start). Adding one of these to a context therefore replaces
 * the current instance with a new instance of the given class; the current instance is stopped first (where it has a
 * lifecycle). These components are required by the context and cannot be removed - only replaced. Replacing the
 * resources of a running context is refused (the context must be stopped first). The manager's session id generator is
 * replaced the same way.
 * <p>
 * <b>TLS.</b> Adding an {@code sslHostConfig} to a connector enables TLS on that connector. The TLS state of a running
 * connector only takes effect when the connector is restarted, so the operations that change it (add or remove an
 * {@code sslHostConfig}, add or remove a {@code certificate}) restart the affected connector and roll back the change
 * if the restart fails (for example because the keystore does not exist or the password is wrong). The connector that
 * hosts this web application itself is never touched.
 * <p>
 * <b>Upgrade protocols.</b> A connector whose protocol handler is the HTTP/1.1 variant can carry upgrade protocols (the
 * {@code UpgradeProtocol} interface; the only implementation shipped with Tomcat is the HTTP/2 one,
 * {@code org.apache.coyote.http2.Http2Protocol}). An upgrade protocol is only referenced when the connector is
 * initialised, so adding one does not change a running connector: it becomes active the next time the connector is
 * (re)started (see the lifecycle operations). No live activation is attempted.
 * <p>
 * <b>JNDI naming resources.</b> The server and every context have a {@code namingResources} node (a
 * {@code NamingResourcesImpl}) that holds the JNDI entries: {@code resource}, {@code resourceLink},
 * {@code resourceEnvRef}, {@code environment}, {@code ejb}, {@code localEjb} and {@code service}. A server level node
 * does not accept {@code resourceLink} entries (they only exist in the context JNDI environment and are not parsed from
 * {@code <GlobalNamingResources>}). Adding and removing an entry updates the live JNDI environment of the server (the
 * global context) or of the context ({@code java:comp/env}) immediately; the container's {@code NamingContextListener}
 * performs the JNDI bind/unbind on the property change event. Updating an attribute of an existing entry does not fire
 * such an event, so the update is implemented as a remove plus re-add of the entry (the change is rolled back if the
 * re-add fails). The factory specific options of a {@code resource} (e.g. {@code url}, {@code driverClassName},
 * {@code maxTotal}) are free form string parameters; for the first party JNDI {@code ObjectFactory} implementations
 * shipped with Tomcat ({@code BasicDataSourceFactory}, {@code MemoryUserDatabaseFactory},
 * {@code DataSourceUserDatabaseFactory} and the per user / shared pool data source factories) the parameters they
 * actually consume are listed in the node's property set, with validation.
 * <p>
 * Like the rest of the mutable API, this API requires the {@code manager-gui} role and, for the mutating methods, a
 * valid CSRF token.
 */
public class ConfigApiServlet extends HttpServlet implements ContainerServlet {


    @Serial
    private static final long serialVersionUID = 1L;


    /**
     * A component name that may be used for new hosts, wrappers, executors and services (and that is accepted for
     * engines).
     */
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9._-]+");

    /**
     * The attribute types that can be edited through the API.
     */
    private static final Set<String> EDITABLE_TYPES = Set.of("boolean", "int", "long", "short", "byte", "float",
            "double", "java.lang.String", "[Ljava.lang.String;");

    /**
     * Attribute names whose update requires a type to confirm (renames and the like).
     */
    private static final Set<String> RISKY_ATTRIBUTES = Set.of("name", "path", "defaultHost");

    /**
     * The cluster sub component types. They are excluded from the generic "accepts a lifecycle listener" flag: only the
     * cluster itself (not its channel, manager, ...) accepts a {@code <Listener>} in the cluster model, even when the
     * sub component is itself a lifecycle.
     */
    private static final Set<String> CLUSTER_SUB_TYPES = Set.of("channel", "membership", "sender", "receiver",
            "transport", "interceptor", "deployer", "clusterManager", "clusterValve", "clusterListener", "member");

    /**
     * The maximum number of nested Realm levels, the same bound the XML parser applies to Realm elements
     * (org.apache.catalina.startup.RealmRuleSet).
     */
    private static final int MAX_NESTED_REALM_LEVELS = 3;


    private transient Wrapper wrapper = null;

    private transient Context selfContext = null;

    private transient Host selfHost = null;

    private transient Server server = null;


    // ------------------------------------------------ ContainerServlet API


    @Override
    public Wrapper getWrapper() {
        return wrapper;
    }


    @Override
    public void setWrapper(Wrapper wrapper) {
        this.wrapper = wrapper;
        if (wrapper == null) {
            selfContext = null;
            selfHost = null;
            server = null;
        } else {
            selfContext = (Context) wrapper.getParent();
            selfHost = (Host) selfContext.getParent();
            Engine engine = (Engine) selfHost.getParent();
            Service service = engine.getService();
            server = (service != null) ? service.getServer() : null;
        }
    }


    // ------------------------------------------------------------ Request API


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String path = path(request);

        try {
            requireServer();
            if ("/api/config/tree".equals(path)) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("tree", build(server, "server", "server"));
                Api.json(response, payload);
            } else if (path.startsWith("/api/config/node/")) {
                NodeRef ref = resolve(path.substring("/api/config/node/".length()));
                Api.json(response, node(ref));
            } else if ("/api/config/store/preview".equals(path)) {
                preview(response);
            } else {
                Api.notFound(response);
            }
        } catch (ConfigException e) {
            Api.error(response, e.status, e.code, e.getMessage());
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.config"), e);
            throw new ServletException(e);
        }
    }


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String path = path(request);
        Map<String, Object> body = readJson(request);

        try {
            requireServer();
            if ("/api/config/attribute".equals(path)) {
                updateAttribute(response, body);
            } else if ("/api/config/child".equals(path)) {
                addChild(response, body);
            } else if ("/api/config/store".equals(path)) {
                store(response);
            } else if ("/api/config/lifecycle".equals(path)) {
                lifecycle(response, body);
            } else {
                Api.notFound(response);
            }
        } catch (ConfigException e) {
            Api.error(response, e.status, e.code, e.getMessage());
        } catch (IllegalArgumentException e) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_JSON", e.getMessage());
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.config"), e);
            throw new ServletException(e);
        }
    }


    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String path = path(request);

        try {
            requireServer();
            if ("/api/config/child".equals(path)) {
                Map<String, Object> body = readJson(request);
                removeChild(response, body);
            } else {
                Api.notFound(response);
            }
        } catch (ConfigException e) {
            Api.error(response, e.status, e.code, e.getMessage());
        } catch (IllegalArgumentException e) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_JSON", e.getMessage());
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.config"), e);
            throw new ServletException(e);
        }
    }


    // ------------------------------------------------------- Tree building


    /**
     * Build the tree entry of one component, including its children.
     */
    private Map<String, Object> build(Object component, String id, String type) {

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", type);
        node.put("className", component.getClass().getName());
        node.put("name", displayName(component, type));
        if (component instanceof Lifecycle lifecycle) {
            node.put("state", lifecycle.getState().toString());
        }
        if ("context".equals(type) && component == selfContext) {
            node.put("self", Boolean.TRUE);
        }
        if ("namingResources".equals(type) && component instanceof NamingResourcesImpl namingResources) {
            // The server level node (container is the Server) does not
            // accept resourceLink entries; the client filters them out.
            node.put("global", Boolean.valueOf(namingResources.getContainer() instanceof Server));
        }

        List<Map<String, Object>> children = new ArrayList<>();
        if (component instanceof StandardServer s) {
            children.add(build(s.getGlobalNamingResources(), id + "/namingResources/0", "namingResources"));
            for (Service service : s.findServices()) {
                children.add(build(service, id + "/service/" + enc(service.getName()), "service"));
            }
            children.addAll(childrenOfListeners(s, id));
        } else if (component instanceof StandardService service) {
            Engine engine = service.getContainer();
            if (engine != null) {
                children.add(build(engine, id + "/engine/" + enc(engine.getName()), "engine"));
            }
            children.addAll(childrenOfConnectors(service, id));
            children.addAll(childrenOfExecutors(service, id));
            children.addAll(childrenOfListeners(service, id));
        } else if (component instanceof StandardEngine engine) {
            for (Container host : engine.findChildren()) {
                if (host instanceof Host) {
                    children.add(build(host, id + "/host/" + enc(host.getName()), "host"));
                }
            }
            children.addAll(childrenOfRealm(engine, id));
            children.addAll(childrenOfCluster(engine, id));
            children.addAll(childrenOfValves(engine, id));
            children.addAll(childrenOfListeners(engine, id));
        } else if (component instanceof Host host) {
            for (String alias : host.findAliases()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", id + "/alias/" + enc(alias));
                entry.put("type", "alias");
                entry.put("className", String.class.getName());
                entry.put("name", alias);
                entry.put("children", new ArrayList<Map<String, Object>>());
                children.add(entry);
            }
            for (Container child : host.findChildren()) {
                if (child instanceof Context context) {
                    children.add(build(context, id + "/context/" + encContextPath(context.getPath()), "context"));
                }
            }
            children.addAll(childrenOfRealm(host, id));
            children.addAll(childrenOfCluster(host, id));
            children.addAll(childrenOfValves(host, id));
            children.addAll(childrenOfListeners((LifecycleBase) host, id));
        } else if (component instanceof Context context) {
            for (Container child : context.findChildren()) {
                if (child instanceof Wrapper wrapper) {
                    children.add(build(wrapper, id + "/wrapper/" + enc(wrapper.getName()), "wrapper"));
                }
            }
            children.addAll(childrenOfRealm(context, id));
            children.addAll(childrenOfCluster(context, id));
            children.addAll(childrenOfContextComponents(context, id));
            children.add(build(context.getNamingResources(), id + "/namingResources/0", "namingResources"));
            children.addAll(childrenOfValves(context, id));
            children.addAll(childrenOfListeners((LifecycleBase) context, id));
        } else if (component instanceof Connector connector) {
            children.addAll(sslHostConfigChildren(connector, id));
            children.addAll(upgradeProtocolChildren(connector, id));
        } else if (component instanceof SSLHostConfig sslHostConfig) {
            children.addAll(certificateChildren(sslHostConfig, id));
        } else if (component instanceof CatalinaCluster cluster) {
            children.addAll(clusterChildren(cluster, id));
        } else if (component instanceof Channel channel) {
            if (channel instanceof ManagedChannel managed) {
                children.addAll(channelChildren(managed, id));
            }
        } else if (component instanceof ChannelSender sender) {
            children.addAll(senderChildren(sender, id));
        } else if (component instanceof MembershipService membership) {
            children.addAll(membershipChildren(membership, id));
        } else if (component instanceof StaticMembershipInterceptor interceptor) {
            children.addAll(interceptorChildren(interceptor, id));
        } else if (component instanceof Realm realm) {
            children.addAll(childrenOfSubRealms(realm, id));
        } else if (component instanceof Manager manager) {
            children.addAll(sessionIdGeneratorChildren(manager, id));
        } else if (component instanceof NamingResourcesImpl namingResources) {
            children.addAll(childrenOfNamingResources(namingResources, id));
        }
        node.put("children", children);
        return node;
    }


    /**
     * The tree entries of the TLS (SSL) host configurations of a connector, each with its certificate children.
     */
    private List<Map<String, Object>> sslHostConfigChildren(Connector connector, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        SSLHostConfig[] hostConfigs = connector.findSslHostConfigs();
        if (hostConfigs == null || hostConfigs.length == 0) {
            return result;
        }
        // The configurations are held in a hash map; sort them by host
        // name so that the tree (and the order in which they are
        // displayed) is stable across requests and restarts.
        SSLHostConfig[] sorted = hostConfigs.clone();
        Arrays.sort(sorted, (a, b) -> a.getHostName().compareTo(b.getHostName()));
        for (SSLHostConfig hostConfig : sorted) {
            Map<String, Object> entry = new LinkedHashMap<>();
            String id = parentId + "/sslHostConfig/" + enc(hostConfig.getHostName());
            entry.put("id", id);
            entry.put("type", "sslHostConfig");
            entry.put("className", hostConfig.getClass().getName());
            entry.put("name", hostConfig.getHostName());
            entry.put("children", certificateChildren(hostConfig, id));
            result.add(entry);
        }
        return result;
    }


    /**
     * The tree entries of the certificates of an SSL host configuration.
     */
    private List<Map<String, Object>> certificateChildren(SSLHostConfig hostConfig, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        SSLHostConfigCertificate[] certificates = hostConfig.getCertificates().toArray(new SSLHostConfigCertificate[0]);
        for (int i = 0; i < certificates.length; i++) {
            SSLHostConfigCertificate certificate = certificates[i];
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", parentId + "/certificate/" + i);
            entry.put("type", "certificate");
            entry.put("className", certificate.getClass().getName());
            entry.put("name", certificateLabel(certificate));
            entry.put("children", new ArrayList<Map<String, Object>>());
            result.add(entry);
        }
        return result;
    }


    /**
     * A human readable name for a certificate configuration: the certificate type, with the (typeless) default
     * certificate shown as "default".
     */
    private static String certificateLabel(SSLHostConfigCertificate certificate) {
        if (certificate.getType() == SSLHostConfigCertificate.Type.UNDEFINED) {
            return "default";
        }
        return certificate.getType().name();
    }


    /**
     * The tree entries of the upgrade protocols of a connector, addressed by a positional index in the order they were
     * added.
     */
    private List<Map<String, Object>> upgradeProtocolChildren(Connector connector, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        UpgradeProtocol[] upgradeProtocols = connector.findUpgradeProtocols();
        for (int i = 0; i < upgradeProtocols.length; i++) {
            UpgradeProtocol upgradeProtocol = upgradeProtocols[i];
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", parentId + "/upgradeProtocol/" + i);
            entry.put("type", "upgradeProtocol");
            entry.put("className", upgradeProtocol.getClass().getName());
            entry.put("name", upgradeProtocolLabel(upgradeProtocol));
            entry.put("children", new ArrayList<Map<String, Object>>());
            result.add(entry);
        }
        return result;
    }


    /**
     * A human readable name for an upgrade protocol: the ALPN name (e.g. h2), then the HTTP upgrade name (e.g. h2c),
     * then the simple name of the class.
     */
    private static String upgradeProtocolLabel(UpgradeProtocol upgradeProtocol) {
        String label = upgradeProtocol.getAlpnName();
        if (label == null || label.isEmpty()) {
            label = upgradeProtocol.getHttpUpgradeName(false);
        }
        if (label == null || label.isEmpty()) {
            label = upgradeProtocol.getHttpUpgradeName(true);
        }
        if (label == null || label.isEmpty()) {
            label = upgradeProtocol.getClass().getSimpleName();
        }
        return label;
    }


    private List<Map<String, Object>> childrenOfConnectors(StandardService service, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        Connector[] connectors = service.findConnectors();
        for (int i = 0; i < connectors.length; i++) {
            Connector connector = connectors[i];
            String id = parentId + "/connector/" + i;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", id);
            entry.put("type", "connector");
            entry.put("className", connector.getClass().getName());
            entry.put("name", connectorLabel(connector));
            entry.put("state", connector.getState().toString());
            List<Map<String, Object>> connectorChildren = sslHostConfigChildren(connector, id);
            connectorChildren.addAll(upgradeProtocolChildren(connector, id));
            entry.put("children", connectorChildren);
            result.add(entry);
        }
        return result;
    }


    /**
     * A human readable name for a connector: the configured protocol when known, otherwise the simple name of the
     * protocol handler class.
     */
    private static String connectorLabel(Connector connector) {
        String protocol = connector.getProtocol();
        if (protocol == null || protocol.isEmpty()) {
            String handler = connector.getProtocolHandlerClassName();
            int separator = handler != null ? handler.lastIndexOf('.') : -1;
            protocol = (separator >= 0) ? handler.substring(separator + 1) : "connector";
        }
        return protocol + " (port " + connector.getPort() + ")";
    }


    private List<Map<String, Object>> childrenOfExecutors(StandardService service, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Executor executor : service.findExecutors()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", parentId + "/executor/" + enc(executor.getName()));
            entry.put("type", "executor");
            entry.put("className", executor.getClass().getName());
            entry.put("name", executor.getName());
            entry.put("state", executor.getState().toString());
            entry.put("children", new ArrayList<Map<String, Object>>());
            result.add(entry);
        }
        return result;
    }


    private List<Map<String, Object>> childrenOfValves(Container container, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        Valve[] valves = container.getPipeline().getValves();
        for (int i = 0; i < valves.length; i++) {
            Valve valve = valves[i];
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", parentId + "/valve/" + i);
            entry.put("type", "valve");
            entry.put("className", valve.getClass().getName());
            entry.put("name", valve.getClass().getSimpleName());
            entry.put("basic", Boolean.valueOf(i == 0));
            entry.put("children", new ArrayList<Map<String, Object>>());
            result.add(entry);
        }
        return result;
    }


    private List<Map<String, Object>> childrenOfListeners(LifecycleBase component, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        LifecycleListener[] listeners = component.findLifecycleListeners();
        for (int i = 0; i < listeners.length; i++) {
            LifecycleListener listener = listeners[i];
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", parentId + "/listener/" + i);
            entry.put("type", "listener");
            entry.put("className", listener.getClass().getName());
            entry.put("name", listener.getClass().getSimpleName());
            entry.put("children", new ArrayList<Map<String, Object>>());
            result.add(entry);
        }
        return result;
    }


    /**
     * The realm directly attached to the container, or {@code null} when the container has none of its own.
     * {@code getRealm()} falls back to the parent container's realm, so the fallback (the same comparison storeconfig
     * uses) must be excluded.
     */
    private static Realm ownRealm(Container container) {
        Realm realm = container.getRealm();
        if (realm == null) {
            return null;
        }
        Container parent = container.getParent();
        if (parent != null && realm == parent.getRealm()) {
            return null;
        }
        return realm;
    }


    /**
     * The realm the container resolves to (via {@code getRealm()}) once its own realm has been detached.
     */
    private static Realm fallbackRealm(Container container) {
        Container parent = container.getParent();
        return parent != null ? parent.getRealm() : null;
    }


    /**
     * The tree entry for the realm directly attached to the container, if it has one.
     */
    private List<Map<String, Object>> childrenOfRealm(Container container, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        Realm realm = ownRealm(container);
        if (realm != null) {
            result.add(realmEntry(realm, parentId + "/realm/0"));
        }
        return result;
    }


    /**
     * The tree entries of the (sub) realms nested in the given realm. Only combined realms hold sub realms.
     */
    private List<Map<String, Object>> childrenOfSubRealms(Realm realm, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(realm instanceof CombinedRealm combined)) {
            return result;
        }
        Realm[] nested = combined.getNestedRealms();
        for (int i = 0; i < nested.length; i++) {
            result.add(realmEntry(nested[i], parentId + "/realm/" + i));
        }
        return result;
    }


    private Map<String, Object> realmEntry(Realm realm, String id) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("type", "realm");
        entry.put("className", realm.getClass().getName());
        entry.put("name", realm.getClass().getSimpleName());
        if (realm instanceof Lifecycle lifecycle) {
            entry.put("state", lifecycle.getState().toString());
        }
        entry.put("children", childrenOfSubRealms(realm, id));
        return entry;
    }


    /**
     * The tree entries of the sub components of a context (manager, resources, loader, cookie processor). A running
     * context always has all of them (the defaults are created at context start), so they are always shown once the
     * context has started.
     */
    private List<Map<String, Object>> childrenOfContextComponents(Context context, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        Manager manager = context.getManager();
        if (manager != null) {
            result.add(build(manager, parentId + "/manager/0", "manager"));
        }
        WebResourceRoot resources = context.getResources();
        if (resources != null) {
            result.add(build(resources, parentId + "/resources/0", "resources"));
        }
        Loader loader = context.getLoader();
        if (loader != null) {
            result.add(build(loader, parentId + "/loader/0", "loader"));
        }
        CookieProcessor cookieProcessor = context.getCookieProcessor();
        if (cookieProcessor != null) {
            result.add(build(cookieProcessor, parentId + "/cookieProcessor/0", "cookieProcessor"));
        }
        return result;
    }


    /**
     * The tree entry of the session id generator of a manager, if it has one (a running manager always does; the
     * manager creates a default one at start).
     */
    private List<Map<String, Object>> sessionIdGeneratorChildren(Manager manager, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        SessionIdGenerator generator = manager.getSessionIdGenerator();
        if (generator != null) {
            result.add(build(generator, parentId + "/sessionIdGenerator/0", "sessionIdGenerator"));
        }
        return result;
    }


    /**
     * The tree entries of the JNDI entries of a {@code NamingResourcesImpl}, keyed by their JNDI names. A server level
     * instance (container is the {@code Server}) does not expose resource links: they are only part of a context JNDI
     * environment and are not parsed from {@code <GlobalNamingResources>}.
     */
    private List<Map<String, Object>> childrenOfNamingResources(NamingResourcesImpl namingResources, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        boolean global = namingResources.getContainer() instanceof Server;
        for (ContextResource resource : namingResources.findResources()) {
            result.add(namingEntry(resource, "resource", parentId));
        }
        if (!global) {
            for (ContextResourceLink link : namingResources.findResourceLinks()) {
                result.add(namingEntry(link, "resourceLink", parentId));
            }
        }
        for (ContextResourceEnvRef resourceEnvRef : namingResources.findResourceEnvRefs()) {
            result.add(namingEntry(resourceEnvRef, "resourceEnvRef", parentId));
        }
        for (ContextEnvironment environment : namingResources.findEnvironments()) {
            result.add(namingEntry(environment, "environment", parentId));
        }
        for (ContextEjb ejb : namingResources.findEjbs()) {
            result.add(namingEntry(ejb, "ejb", parentId));
        }
        for (ContextLocalEjb localEjb : namingResources.findLocalEjbs()) {
            result.add(namingEntry(localEjb, "localEjb", parentId));
        }
        for (ContextService service : namingResources.findServices()) {
            result.add(namingEntry(service, "serviceRef", parentId));
        }
        return result;
    }


    private Map<String, Object> namingEntry(ResourceBase entry, String type, String parentId) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", parentId + "/" + type + "/" + enc(entry.getName()));
        node.put("type", type);
        node.put("className", entry.getClass().getName());
        node.put("name", entry.getName());
        node.put("children", new ArrayList<Map<String, Object>>());
        return node;
    }


    // ------------------------------------------------------ Cluster children


    /**
     * The cluster directly attached to the container, or {@code null} when the container has none of its own.
     * {@code getCluster()} falls back to the parent container's cluster, so the fallback (the same comparison
     * storeconfig uses) must be excluded.
     */
    private static Cluster ownCluster(Container container) {
        Cluster cluster = container.getCluster();
        if (cluster == null) {
            return null;
        }
        Container parent = container.getParent();
        if (parent != null && cluster == parent.getCluster()) {
            return null;
        }
        return cluster;
    }


    /**
     * The tree entry for the cluster directly attached to the container, if it has one.
     */
    private List<Map<String, Object>> childrenOfCluster(Container container, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        Cluster cluster = ownCluster(container);
        if (cluster != null) {
            result.add(build(cluster, parentId + "/cluster/0", "cluster"));
        }
        return result;
    }


    /**
     * The tree children of a cluster: channel, deployer, valves, manager template (SimpleTcpCluster), lifecycle
     * listeners and cluster listeners.
     */
    private List<Map<String, Object>> clusterChildren(CatalinaCluster cluster, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        Channel channel = cluster.getChannel();
        if (channel != null) {
            result.add(build(channel, parentId + "/channel/0", "channel"));
        }
        ClusterDeployer deployer = cluster.getClusterDeployer();
        if (deployer != null) {
            result.add(build(deployer, parentId + "/deployer/0", "deployer"));
        }
        Valve[] valves = cluster.getValves();
        for (int i = 0; i < valves.length; i++) {
            result.add(build(valves[i], parentId + "/clusterValve/" + i, "clusterValve"));
        }
        if (cluster instanceof SimpleTcpCluster tcp) {
            ClusterManager manager = tcp.getManagerTemplate();
            if (manager != null) {
                result.add(build(manager, parentId + "/clusterManager/0", "clusterManager"));
            }
            if (cluster instanceof LifecycleBase base) {
                LifecycleListener[] listeners = base.findLifecycleListeners();
                for (int i = 0; i < listeners.length; i++) {
                    result.add(build(listeners[i], parentId + "/listener/" + i, "listener"));
                }
            }
            int idx = 0;
            for (ClusterListener listener : tcp.findClusterListeners()) {
                if (listener == deployer) {
                    // The deployer is already shown as its own child.
                    continue;
                }
                result.add(build(listener, parentId + "/clusterListener/" + idx, "clusterListener"));
                idx++;
            }
        }
        return result;
    }


    /**
     * The tree children of a channel: membership, sender, receiver and the (user configured) interceptors.
     */
    private List<Map<String, Object>> channelChildren(ManagedChannel channel, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        MembershipService membership = channel.getMembershipService();
        if (membership != null) {
            result.add(build(membership, parentId + "/membership/0", "membership"));
        }
        ChannelSender sender = channel.getChannelSender();
        if (sender != null) {
            result.add(build(sender, parentId + "/sender/0", "sender"));
        }
        ChannelReceiver receiver = channel.getChannelReceiver();
        if (receiver != null) {
            result.add(build(receiver, parentId + "/receiver/0", "receiver"));
        }
        Iterator<ChannelInterceptor> interceptors = channel.getInterceptors();
        int i = 0;
        while (interceptors.hasNext()) {
            result.add(build(interceptors.next(), parentId + "/interceptor/" + i, "interceptor"));
            i++;
        }
        return result;
    }


    /**
     * The tree children of a channel sender: the transport (a replication transmitter holds one).
     */
    private List<Map<String, Object>> senderChildren(ChannelSender sender, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (sender instanceof ReplicationTransmitter transmitter) {
            MultiPointSender transport = transmitter.getTransport();
            if (transport != null) {
                result.add(build(transport, parentId + "/transport/0", "transport"));
            }
        }
        return result;
    }


    /**
     * The tree children of a membership service: the static members when the service is a static membership service.
     */
    private List<Map<String, Object>> membershipChildren(MembershipService membership, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (membership instanceof StaticMembershipService sms) {
            Member local = sms.getLocalMember(false);
            if (local != null) {
                result.add(build(local, parentId + "/localMember/0", "member"));
            }
            List<StaticMember> members = sms.getStaticMembers();
            for (int i = 0; i < members.size(); i++) {
                result.add(build(members.get(i), parentId + "/member/" + i, "member"));
            }
        }
        return result;
    }


    /**
     * The tree children of an interceptor: the local member when the interceptor is a static membership interceptor.
     */
    private List<Map<String, Object>> interceptorChildren(ChannelInterceptor interceptor, String parentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (interceptor instanceof StaticMembershipInterceptor smi) {
            Member local = smi.getLocalMember(false);
            if (local != null) {
                result.add(build(local, parentId + "/localMember/0", "member"));
            }
        }
        return result;
    }


    private static String memberLabel(Member member) {
        byte[] hostBytes = member.getHost();
        String host = hostBytes == null ? null : new String(hostBytes);
        if (host == null || host.isEmpty()) {
            return member.getName();
        }
        return host + ":" + member.getPort();
    }


    private static String displayName(Object component, String type) {
        if (component instanceof Service s) {
            return s.getName();
        }
        if (component instanceof Engine e) {
            return e.getName();
        }
        if (component instanceof Host h) {
            return h.getName();
        }
        if (component instanceof Context c) {
            // The root context has the empty path; display it as "/".
            String path = c.getPath();
            return path.isEmpty() ? "/" : path;
        }
        if (component instanceof Wrapper w) {
            return w.getName();
        }
        if (component instanceof Executor e) {
            return e.getName();
        }
        if (component instanceof Connector connector) {
            return connectorLabel(connector);
        }
        if (component instanceof SSLHostConfig hostConfig) {
            return hostConfig.getHostName();
        }
        if (component instanceof SSLHostConfigCertificate certificate) {
            return certificateLabel(certificate);
        }
        if (component instanceof UpgradeProtocol upgradeProtocol) {
            return upgradeProtocolLabel(upgradeProtocol);
        }
        if (component instanceof ResourceBase entry) {
            return entry.getName();
        }
        if (component instanceof Member member) {
            return memberLabel(member);
        }
        return component.getClass().getSimpleName();
    }


    // --------------------------------------------------------- Node details


    private Map<String, Object> node(NodeRef ref) throws ConfigException {

        Object component = ref.component;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", ref.id);
        out.put("type", ref.type);
        out.put("name", ref.aliasValue != null ? ref.aliasValue : displayName(component, ref.type));
        out.put("className", component.getClass().getName());
        if (component instanceof Lifecycle lifecycle) {
            out.put("state", lifecycle.getState().toString());
        }
        if ("context".equals(ref.type) && component == selfContext) {
            out.put("self", Boolean.TRUE);
        }
        // Whether a lifecycle listener can be added to this component.
        // Not derivable on the client for a listener node, whose class may
        // or may not implement Lifecycle (e.g. a valve used as a listener).
        // An alias is excluded: its component is the parent host (an alias
        // is just a name, not a component that holds listeners).
        out.put("acceptsListener",
                !"alias".equals(ref.type) && !CLUSTER_SUB_TYPES.contains(ref.type) && component instanceof Lifecycle);
        // Whether the component has a lifecycle (and therefore supports
        // the start / stop / restart operations).
        if (component instanceof Lifecycle) {
            out.put("lifecycle", Boolean.TRUE);
            // Whether a start or stop of the component would interrupt
            // access to this web application (only a restart is allowed
            // for it; the client reconnects at the end of the operation).
            // Not derivable on the client for the service, the engine
            // and the connector (which of them route the requests of
            // this web application is a server-side decision).
            if (affectsSelf(ref)) {
                out.put("affectsSelf", Boolean.TRUE);
            }
        }

        if ("connector".equals(ref.type) && component instanceof Connector connector) {
            out.put("sslEnabled", isSslEnabled(connector));
        }
        if ("realm".equals(ref.type)) {
            // Only combined realms can hold (sub) realms. Not derivable on
            // the client from the class name alone (subclasses).
            out.put("acceptsSubRealm", component instanceof CombinedRealm);
        }
        if ("namingResources".equals(ref.type) && component instanceof NamingResourcesImpl namingResources) {
            // The server level node does not accept resourceLink entries.
            out.put("global", Boolean.valueOf(namingResources.getContainer() instanceof Server));
        }
        if ("sslHostConfig".equals(ref.type) && component instanceof SSLHostConfig hostConfig) {
            Connector owner = connectorOfSsl(hostConfig);
            if (owner != null) {
                try {
                    out.put("isDefault", Boolean
                            .valueOf(endpointOf(owner).getDefaultSSLHostConfigName().equals(hostConfig.getHostName())));
                } catch (ConfigException e) {
                    // No endpoint: leave isDefault out.
                }
            }
        }

        List<Map<String, Object>> properties = new ArrayList<>();
        // Some components have no modeler descriptor; their attribute
        // list is defined explicitly. They are handled before the
        // descriptor branch because the registry falls back to an
        // introspected ManagedBean for them, which would otherwise
        // shadow the (more complete) explicit list.
        ExplicitSpec explicit = explicitAttributes(component, ref.type);
        if (!explicit.attributes().isEmpty()) {
            // Factory options of a resource are localized per factory (the
            // same parameter name may carry a different text depending on
            // the factory that consumes it).
            String paramScope = "resource".equals(ref.type) && component instanceof ContextResource resource
                    ? factoryKey(effectiveFactory(resource)) : null;
            for (ExplicitAttribute attribute : explicit.attributes()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", attribute.getName());
                entry.put("type", attribute.getType());
                String description = attributeDescription(explicit.scope(), paramScope, attribute);
                if (description != null) {
                    entry.put("description", description);
                }
                entry.put("writable", Boolean.valueOf(attribute.isWritable()));
                if (attribute.isParam()) {
                    entry.put("param", Boolean.TRUE);
                }
                entry.put("value", jsonSafe(readExplicitValue(component, attribute)));
                properties.add(entry);
            }
        } else {
            ManagedBean descriptor = descriptor(component);
            if (descriptor != null) {
                for (AttributeInfo attribute : descriptor.getAttributes()) {
                    if (!attribute.isReadable()) {
                        continue;
                    }
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", attribute.getName());
                    entry.put("type", attribute.getType());
                    // The bundle wins when it defines the attribute (the
                    // hook that lets the standard, descriptor-provided
                    // descriptions be localized); otherwise the descriptor
                    // text is used as-is.
                    String description = Strings.sm().getString("manager2.attr." + ref.type + "." + attribute.getName());
                    if (description == null) {
                        description = attribute.getDescription();
                    }
                    if (description != null) {
                        entry.put("description", description);
                    }
                    entry.put("writable",
                            Boolean.valueOf(attribute.isWriteable() && EDITABLE_TYPES.contains(attribute.getType())));
                    entry.put("value", jsonSafe(readValue(component, attribute)));
                    properties.add(entry);
                }
            }
        }
        if ("connector".equals(ref.type) && component instanceof Connector connector) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", "sslEnabled");
            entry.put("type", "boolean");
            entry.put("writable", Boolean.FALSE);
            entry.put("value", Boolean.valueOf(isSslEnabled(connector)));
            properties.add(entry);
        }
        out.put("properties", properties);

        // The direct children of this node (one level, no recursion)
        Map<String, Object> built = build(component, ref.id, ref.type);
        out.put("children", built.get("children"));
        return out;
    }


    /**
     * The modeler descriptor (MBean contract) of the component's class, or {@code null} for classes without a
     * descriptor.
     */
    private static ManagedBean descriptor(Object component) {
        return Registry.getRegistry(null).findManagedBean(component.getClass().getName());
    }


    private static AttributeInfo findAttribute(ManagedBean descriptor, String name) {
        if (descriptor != null) {
            for (AttributeInfo attribute : descriptor.getAttributes()) {
                if (attribute.getName().equals(name)) {
                    return attribute;
                }
            }
        }
        return null;
    }


    private static Object readValue(Object component, AttributeInfo attribute) {
        String method = attribute.getGetMethod();
        if (method == null) {
            method = ("boolean".equals(attribute.getType()) || attribute.isIs())
                    ? "is" + capitalize(attribute.getName())
                    : "get" + capitalize(attribute.getName());
        }
        try {
            Method m = component.getClass().getMethod(method);
            return m.invoke(component);
        } catch (Exception e) {
            return null;
        }
    }


    // ------------------------------------- Explicit component attributes


    /**
     * One explicitly defined attribute of a component whose class has no modeler MBean descriptor (the TLS components
     * {@code SSLHostConfig}/{@code SSLHostConfigCertificate} and the context sub components {@code WebappLoader},
     * {@code CookieProcessorBase} and {@code SessionIdGeneratorBase}), defined here instead of being derived.
     * Descriptions are not carried here: they are looked up from the message bundle by scope and attribute name (see
     * {@link #attributeDescription}).
     */
    private static final class ExplicitAttribute {

        private final String name;

        private final String type;

        private final boolean writable;

        private final boolean param;


        ExplicitAttribute(String name, String type, boolean writable) {
            this(name, type, writable, false);
        }


        ExplicitAttribute(String name, String type, boolean writable, boolean param) {
            this.name = name;
            this.type = type;
            this.writable = writable;
            this.param = param;
        }


        String getName() {
            return name;
        }


        String getType() {
            return type;
        }


        boolean isWritable() {
            return writable;
        }


        /**
         * {@code true} for attributes that are not bean properties but string parameters of the generic property map of
         * a JNDI entry (the {@code ResourceBase} properties).
         */
        boolean isParam() {
            return param;
        }
    }


    private static final List<ExplicitAttribute> SSL_HOST_CONFIG_ATTRIBUTES = List.of(
            new ExplicitAttribute("hostName", "java.lang.String", false),
            new ExplicitAttribute("protocols", "java.lang.String", true),
            new ExplicitAttribute("certificateVerification", "java.lang.String", true),
            new ExplicitAttribute("certificateVerificationDepth", "int", true),
            new ExplicitAttribute("ciphers", "java.lang.String", true),
            new ExplicitAttribute("cipherSuites", "java.lang.String", true),
            new ExplicitAttribute("honorCipherOrder", "boolean", true),
            new ExplicitAttribute("sessionCacheSize", "int", true),
            new ExplicitAttribute("sessionTimeout", "int", true),
            new ExplicitAttribute("groups", "java.lang.String", true),
            new ExplicitAttribute("keyManagerAlgorithm", "java.lang.String", true),
            new ExplicitAttribute("sslProtocol", "java.lang.String", true),
            new ExplicitAttribute("revocationEnabled", "boolean", true),
            new ExplicitAttribute("trustManagerClassName", "java.lang.String", true),
            new ExplicitAttribute("truststoreAlgorithm", "java.lang.String", true),
            new ExplicitAttribute("truststoreFile", "java.lang.String", true),
            new ExplicitAttribute("truststorePassword", "java.lang.String", true),
            new ExplicitAttribute("truststoreProvider", "java.lang.String", true),
            new ExplicitAttribute("truststoreType", "java.lang.String", true),
            new ExplicitAttribute("caCertificateFile", "java.lang.String", true),
            new ExplicitAttribute("caCertificatePath", "java.lang.String", true),
            new ExplicitAttribute("certificateRevocationListPath", "java.lang.String", true),
            new ExplicitAttribute("disableCompression", "boolean", true),
            new ExplicitAttribute("disableSessionTickets", "boolean", true),
            new ExplicitAttribute("insecureRenegotiation", "boolean", true));


    private static final List<ExplicitAttribute> CERTIFICATE_ATTRIBUTES = List.of(
            new ExplicitAttribute("type", "java.lang.String", false),
            new ExplicitAttribute("certificateKeystoreFile", "java.lang.String", true),
            new ExplicitAttribute("certificateKeystorePassword", "java.lang.String", true),
            new ExplicitAttribute("certificateKeystorePasswordFile", "java.lang.String", true),
            new ExplicitAttribute("certificateKeystoreType", "java.lang.String", true),
            new ExplicitAttribute("certificateKeystoreProvider", "java.lang.String", true),
            new ExplicitAttribute("certificateKeyAlias", "java.lang.String", true),
            new ExplicitAttribute("certificateKeyPassword", "java.lang.String", true),
            new ExplicitAttribute("certificateKeyPasswordFile", "java.lang.String", true),
            new ExplicitAttribute("certificateFile", "java.lang.String", true),
            new ExplicitAttribute("certificateChainFile", "java.lang.String", true),
            new ExplicitAttribute("certificateKeyFile", "java.lang.String", true));


    private static final List<ExplicitAttribute> WEBAPP_LOADER_ATTRIBUTES = List.of(
            new ExplicitAttribute("delegate", "boolean", true),
            new ExplicitAttribute("loaderClass", "java.lang.String", true),
            new ExplicitAttribute("jakartaConverter", "java.lang.String", true));


    private static final List<ExplicitAttribute> COOKIE_PROCESSOR_ATTRIBUTES = List.of(
            new ExplicitAttribute("cookiesWithoutEquals", "java.lang.String", true),
            new ExplicitAttribute("sameSiteCookies", "java.lang.String", true),
            new ExplicitAttribute("partitioned", "boolean", true));


    private static final List<ExplicitAttribute> SESSION_ID_GENERATOR_ATTRIBUTES = List.of(
            new ExplicitAttribute("secureRandomClass", "java.lang.String", true),
            new ExplicitAttribute("jvmRoute", "java.lang.String", true),
            new ExplicitAttribute("sessionIdLength", "int", true));


    // The HTTP/2 upgrade protocol has no modeler descriptor either. Only the
    // common, documented knobs are listed (like the cluster channel
    // components). All changes take effect when the owning connector is
    // (re)started, the same as the protocol itself.
    private static final List<ExplicitAttribute> HTTP2_PROTOCOL_ATTRIBUTES = List.of(
            new ExplicitAttribute("readTimeout", "long", true),
            new ExplicitAttribute("writeTimeout", "long", true),
            new ExplicitAttribute("keepAliveTimeout", "long", true),
            new ExplicitAttribute("streamReadTimeout", "long", true),
            new ExplicitAttribute("streamWriteTimeout", "long", true),
            new ExplicitAttribute("maxConcurrentStreams", "long", true),
            new ExplicitAttribute("maxConcurrentStreamExecution", "int", true),
            new ExplicitAttribute("initialWindowSize", "int", true),
            new ExplicitAttribute("useSendfile", "boolean", true),
            new ExplicitAttribute("allowSchemeMismatch", "boolean", true),
            new ExplicitAttribute("maxHeaderCount", "int", true),
            new ExplicitAttribute("maxHeaderSize", "int", false),
            new ExplicitAttribute("maxTrailerCount", "int", true),
            new ExplicitAttribute("maxTrailerSize", "int", false),
            new ExplicitAttribute("overheadCountFactor", "int", true),
            new ExplicitAttribute("overheadResetFactor", "int", true),
            new ExplicitAttribute("overheadContinuationThreshold", "int", true),
            new ExplicitAttribute("overheadDataThreshold", "int", true),
            new ExplicitAttribute("overheadWindowUpdateThreshold", "int", true),
            new ExplicitAttribute("initiatePingDisabled", "boolean", true),
            new ExplicitAttribute("discardRequestsAndResponses", "boolean", true),
            new ExplicitAttribute("drainTimeout", "long", true));


    // --------------------------------- Cluster channel attributes

    // The sub components of a cluster channel (GroupChannel, the multicast
    // membership service, the receiver, the sender transport and the
    // interceptors) have no modeler descriptors, so their attributes are
    // defined explicitly. Only the common, documented knobs are listed.

    private static final List<ExplicitAttribute> CHANNEL_ATTRIBUTES = List.of(
            new ExplicitAttribute("name", "java.lang.String", true),
            new ExplicitAttribute("heartbeat", "boolean", true),
            new ExplicitAttribute("heartbeatSleeptime", "long", true),
            new ExplicitAttribute("jmxDomain", "java.lang.String", true),
            new ExplicitAttribute("jmxPrefix", "java.lang.String", true),
            new ExplicitAttribute("optionCheck", "boolean", true));

    private static final List<ExplicitAttribute> MCAST_ATTRIBUTES = List.of(
            new ExplicitAttribute("address", "java.lang.String", true),
            new ExplicitAttribute("port", "int", true),
            new ExplicitAttribute("frequency", "long", true),
            new ExplicitAttribute("dropTime", "long", true),
            new ExplicitAttribute("ttl", "int", true),
            new ExplicitAttribute("soTimeout", "int", true),
            new ExplicitAttribute("recoveryEnabled", "boolean", true),
            new ExplicitAttribute("recoverySleepTime", "long", true),
            new ExplicitAttribute("localLoopbackDisabled", "boolean", true));

    private static final List<ExplicitAttribute> RECEIVER_ATTRIBUTES = List.of(
            new ExplicitAttribute("port", "int", true),
            new ExplicitAttribute("autoBind", "int", true),
            new ExplicitAttribute("address", "java.lang.String", true),
            new ExplicitAttribute("udpPort", "int", true),
            new ExplicitAttribute("maxThreads", "int", true),
            new ExplicitAttribute("minThreads", "int", true),
            new ExplicitAttribute("selectorTimeout", "long", true),
            new ExplicitAttribute("tcpNoDelay", "boolean", true),
            new ExplicitAttribute("soKeepAlive", "boolean", true),
            new ExplicitAttribute("soReuseAddress", "boolean", true));

    private static final List<ExplicitAttribute> TRANSPORT_ATTRIBUTES = List.of(
            new ExplicitAttribute("poolSize", "int", true),
            new ExplicitAttribute("timeout", "long", true),
            new ExplicitAttribute("maxRetryAttempts", "int", true),
            new ExplicitAttribute("udpPort", "int", true),
            new ExplicitAttribute("directBuffer", "boolean", true),
            new ExplicitAttribute("tcpNoDelay", "boolean", true),
            new ExplicitAttribute("soKeepAlive", "boolean", true),
            new ExplicitAttribute("soReuseAddress", "boolean", true));

    private static final List<ExplicitAttribute> MESSAGE_DISPATCH_INTERCEPTOR_ATTRIBUTES = List.of(
            new ExplicitAttribute("maxQueueSize", "long", true),
            new ExplicitAttribute("maxThreads", "int", true),
            new ExplicitAttribute("maxSpareThreads", "int", true),
            new ExplicitAttribute("keepAliveTime", "long", true),
            new ExplicitAttribute("useDeepClone", "boolean", true),
            new ExplicitAttribute("alwaysSend", "boolean", true));

    private static final List<ExplicitAttribute> TCP_FAILURE_DETECTOR_ATTRIBUTES = List.of(
            new ExplicitAttribute("connectTimeout", "long", true),
            new ExplicitAttribute("readTestTimeout", "long", true),
            new ExplicitAttribute("performSendTest", "boolean", true),
            new ExplicitAttribute("performReadTest", "boolean", true),
            new ExplicitAttribute("removeSuspectsTimeout", "int", true));

    private static final List<ExplicitAttribute> INTERCEPTOR_ATTRIBUTES = List.of(new ExplicitAttribute("optionFlag", "int", true));


    // ------------------------------------------------- JNDI entry attributes

    // The JNDI entry types (children of a namingResources node).

    private static final List<ExplicitAttribute> RESOURCE_ATTRIBUTES = List.of(
            new ExplicitAttribute("name", "java.lang.String", true),
            new ExplicitAttribute("type", "java.lang.String", true),
            new ExplicitAttribute("auth", "java.lang.String", true),
            new ExplicitAttribute("scope", "java.lang.String", true),
            new ExplicitAttribute("singleton", "boolean", true),
            new ExplicitAttribute("closeMethod", "java.lang.String", true),
            new ExplicitAttribute("lookupName", "java.lang.String", true),
            new ExplicitAttribute("description", "java.lang.String", true));

    private static final List<ExplicitAttribute> RESOURCE_LINK_ATTRIBUTES = List.of(
            new ExplicitAttribute("name", "java.lang.String", true),
            new ExplicitAttribute("type", "java.lang.String", true),
            new ExplicitAttribute("global", "java.lang.String", true),
            new ExplicitAttribute("factory", "java.lang.String", true),
            new ExplicitAttribute("description", "java.lang.String", true));

    private static final List<ExplicitAttribute> RESOURCE_ENV_REF_ATTRIBUTES = List.of(
            new ExplicitAttribute("name", "java.lang.String", true),
            new ExplicitAttribute("type", "java.lang.String", true),
            new ExplicitAttribute("override", "boolean", true),
            new ExplicitAttribute("description", "java.lang.String", true));

    private static final List<ExplicitAttribute> ENVIRONMENT_ATTRIBUTES = List.of(
            new ExplicitAttribute("name", "java.lang.String", true),
            new ExplicitAttribute("type", "java.lang.String", true),
            new ExplicitAttribute("value", "java.lang.String", true),
            new ExplicitAttribute("override", "boolean", true),
            new ExplicitAttribute("description", "java.lang.String", true));

    private static final List<ExplicitAttribute> EJB_ATTRIBUTES = List.of(
            new ExplicitAttribute("name", "java.lang.String", true),
            new ExplicitAttribute("type", "java.lang.String", true),
            new ExplicitAttribute("home", "java.lang.String", true),
            new ExplicitAttribute("link", "java.lang.String", true),
            new ExplicitAttribute("remote", "java.lang.String", true),
            new ExplicitAttribute("description", "java.lang.String", true));

    private static final List<ExplicitAttribute> LOCAL_EJB_ATTRIBUTES = List.of(
            new ExplicitAttribute("name", "java.lang.String", true),
            new ExplicitAttribute("type", "java.lang.String", true),
            new ExplicitAttribute("local", "java.lang.String", true),
            new ExplicitAttribute("home", "java.lang.String", true),
            new ExplicitAttribute("link", "java.lang.String", true),
            new ExplicitAttribute("description", "java.lang.String", true));

    private static final List<ExplicitAttribute> SERVICE_ATTRIBUTES = List.of(
            new ExplicitAttribute("name", "java.lang.String", true),
            new ExplicitAttribute("type", "java.lang.String", true),
            new ExplicitAttribute("interface", "java.lang.String", true),
            new ExplicitAttribute("displayname", "java.lang.String", true),
            new ExplicitAttribute("wsdlfile", "java.lang.String", true),
            new ExplicitAttribute("description", "java.lang.String", true));

    // The first party JNDI ObjectFactory implementations shipped with
    // Tomcat and the string parameters (RefAddr keys) each one consumes.

    private static final String BASIC_DATA_SOURCE_FACTORY = "org.apache.tomcat.dbcp.dbcp2.BasicDataSourceFactory";

    private static final String MEMORY_USER_DATABASE_FACTORY = "org.apache.catalina.users.MemoryUserDatabaseFactory";

    private static final String DATA_SOURCE_USER_DATABASE_FACTORY = "org.apache.catalina.users.DataSourceUserDatabaseFactory";

    private static final String PER_USER_POOL_DATA_SOURCE_FACTORY = "org.apache.tomcat.dbcp.dbcp2.datasources.PerUserPoolDataSourceFactory";

    private static final String SHARED_POOL_DATA_SOURCE_FACTORY = "org.apache.tomcat.dbcp.dbcp2.datasources.SharedPoolDataSourceFactory";

    private static final List<ExplicitAttribute> BASIC_DATA_SOURCE_FACTORY_OPTIONS = List.of(
            option("defaultAutoCommit", "boolean"),
            option("defaultReadOnly", "boolean"),
            option("defaultTransactionIsolation", "java.lang.String"),
            option("defaultCatalog", "java.lang.String"),
            option("defaultSchema", "java.lang.String"),
            option("cacheState", "boolean"),
            option("driverClassName", "java.lang.String"),
            option("lifo", "boolean"),
            option("maxTotal", "int"),
            option("maxIdle", "int"),
            option("minIdle", "int"),
            option("initialSize", "int"),
            option("maxWaitMillis", "long"),
            option("testOnCreate", "boolean"),
            option("testOnBorrow", "boolean"),
            option("testOnReturn", "boolean"),
            option("timeBetweenEvictionRunsMillis", "long"),
            option("numTestsPerEvictionRun", "int"),
            option("minEvictableIdleTimeMillis", "long"),
            option("softMinEvictableIdleTimeMillis", "long"),
            option("evictionPolicyClassName", "java.lang.String"),
            option("testWhileIdle", "boolean"),
            option("password", "java.lang.String"),
            option("url", "java.lang.String"),
            option("username", "java.lang.String"),
            option("validationQuery", "java.lang.String"),
            option("validationQueryTimeout", "long"),
            option("connectionInitSqls", "java.lang.String"),
            option("accessToUnderlyingConnectionAllowed", "boolean"),
            option("removeAbandonedOnBorrow", "boolean"),
            option("removeAbandonedOnMaintenance", "boolean"),
            option("removeAbandonedTimeout", "long"),
            option("logAbandoned", "boolean"),
            option("abandonedUsageTracking", "java.lang.String"),
            option("poolPreparedStatements", "boolean"),
            option("clearStatementPoolOnReturn", "boolean"),
            option("maxOpenPreparedStatements", "int"),
            option("connectionProperties", "java.lang.String"),
            option("maxConnLifetimeMillis", "long"),
            option("logExpiredConnections", "boolean"),
            option("rollbackOnReturn", "boolean"),
            option("enableAutoCommitOnReturn", "boolean"),
            option("defaultQueryTimeout", "long"),
            option("fastFailValidation", "boolean"),
            option("disconnectionSqlCodes", "java.lang.String"),
            option("disconnectionIgnoreSqlCodes", "java.lang.String"),
            option("jmxName", "java.lang.String"),
            option("registerConnectionMBean", "boolean"),
            option("connectionFactoryClassName", "java.lang.String"));

    private static final List<ExplicitAttribute> MEMORY_USER_DATABASE_FACTORY_OPTIONS = List.of(
            option("pathname", "java.lang.String"),
            option("readonly", "boolean"),
            option("watchSource", "boolean"));

    private static final List<ExplicitAttribute> DATA_SOURCE_USER_DATABASE_FACTORY_OPTIONS = List.of(
            option("dataSourceName", "java.lang.String"),
            option("readonly", "boolean"),
            option("userTable", "java.lang.String"),
            option("groupTable", "java.lang.String"),
            option("roleTable", "java.lang.String"),
            option("userRoleTable", "java.lang.String"),
            option("userGroupTable", "java.lang.String"),
            option("groupRoleTable", "java.lang.String"),
            option("roleNameCol", "java.lang.String"),
            option("roleAndGroupDescriptionCol", "java.lang.String"),
            option("groupNameCol", "java.lang.String"),
            option("userCredCol", "java.lang.String"),
            option("userFullNameCol", "java.lang.String"),
            option("userNameCol", "java.lang.String"));

    private static final List<ExplicitAttribute> POOL_DATA_SOURCE_FACTORY_OPTIONS = List.of(
            option("instanceKey", "java.lang.String"),
            option("description", "java.lang.String"),
            option("loginTimeout", "int"),
            option("blockWhenExhausted", "boolean"),
            option("evictionPolicyClassName", "java.lang.String"),
            option("lifo", "boolean"),
            option("maxIdlePerKey", "int"),
            option("maxTotalPerKey", "int"),
            option("maxWaitMillis", "long"),
            option("minEvictableIdleTimeMillis", "long"),
            option("minIdlePerKey", "int"),
            option("numTestsPerEvictionRun", "int"),
            option("softMinEvictableIdleTimeMillis", "long"),
            option("testOnCreate", "boolean"),
            option("testOnBorrow", "boolean"),
            option("testOnReturn", "boolean"),
            option("testWhileIdle", "boolean"),
            option("timeBetweenEvictionRunsMillis", "long"),
            option("validationQuery", "java.lang.String"),
            option("validationQueryTimeout", "int"),
            option("rollbackAfterValidation", "boolean"),
            option("maxConnLifetimeMillis", "long"),
            option("defaultAutoCommit", "boolean"),
            option("defaultTransactionIsolation", "int"),
            option("defaultReadOnly", "boolean"));

    private static final List<ExplicitAttribute> PER_USER_POOL_DATA_SOURCE_FACTORY_OPTIONS = poolOptions(
            option("defaultMaxTotal", "int"),
            option("defaultMaxIdle", "int"),
            option("defaultMaxWaitMillis", "long"));

    private static final List<ExplicitAttribute> SHARED_POOL_DATA_SOURCE_FACTORY_OPTIONS = poolOptions(
            option("maxTotal", "int"));

    private static List<ExplicitAttribute> poolOptions(ExplicitAttribute... first) {
        List<ExplicitAttribute> result = new ArrayList<>(Arrays.asList(first));
        result.addAll(POOL_DATA_SOURCE_FACTORY_OPTIONS);
        return List.copyOf(result);
    }

    private static ExplicitAttribute option(String name, String type) {
        return new ExplicitAttribute(name, type, true, true);
    }


    /**
     * The factory options (RefAddr keys) of the given first party JNDI factory, or {@code null} when the factory is not
     * one of the factories shipped with Tomcat (their parameters stay free form).
     */
    private static List<ExplicitAttribute> factoryOptions(String factory) {
        if (factory == null) {
            return null;
        }
        return switch (factory) {
            case BASIC_DATA_SOURCE_FACTORY -> BASIC_DATA_SOURCE_FACTORY_OPTIONS;
            case MEMORY_USER_DATABASE_FACTORY -> MEMORY_USER_DATABASE_FACTORY_OPTIONS;
            case DATA_SOURCE_USER_DATABASE_FACTORY -> DATA_SOURCE_USER_DATABASE_FACTORY_OPTIONS;
            case PER_USER_POOL_DATA_SOURCE_FACTORY -> PER_USER_POOL_DATA_SOURCE_FACTORY_OPTIONS;
            case SHARED_POOL_DATA_SOURCE_FACTORY -> SHARED_POOL_DATA_SOURCE_FACTORY_OPTIONS;
            default -> null;
        };
    }


    /**
     * The factory a resource resolves to: the explicit {@code factory} parameter, or the default factory the
     * {@code ResourceFactory} dispatches the resource type to.
     */
    private static String effectiveFactory(ContextResource resource) {
        Object factory = resource.getProperty("factory");
        if (factory != null && !String.valueOf(factory).isEmpty()) {
            return String.valueOf(factory);
        }
        // Default dispatch of org.apache.naming.factory.ResourceFactory.
        if ("javax.sql.DataSource".equals(resource.getType())) {
            return BASIC_DATA_SOURCE_FACTORY;
        }
        return null;
    }


    private static boolean isNamingEntry(String type) {
        return "resource".equals(type) || "resourceLink".equals(type) || "resourceEnvRef".equals(type) ||
                "environment".equals(type) || "ejb".equals(type) || "localEjb".equals(type) ||
                "serviceRef".equals(type);
    }


    /**
     * The attribute list of one JNDI entry: the core attributes of the entry type, plus - for a resource whose factory
     * is one of the first party factories with a closed set of parameters - the factory options, plus the free form
     * parameters that are set but not covered by the list.
     */
    private static List<ExplicitAttribute> namingEntryAttributes(Object component, String type) {
        List<ExplicitAttribute> result;
        if ("resource".equals(type)) {
            ContextResource resource = (ContextResource) component;
            result = new ArrayList<>(RESOURCE_ATTRIBUTES);
            List<ExplicitAttribute> options = factoryOptions(effectiveFactory(resource));
            if (options != null) {
                result.addAll(options);
            }
        } else {
            result = new ArrayList<>(switch (type) {
                case "resourceLink" -> RESOURCE_LINK_ATTRIBUTES;
                case "resourceEnvRef" -> RESOURCE_ENV_REF_ATTRIBUTES;
                case "environment" -> ENVIRONMENT_ATTRIBUTES;
                case "ejb" -> EJB_ATTRIBUTES;
                case "localEjb" -> LOCAL_EJB_ATTRIBUTES;
                case "serviceRef" -> SERVICE_ATTRIBUTES;
                default -> List.of();
            });
        }
        // The string parameters of the entry (the ResourceBase properties)
        // that are not covered by the explicit attribute tables above.
        appendParams((ResourceBase) component, result);
        return result;
    }


    /**
     * Append the string parameters of the given JNDI entry that are not already part of its explicit attribute list.
     */
    private static void appendParams(ResourceBase entry, List<ExplicitAttribute> result) {
        Set<String> covered = new HashSet<>();
        for (ExplicitAttribute attribute : result) {
            covered.add(attribute.getName());
        }
        for (Iterator<String> it = entry.listProperties(); it.hasNext();) {
            String key = it.next();
            if (covered.add(key)) {
                result.add(new ExplicitAttribute(key, "java.lang.String", true, true));
            }
        }
    }


    /**
     * The explicitly defined attributes of the given component, or an empty list when the component's class has a
     * modeler descriptor (or is not one of the explicitly supported classes).
     */
    private static ExplicitSpec explicitAttributes(Object component, String type) {
        if ("sslHostConfig".equals(type)) {
            return new ExplicitSpec("sslHostConfig", SSL_HOST_CONFIG_ATTRIBUTES);
        }
        if ("certificate".equals(type)) {
            return new ExplicitSpec("certificate", CERTIFICATE_ATTRIBUTES);
        }
        if (component instanceof WebappLoader) {
            return new ExplicitSpec("loader", WEBAPP_LOADER_ATTRIBUTES);
        }
        if (component instanceof CookieProcessorBase) {
            return new ExplicitSpec("cookieProcessor", COOKIE_PROCESSOR_ATTRIBUTES);
        }
        if (component instanceof SessionIdGeneratorBase) {
            return new ExplicitSpec("sessionIdGenerator", SESSION_ID_GENERATOR_ATTRIBUTES);
        }
        if (component instanceof Http2Protocol) {
            return new ExplicitSpec("http2Protocol", HTTP2_PROTOCOL_ATTRIBUTES);
        }
        if (component instanceof GroupChannel) {
            return new ExplicitSpec("channel", CHANNEL_ATTRIBUTES);
        }
        if (component instanceof McastService) {
            return new ExplicitSpec("membership", MCAST_ATTRIBUTES);
        }
        if (component instanceof ReceiverBase) {
            return new ExplicitSpec("receiver", RECEIVER_ATTRIBUTES);
        }
        if (component instanceof AbstractSender) {
            return new ExplicitSpec("transport", TRANSPORT_ATTRIBUTES);
        }
        if (component instanceof MessageDispatchInterceptor) {
            return new ExplicitSpec("messageDispatchInterceptor", MESSAGE_DISPATCH_INTERCEPTOR_ATTRIBUTES);
        }
        if (component instanceof TcpFailureDetector) {
            return new ExplicitSpec("tcpFailureDetector", TCP_FAILURE_DETECTOR_ATTRIBUTES);
        }
        if (component instanceof ChannelInterceptor) {
            return new ExplicitSpec("channelInterceptor", INTERCEPTOR_ATTRIBUTES);
        }
        if (isNamingEntry(type) && component instanceof ResourceBase) {
            return new ExplicitSpec(type, namingEntryAttributes(component, type));
        }
        return new ExplicitSpec(type, List.of());
    }


    /**
     * The explicit attribute list of a component together with the key scope its descriptions are looked up under
     * (see {@link #attributeDescription}).
     */
    private record ExplicitSpec(String scope, List<ExplicitAttribute> attributes) {
    }


    /**
     * The localized description of one explicitly defined attribute, looked up from the message bundle by its
     * {@code name}: {@code manager2.attr.<scope>.<name>} for a bean attribute; for a string parameter,
     * {@code manager2.param.<factoryKey>.<name>} first (a factory with a closed parameter set may give the name its
     * own text) and then {@code manager2.param.<name>}.
     *
     * @param scope      the key scope of the attribute list the attribute belongs to
     * @param paramScope the factory key scope for the parameters of a resource bound to a first party factory, or
     *                   {@code null}
     * @param attribute  the attribute
     *
     * @return the localized description, or {@code null} when the bundle defines no message for the attribute
     */
    private static String attributeDescription(String scope, String paramScope, ExplicitAttribute attribute) {
        var sm = Strings.sm();
        if (attribute.isParam()) {
            if (paramScope != null) {
                String scoped = sm.getString("manager2.param." + paramScope + "." + attribute.getName());
                if (scoped != null) {
                    return scoped;
                }
            }
            return sm.getString("manager2.param." + attribute.getName());
        }
        return sm.getString("manager2.attr." + scope + "." + attribute.getName());
    }


    /**
     * The bundle key scope of the parameters of a first party JNDI factory (see
     * {@link #attributeDescription(String, String, ExplicitAttribute)}).
     *
     * @param factory the factory class name, possibly {@code null}
     *
     * @return the key scope of the factory, or {@code null} for a factory that is not shipped with Tomcat
     */
    private static String factoryKey(String factory) {
        if (factory == null) {
            return null;
        }
        return switch (factory) {
            case BASIC_DATA_SOURCE_FACTORY -> "basicDataSource";
            case MEMORY_USER_DATABASE_FACTORY -> "memoryUserDatabase";
            case DATA_SOURCE_USER_DATABASE_FACTORY -> "dataSourceUserDatabase";
            case PER_USER_POOL_DATA_SOURCE_FACTORY -> "perUserPoolDataSource";
            case SHARED_POOL_DATA_SOURCE_FACTORY -> "sharedPoolDataSource";
            default -> null;
        };
    }


    private static ExplicitAttribute findExplicitAttribute(Object component, String type, String name) {
        for (ExplicitAttribute attribute : explicitAttributes(component, type).attributes()) {
            if (attribute.getName().equals(name)) {
                return attribute;
            }
        }
        return null;
    }


    /**
     * Read the value of one explicitly defined attribute. A few attributes need a dedicated reader: the protocol set is
     * shown as the {@code +} separated list that {@code setProtocols} accepts, and the certificate verification level
     * as its string form.
     */
    private static Object readExplicitValue(Object component, ExplicitAttribute attribute) {
        String name = attribute.getName();
        if (attribute.isParam()) {
            Object value = ((ResourceBase) component).getProperty(name);
            return value == null ? null : String.valueOf(value);
        }
        try {
            if ("protocols".equals(name) && component instanceof SSLHostConfig hostConfig) {
                StringBuilder protocols = new StringBuilder();
                for (String protocol : hostConfig.getProtocols()) {
                    if (!protocols.isEmpty()) {
                        protocols.append('+');
                    }
                    protocols.append(protocol);
                }
                return protocols.toString();
            }
            if ("certificateVerification".equals(name) && component instanceof SSLHostConfig hostConfig) {
                return hostConfig.getCertificateVerificationAsString();
            }
            try {
                return component.getClass().getMethod("get" + capitalize(name)).invoke(component);
            } catch (NoSuchMethodException nsm) {
                return component.getClass().getMethod("is" + capitalize(name)).invoke(component);
            }
        } catch (Exception e) {
            return null;
        }
    }


    private static void setExplicitValue(Object component, ExplicitAttribute attribute, Object value)
            throws ConfigException {
        if (attribute.isParam()) {
            // A string parameter of the generic property map of a JNDI
            // entry; an empty value removes the parameter.
            ResourceBase base = (ResourceBase) component;
            if (value == null || (value instanceof String s && s.isEmpty())) {
                base.removeProperty(attribute.getName());
            } else {
                base.setProperty(attribute.getName(), String.valueOf(value));
            }
            return;
        }
        String method = "set" + capitalize(attribute.getName());
        try {
            Method m = component.getClass().getMethod(method, classForType(attribute.getType()));
            m.invoke(component, value);
        } catch (NoSuchMethodException e) {
            throw new ConfigException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "NO_SETTER",
                    Strings.sm().getString("manager2.configNoSetter", method));
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "SET_FAILED",
                    Strings.sm().getString("manager2.configSetFailed", attribute.getName(),
                            t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
        } catch (IllegalAccessException e) {
            throw new ConfigException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "NO_SETTER",
                    Strings.sm().getString("manager2.configNoSetter", method));
        }
    }


    /**
     * Convert an arbitrary component value to a JSON safe representation (null, String, Number, Boolean, List or Map).
     */
    private static Object jsonSafe(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number && !(value instanceof Double d && (d.isNaN() || d.isInfinite()))) {
            return value;
        }
        if (value instanceof String[] array) {
            return List.of(array);
        }
        if (value instanceof Object[] array) {
            List<String> list = new ArrayList<>(array.length);
            for (Object item : array) {
                list.add(String.valueOf(item));
            }
            return list;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                result.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
            return result;
        }
        if (value instanceof Double d && (d.isNaN() || d.isInfinite())) {
            return String.valueOf(value);
        }
        return String.valueOf(value);
    }


    // ---------------------------------------------------------- Resolving


    /**
     * A resolved tree node: the component, its parent (if it has one), the node type and, for alias nodes, the alias
     * value.
     */
    private static final class NodeRef {

        private final Object component;
        private final Object parent;
        private final String type;
        private final String aliasValue;
        private final String id;


        NodeRef(Object component, Object parent, String type, String aliasValue, String id) {
            this.component = component;
            this.parent = parent;
            this.type = type;
            this.aliasValue = aliasValue;
            this.id = id;
        }
    }


    /**
     * Resolve a node id (see the class javadoc for the grammar) to a component.
     */
    private NodeRef resolve(String id) throws ConfigException {

        if (id == null || id.isEmpty()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_ID",
                    Strings.sm().getString("manager2.configInvalidId"));
        }

        String[] segments = id.split("/", -1);
        if (!"server".equals(segments[0])) {
            throw new ConfigException(HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND",
                    Strings.sm().getString("manager2.configNotFound"));
        }

        Object current = server;
        Object parent = null;
        String type = "server";

        for (int i = 1; i < segments.length; i += 2) {
            if (i + 1 >= segments.length) {
                throw new ConfigException(HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND",
                        Strings.sm().getString("manager2.configNotFound"));
            }
            String kind = segments[i];
            String value = dec(segments[i + 1]);

            switch (kind) {
                case "service" -> {
                    Service service = server.findService(value);
                    if (service == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = service;
                }
                case "engine" -> {
                    if (!(current instanceof StandardService service) ||
                            !(service.getContainer() instanceof Engine engine)) {
                        throw notFound();
                    }
                    parent = current;
                    current = engine;
                }
                case "host" -> {
                    if (!(current instanceof StandardEngine engine) ||
                            !(engine.findChild(value) instanceof Host host)) {
                        throw notFound();
                    }
                    parent = current;
                    current = host;
                }
                case "context" -> {
                    // The root context is registered under the empty name,
                    // not "/", so map the bare "+" segment accordingly.
                    String contextName = "+".equals(segments[i + 1]) ? "" : value;
                    if (!(current instanceof Host host) || !(host.findChild(contextName) instanceof Context context)) {
                        throw notFound();
                    }
                    parent = current;
                    current = context;
                }
                case "wrapper" -> {
                    if (!(current instanceof Context context) ||
                            !(context.findChild(value) instanceof Wrapper wrapper)) {
                        throw notFound();
                    }
                    parent = current;
                    current = wrapper;
                }
                case "alias" -> {
                    if (!(current instanceof Host host)) {
                        throw notFound();
                    }
                    if (!Arrays.asList(host.findAliases()).contains(value)) {
                        throw notFound();
                    }
                    return new NodeRef(host, host, "alias", value, id);
                }
                case "valve" -> {
                    if (!(current instanceof Container container)) {
                        throw notFound();
                    }
                    Valve[] valves = container.getPipeline().getValves();
                    int index = index(value);
                    if (index < 0 || index >= valves.length) {
                        throw notFound();
                    }
                    parent = current;
                    current = valves[index];
                }
                case "connector" -> {
                    if (!(current instanceof StandardService service)) {
                        throw notFound();
                    }
                    Connector[] connectors = service.findConnectors();
                    int idx = index(value);
                    if (idx < 0 || idx >= connectors.length) {
                        throw notFound();
                    }
                    parent = current;
                    current = connectors[idx];
                }
                case "executor" -> {
                    if (!(current instanceof StandardService service)) {
                        throw notFound();
                    }
                    Executor found = null;
                    for (Executor executor : service.findExecutors()) {
                        if (executor.getName().equals(value)) {
                            found = executor;
                            break;
                        }
                    }
                    if (found == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = found;
                }
                case "listener" -> {
                    if (!(current instanceof LifecycleBase base)) {
                        throw notFound();
                    }
                    LifecycleListener[] listeners = base.findLifecycleListeners();
                    int index = index(value);
                    if (index < 0 || index >= listeners.length) {
                        throw notFound();
                    }
                    parent = current;
                    current = listeners[index];
                }
                case "realm" -> {
                    int index = index(value);
                    Realm found = null;
                    if (current instanceof Container container) {
                        // A container holds at most one directly attached
                        // realm, addressed as index 0.
                        if (index == 0) {
                            found = ownRealm(container);
                        }
                    } else if (current instanceof CombinedRealm combined) {
                        Realm[] nested = combined.getNestedRealms();
                        if (index >= 0 && index < nested.length) {
                            found = nested[index];
                        }
                    }
                    if (found == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = found;
                }
                case "sslHostConfig" -> {
                    if (!(current instanceof Connector connector)) {
                        throw notFound();
                    }
                    SSLHostConfig[] hostConfigs = connector.findSslHostConfigs();
                    SSLHostConfig found = null;
                    if (hostConfigs != null) {
                        for (SSLHostConfig hostConfig : hostConfigs) {
                            // Host names are case-insensitive and stored
                            // in lower case.
                            if (hostConfig.getHostName().equalsIgnoreCase(value)) {
                                found = hostConfig;
                                break;
                            }
                        }
                    }
                    if (found == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = found;
                }
                case "upgradeProtocol" -> {
                    if (!(current instanceof Connector connector)) {
                        throw notFound();
                    }
                    UpgradeProtocol[] upgradeProtocols = connector.findUpgradeProtocols();
                    int index = index(value);
                    if (index < 0 || index >= upgradeProtocols.length) {
                        throw notFound();
                    }
                    parent = current;
                    current = upgradeProtocols[index];
                }
                case "certificate" -> {
                    if (!(current instanceof SSLHostConfig hostConfig)) {
                        throw notFound();
                    }
                    SSLHostConfigCertificate[] certificates = hostConfig.getCertificates()
                            .toArray(new SSLHostConfigCertificate[0]);
                    int index = index(value);
                    if (index < 0 || index >= certificates.length) {
                        throw notFound();
                    }
                    parent = current;
                    current = certificates[index];
                }
                case "manager" -> {
                    // A context holds exactly one manager, addressed as
                    // index 0.
                    if (!(current instanceof Context context) || index(value) != 0) {
                        throw notFound();
                    }
                    Manager manager = context.getManager();
                    if (manager == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = manager;
                }
                case "resources" -> {
                    if (!(current instanceof Context context) || index(value) != 0) {
                        throw notFound();
                    }
                    WebResourceRoot resources = context.getResources();
                    if (resources == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = resources;
                }
                case "loader" -> {
                    if (!(current instanceof Context context) || index(value) != 0) {
                        throw notFound();
                    }
                    Loader loader = context.getLoader();
                    if (loader == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = loader;
                }
                case "cookieProcessor" -> {
                    if (!(current instanceof Context context) || index(value) != 0) {
                        throw notFound();
                    }
                    CookieProcessor cookieProcessor = context.getCookieProcessor();
                    if (cookieProcessor == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = cookieProcessor;
                }
                case "sessionIdGenerator" -> {
                    // A manager holds at most one session id generator,
                    // addressed as index 0.
                    if (!(current instanceof Manager manager) || index(value) != 0) {
                        throw notFound();
                    }
                    SessionIdGenerator generator = manager.getSessionIdGenerator();
                    if (generator == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = generator;
                }
                case "namingResources" -> {
                    // The server and each context hold exactly one
                    // NamingResourcesImpl, addressed as index 0.
                    if (index(value) != 0) {
                        throw notFound();
                    }
                    NamingResourcesImpl namingResources = null;
                    if (current instanceof StandardServer s) {
                        namingResources = s.getGlobalNamingResources();
                    } else if (current instanceof Context context) {
                        namingResources = context.getNamingResources();
                    }
                    if (namingResources == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = namingResources;
                }
                case "resource" -> {
                    if (!(current instanceof NamingResourcesImpl namingResources) ||
                            namingResources.findResource(value) == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = namingResources.findResource(value);
                }
                case "resourceLink" -> {
                    if (!(current instanceof NamingResourcesImpl namingResources) ||
                            namingResources.findResourceLink(value) == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = namingResources.findResourceLink(value);
                }
                case "resourceEnvRef" -> {
                    if (!(current instanceof NamingResourcesImpl namingResources) ||
                            namingResources.findResourceEnvRef(value) == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = namingResources.findResourceEnvRef(value);
                }
                case "environment" -> {
                    if (!(current instanceof NamingResourcesImpl namingResources) ||
                            namingResources.findEnvironment(value) == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = namingResources.findEnvironment(value);
                }
                case "ejb" -> {
                    if (!(current instanceof NamingResourcesImpl namingResources) ||
                            namingResources.findEjb(value) == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = namingResources.findEjb(value);
                }
                case "localEjb" -> {
                    if (!(current instanceof NamingResourcesImpl namingResources) ||
                            namingResources.findLocalEjb(value) == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = namingResources.findLocalEjb(value);
                }
                case "serviceRef" -> {
                    if (!(current instanceof NamingResourcesImpl namingResources) ||
                            namingResources.findService(value) == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = namingResources.findService(value);
                }
                case "cluster" -> {
                    if (!(current instanceof Container container) || ownCluster(container) == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = container.getCluster();
                }
                case "channel" -> {
                    if (!(current instanceof CatalinaCluster cluster) || index(value) != 0) {
                        throw notFound();
                    }
                    Channel channel = cluster.getChannel();
                    if (channel == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = channel;
                }
                case "membership" -> {
                    if (!(current instanceof ManagedChannel managed) || index(value) != 0) {
                        throw notFound();
                    }
                    MembershipService membership = managed.getMembershipService();
                    if (membership == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = membership;
                }
                case "sender" -> {
                    if (!(current instanceof ManagedChannel managed) || index(value) != 0) {
                        throw notFound();
                    }
                    ChannelSender sender = managed.getChannelSender();
                    if (sender == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = sender;
                }
                case "transport" -> {
                    if (!(current instanceof ReplicationTransmitter transmitter) || index(value) != 0) {
                        throw notFound();
                    }
                    MultiPointSender transport = transmitter.getTransport();
                    if (transport == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = transport;
                }
                case "receiver" -> {
                    if (!(current instanceof ManagedChannel managed) || index(value) != 0) {
                        throw notFound();
                    }
                    ChannelReceiver receiver = managed.getChannelReceiver();
                    if (receiver == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = receiver;
                }
                case "interceptor" -> {
                    if (!(current instanceof ManagedChannel managed)) {
                        throw notFound();
                    }
                    int wantedInterceptor = index(value);
                    Iterator<ChannelInterceptor> interceptorIter = managed.getInterceptors();
                    int interceptorIndex = 0;
                    ChannelInterceptor foundInterceptor = null;
                    while (interceptorIter.hasNext()) {
                        ChannelInterceptor interceptor = interceptorIter.next();
                        if (interceptorIndex == wantedInterceptor) {
                            foundInterceptor = interceptor;
                            break;
                        }
                        interceptorIndex++;
                    }
                    if (foundInterceptor == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = foundInterceptor;
                }
                case "clusterValve" -> {
                    if (!(current instanceof CatalinaCluster cluster)) {
                        throw notFound();
                    }
                    Valve[] clusterValves = cluster.getValves();
                    int valveIndex = index(value);
                    if (valveIndex < 0 || valveIndex >= clusterValves.length) {
                        throw notFound();
                    }
                    parent = current;
                    current = clusterValves[valveIndex];
                }
                case "deployer" -> {
                    if (!(current instanceof CatalinaCluster cluster) || index(value) != 0) {
                        throw notFound();
                    }
                    ClusterDeployer deployer = cluster.getClusterDeployer();
                    if (deployer == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = deployer;
                }
                case "clusterManager" -> {
                    if (!(current instanceof SimpleTcpCluster tcp) || index(value) != 0) {
                        throw notFound();
                    }
                    ClusterManager manager = tcp.getManagerTemplate();
                    if (manager == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = manager;
                }
                case "clusterListener" -> {
                    if (!(current instanceof SimpleTcpCluster tcp)) {
                        throw notFound();
                    }
                    int wantedListener = index(value);
                    int listenerIndex = 0;
                    ClusterListener foundListener = null;
                    for (ClusterListener listener : tcp.findClusterListeners()) {
                        if (listener == tcp.getClusterDeployer()) {
                            continue;
                        }
                        if (listenerIndex == wantedListener) {
                            foundListener = listener;
                            break;
                        }
                        listenerIndex++;
                    }
                    if (foundListener == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = foundListener;
                }
                case "localMember" -> {
                    Member foundLocalMember = null;
                    if (current instanceof StaticMembershipService sms) {
                        foundLocalMember = sms.getLocalMember(false);
                    } else if (current instanceof StaticMembershipInterceptor smi) {
                        foundLocalMember = smi.getLocalMember(false);
                    }
                    if (foundLocalMember == null) {
                        throw notFound();
                    }
                    parent = current;
                    current = foundLocalMember;
                }
                case "member" -> {
                    if (!(current instanceof StaticMembershipService sms)) {
                        throw notFound();
                    }
                    List<StaticMember> staticMembers = sms.getStaticMembers();
                    int memberIndex = index(value);
                    if (memberIndex < 0 || memberIndex >= staticMembers.size()) {
                        throw notFound();
                    }
                    parent = current;
                    current = staticMembers.get(memberIndex);
                }
                default -> throw notFound();
            }

            type = "localMember".equals(kind) ? "member" : kind;
        }

        return new NodeRef(current, parent, type, null, id);
    }


    private static ConfigException notFound() {
        return new ConfigException(HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND",
                Strings.sm().getString("manager2.configNotFound"));
    }


    private static int index(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }


    // ------------------------------------------------- Attribute updating


    private void updateAttribute(HttpServletResponse response, Map<String, Object> body) throws Exception {

        String id = string(body.get("id"));
        String name = string(body.get("name"));
        if (id == null || name == null || name.isEmpty()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_ID",
                    Strings.sm().getString("manager2.configInvalidId"));
        }

        NodeRef ref = resolve(id);
        if (!"server".equals(ref.type) && ref.component == selfContext &&
                ("name".equals(name) || "path".equals(name))) {
            throw new ConfigException(HttpServletResponse.SC_FORBIDDEN, "SELF_COMPONENT",
                    Strings.sm().getString("manager2.configSelfComponent"));
        }

        // Components without a modeler descriptor take their attributes
        // from the explicit list.
        if (!explicitAttributes(ref.component, ref.type).attributes().isEmpty()) {
            updateExplicitAttribute(response, ref, name, body);
            return;
        }

        ManagedBean descriptor = descriptor(ref.component);
        AttributeInfo attribute = findAttribute(descriptor, name);
        if (attribute == null) {
            throw new ConfigException(HttpServletResponse.SC_NOT_FOUND, "ATTRIBUTE_NOT_FOUND",
                    Strings.sm().getString("manager2.configAttributeNotFound", name));
        }
        if (!attribute.isWriteable() || !EDITABLE_TYPES.contains(attribute.getType())) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "READ_ONLY",
                    Strings.sm().getString("manager2.configReadOnly", name));
        }

        if (RISKY_ATTRIBUTES.contains(name)) {
            String confirm = string(body.get("confirm"));
            // For a context the display name is the (possibly normalized)
            // context path, which is also what the UI asks to be confirmed.
            String expected = displayName(ref.component, ref.type);
            if (!expected.equals(confirm)) {
                throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "CONFIRM_REQUIRED",
                        Strings.sm().getString("manager2.configConfirmRequired", expected));
            }
        }

        Object value = convert(attribute.getType(), body.get("value"));
        setValue(ref.component, attribute, value);

        log(Strings.sm().getString("manager2.configAuditAttribute", name, displayName(ref.component, ref.type),
                String.valueOf(value)));
        Api.ok(response, Strings.sm().getString("manager2.configAttributeUpdated", name, displayName(ref.component, ref.type)));
    }


    /**
     * Update one attribute of a component without a modeler descriptor (the {@code sslHostConfig} and
     * {@code certificate} node types, and the {@code loader}, {@code cookieProcessor} and {@code sessionIdGenerator}
     * node types of the standard implementations).
     */
    private void updateExplicitAttribute(HttpServletResponse response, NodeRef ref, String name,
            Map<String, Object> body) throws Exception {

        Object component = ref.component;
        boolean namingEntry = isNamingEntry(ref.type);
        ResourceBase entry = namingEntry ? (ResourceBase) component : null;
        String oldEntryName = entry == null ? null : entry.getName();

        // Renaming a JNDI entry changes its JNDI name; require the same
        // type-to-confirm as the other renames.
        if (namingEntry && RISKY_ATTRIBUTES.contains(name)) {
            String confirm = string(body.get("confirm"));
            String expected = displayName(component, ref.type);
            if (!expected.equals(confirm)) {
                throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "CONFIRM_REQUIRED",
                        Strings.sm().getString("manager2.configConfirmRequired", expected));
            }
        }

        ExplicitAttribute attribute = findExplicitAttribute(component, ref.type, name);
        boolean param = false;
        String oldParamValue = null;
        if (attribute == null) {
            // A free form string parameter of a JNDI entry: any parameter
            // name is accepted, the JNDI factory decides which ones it
            // consumes.
            if (!namingEntry) {
                throw new ConfigException(HttpServletResponse.SC_NOT_FOUND, "ATTRIBUTE_NOT_FOUND",
                        Strings.sm().getString("manager2.configAttributeNotFound", name));
            }
            param = true;
            Object current = entry.getProperty(name);
            oldParamValue = current == null ? null : String.valueOf(current);
            attribute = new ExplicitAttribute(name, "java.lang.String", true, true);
        } else if (!attribute.isWritable()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "READ_ONLY",
                    Strings.sm().getString("manager2.configReadOnly", name));
        }

        Object value;
        if (attribute.isParam()) {
            // Parameters (the free form parameters of a JNDI entry and
            // the typed factory options) are stored as string properties
            // of the entry: keep the raw string form (an empty value
            // removes the parameter, see setExplicitValue) and validate
            // it against the declared type of the option (free form
            // parameters are strings and always pass).
            String paramValue = string(body.get("value"));
            if (paramValue != null && !paramValue.isEmpty()) {
                validateParamValue(name, paramValue, attribute.getType());
            }
            value = paramValue;
        } else {
            value = convert(attribute.getType(), body.get("value"));
        }
        Object oldValue = param ? null : readExplicitValue(component, attribute);
        setExplicitValue(component, attribute, value);

        // A JNDI entry update is only visible to the live JNDI
        // environment (the NamingContextListener reacts to the property
        // change events) when the entry is removed and re-added.
        if (namingEntry) {
            try {
                rebindEntry(ref, oldEntryName);
            } catch (Exception e) {
                // Roll the change back and re-register the entry as it
                // was before the update.
                try {
                    if (param) {
                        if (oldParamValue == null) {
                            entry.removeProperty(name);
                        } else {
                            entry.setProperty(name, oldParamValue);
                        }
                    } else {
                        setExplicitValue(component, attribute, oldValue);
                    }
                    entry.setName(oldEntryName);
                    rebindEntry(ref, oldEntryName);
                } catch (Exception rollbackError) {
                    log(Strings.sm().getString("manager2.error.config"), rollbackError);
                }
                throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "UPDATE_FAILED",
                        Strings.sm().getString("manager2.configSetFailed", name, rootMessage(e)));
            }
        }

        // On a running, TLS enabled connector the change is applied (and
        // validated) at once by re-creating the SSL context of the
        // affected host configuration. When the new value does not
        // validate the previous value is restored.
        SSLHostConfig sslHostConfig = null;
        Connector connector = null;
        if ("sslHostConfig".equals(ref.type)) {
            sslHostConfig = (SSLHostConfig) ref.component;
            if (ref.parent instanceof Connector parent) {
                connector = parent;
            }
        } else if ("certificate".equals(ref.type) && ref.parent instanceof SSLHostConfig parent) {
            sslHostConfig = parent;
            connector = connectorOfSsl(sslHostConfig);
        }
        if (connector != null && connector.getState().isAvailable() && isSslEnabled(connector)) {
            try {
                endpointOf(connector).reloadSslHostConfig(sslHostConfig.getHostName());
            } catch (Exception e) {
                setExplicitValue(ref.component, attribute, oldValue);
                throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "UPDATE_FAILED", Strings.sm().getString(
                        "manager2.configSslReloadFailed", name, displayName(ref.component, ref.type), rootMessage(e)));
            }
        }

        log(Strings.sm().getString("manager2.configAuditAttribute", name, displayName(ref.component, ref.type),
                String.valueOf(value)));
        Api.ok(response, Strings.sm().getString("manager2.configAttributeUpdated", name, displayName(ref.component, ref.type)));
    }


    /**
     * Re-register a JNDI entry whose state has changed in place: remove it (by the name it was registered under) and
     * add it again. The remove and add fire the property change events that the {@code NamingContextListener} uses to
     * update the live JNDI environment.
     */
    private void rebindEntry(NodeRef ref, String registeredName) throws Exception {
        NamingResourcesImpl namingResources = (NamingResourcesImpl) ref.parent;
        ResourceBase entry = (ResourceBase) ref.component;
        String newName = entry.getName();
        boolean renamed = !registeredName.equals(newName);
        if (renamed) {
            // The removal event carries the entry and the NamingContextListener
            // unbinds the JNDI name the entry reports. Restore the name the
            // entry was registered under so the old binding (not the new one)
            // is the one that is removed.
            entry.setName(registeredName);
        }
        try {
            removeNamingEntry(namingResources, ref.type, registeredName);
        } finally {
            if (renamed) {
                entry.setName(newName);
            }
        }
        addNamingEntryTo(namingResources, ref.type, entry);
        // The add is a silent no-op when the (new) name is already used
        // by another entry; detect that so the caller can roll back.
        if (findNamingEntry(namingResources, ref.type, newName) != entry) {
            throw new IllegalStateException(Strings.sm().getString("manager2.configJndiNameTaken", newName));
        }
    }


    private static ResourceBase findNamingEntry(NamingResourcesImpl namingResources, String type, String name) {
        return switch (type) {
            case "resource" -> namingResources.findResource(name);
            case "resourceLink" -> namingResources.findResourceLink(name);
            case "resourceEnvRef" -> namingResources.findResourceEnvRef(name);
            case "environment" -> namingResources.findEnvironment(name);
            case "ejb" -> namingResources.findEjb(name);
            case "localEjb" -> namingResources.findLocalEjb(name);
            case "serviceRef" -> namingResources.findService(name);
            default -> null;
        };
    }


    private static void removeNamingEntry(NamingResourcesImpl namingResources, String type, String name) {
        switch (type) {
            case "resource" -> namingResources.removeResource(name);
            case "resourceLink" -> namingResources.removeResourceLink(name);
            case "resourceEnvRef" -> namingResources.removeResourceEnvRef(name);
            case "environment" -> namingResources.removeEnvironment(name);
            case "ejb" -> namingResources.removeEjb(name);
            case "localEjb" -> namingResources.removeLocalEjb(name);
            case "serviceRef" -> namingResources.removeService(name);
            default -> {
            }
        }
    }


    private static void addNamingEntryTo(NamingResourcesImpl namingResources, String type, ResourceBase entry) {
        switch (type) {
            case "resource" -> namingResources.addResource((ContextResource) entry);
            case "resourceLink" -> namingResources.addResourceLink((ContextResourceLink) entry);
            case "resourceEnvRef" -> namingResources.addResourceEnvRef((ContextResourceEnvRef) entry);
            case "environment" -> namingResources.addEnvironment((ContextEnvironment) entry);
            case "ejb" -> namingResources.addEjb((ContextEjb) entry);
            case "localEjb" -> namingResources.addLocalEjb((ContextLocalEjb) entry);
            case "serviceRef" -> namingResources.addService((ContextService) entry);
            default -> {
            }
        }
    }


    /**
     * Convert a JSON value to the Java type of the attribute.
     */
    private static Object convert(String type, Object json) throws ConfigException {
        String message = Strings.sm().getString("manager2.configInvalidValue", type);
        try {
            switch (type) {
                case "boolean":
                    if (json instanceof Boolean b) {
                        return b;
                    }
                    return Boolean.valueOf(Boolean.parseBoolean(String.valueOf(json)));
                case "int":
                    return Integer.valueOf(Integer.parseInt(String.valueOf(json).trim()));
                case "long":
                    return Long.valueOf(Long.parseLong(String.valueOf(json).trim()));
                case "short":
                    return Short.valueOf(Short.parseShort(String.valueOf(json).trim()));
                case "byte":
                    return Byte.valueOf(Byte.parseByte(String.valueOf(json).trim()));
                case "float":
                    return Float.valueOf(Float.parseFloat(String.valueOf(json).trim()));
                case "double":
                    return Double.valueOf(Double.parseDouble(String.valueOf(json).trim()));
                case "java.lang.String":
                    return String.valueOf(json);
                case "[Ljava.lang.String;":
                    if (json instanceof List<?> list) {
                        String[] result = new String[list.size()];
                        for (int i = 0; i < list.size(); i++) {
                            result[i] = String.valueOf(list.get(i));
                        }
                        return result;
                    }
                    return String.valueOf(json).split(",");
                default:
                    throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "UNSUPPORTED_TYPE", message);
            }
        } catch (NumberFormatException e) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_VALUE", message);
        }
    }


    private static void setValue(Object component, AttributeInfo attribute, Object value) throws ConfigException {

        String method = attribute.getSetMethod();
        if (method == null) {
            method = "set" + capitalize(attribute.getName());
        }
        try {
            Method m = component.getClass().getMethod(method, classForType(attribute.getType()));
            m.invoke(component, value);
        } catch (NoSuchMethodException e) {
            throw new ConfigException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "NO_SETTER",
                    Strings.sm().getString("manager2.configNoSetter", method));
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "SET_FAILED",
                    Strings.sm().getString("manager2.configSetFailed", attribute.getName(),
                            t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
        } catch (IllegalAccessException e) {
            throw new ConfigException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "NO_SETTER",
                    Strings.sm().getString("manager2.configNoSetter", method));
        }
    }


    private static Class<?> classForType(String type) {
        switch (type) {
            case "boolean":
                return boolean.class;
            case "int":
                return int.class;
            case "long":
                return long.class;
            case "short":
                return short.class;
            case "byte":
                return byte.class;
            case "float":
                return float.class;
            case "double":
                return double.class;
            case "java.lang.String":
                return String.class;
            case "[Ljava.lang.String;":
                return String[].class;
            default:
                try {
                    return Class.forName(type);
                } catch (ClassNotFoundException e) {
                    return Object.class;
                }
        }
    }


    // ----------------------------------------------------- Structural ops


    /**
     * Register a new component (the {@code add} operation) and verify that it actually started. The register operations
     * of the core components report a failed start inconsistently: some throw, some log and leave a failed component
     * behind, the valve pipeline even cleans up silently. All cases are handled here: on a failed start the
     * {@code undo} operation is attempted and a controlled error is raised, so that no broken component is left
     * registered.
     */
    private void addChecked(String label, Object component, Runnable add, Runnable undo) throws ConfigException {
        try {
            add.run();
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.config"), e);
            rollback(label, undo);
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "START_FAILED",
                    Strings.sm().getString("manager2.configStartFailed", label, rootMessage(e)));
        }
        if (component instanceof Lifecycle lifecycle && !lifecycle.getState().isAvailable()) {
            rollback(label, undo);
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "START_FAILED",
                    Strings.sm().getString("manager2.configStartFailed", label, Strings.sm().getString("manager2.configNotStarted")));
        }
    }


    private void rollback(String label, Runnable undo) {
        try {
            undo.run();
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.configRollbackFailed", label,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        }
    }


    /**
     * The message of the deepest cause of the given exception.
     */
    private static String rootMessage(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return (message == null || message.isEmpty()) ? current.getClass().getSimpleName() : message;
    }


    private void addChild(HttpServletResponse response, Map<String, Object> body) throws Exception {

        String parentId = string(body.get("parent"));
        String type = string(body.get("type"));
        if (parentId == null || type == null) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_ID",
                    Strings.sm().getString("manager2.configInvalidId"));
        }
        NodeRef parent = resolve(parentId);

        // The only child of an SSL host configuration is a certificate,
        // and in its request body the "type" field carries the
        // certificate type (RSA, DSA, ...), not a child component type.
        if (parent.component instanceof SSLHostConfig) {
            addCertificate(response, parent, body);
            return;
        }

        switch (type) {
            case "service" -> addService(response, parent, body);
            case "host" -> addHost(response, parent, body);
            case "context" -> addContext(response, parent, body);
            case "wrapper" -> addWrapper(response, parent, body);
            case "valve" -> addValve(response, parent, body);
            case "connector" -> addConnector(response, parent, body);
            case "executor" -> addExecutor(response, parent, body);
            case "alias" -> addAlias(response, parent, body);
            case "listener" -> addListener(response, parent, body);
            case "realm" -> addRealm(response, parent, body);
            case "manager" -> addContextComponent(response, parent, body, "manager");
            case "resources" -> addContextComponent(response, parent, body, "resources");
            case "loader" -> addContextComponent(response, parent, body, "loader");
            case "cookieProcessor" -> addContextComponent(response, parent, body, "cookieProcessor");
            case "sessionIdGenerator" -> addSessionIdGenerator(response, parent, body);
            case "resource" -> addNamingEntry(response, parent, body, "resource");
            case "resourceLink" -> addNamingEntry(response, parent, body, "resourceLink");
            case "resourceEnvRef" -> addNamingEntry(response, parent, body, "resourceEnvRef");
            case "environment" -> addNamingEntry(response, parent, body, "environment");
            case "ejb" -> addNamingEntry(response, parent, body, "ejb");
            case "localEjb" -> addNamingEntry(response, parent, body, "localEjb");
            case "serviceRef" -> addNamingEntry(response, parent, body, "serviceRef");
            case "sslHostConfig" -> addSslHostConfig(response, parent, body);
            case "upgradeProtocol" -> addUpgradeProtocol(response, parent, body);
            case "certificate" -> addCertificate(response, parent, body);
            case "cluster" -> addCluster(response, parent, body);
            case "clusterValve" -> addClusterValve(response, parent, body);
            case "channel" -> addClusterChannel(response, parent, body);
            case "membership" -> addClusterMembership(response, parent, body);
            case "sender" -> addClusterSender(response, parent, body);
            case "receiver" -> addClusterReceiver(response, parent, body);
            case "interceptor" -> addInterceptor(response, parent, body);
            case "deployer" -> addClusterDeployer(response, parent, body);
            case "clusterManager" -> addClusterManager(response, parent, body);
            case "transport" -> addClusterTransport(response, parent, body);
            case "clusterListener" -> addClusterListener(response, parent, body);
            default -> throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "UNSUPPORTED_TYPE",
                    Strings.sm().getString("manager2.configTypeUnsupported", type));
        }
    }


    private void addAlias(HttpServletResponse response, NodeRef parent, Map<String, Object> body) throws Exception {

        if (!(parent.component instanceof Host host)) {
            throw badParent("alias");
        }
        String alias = string(body.get("alias"));
        if (alias == null || alias.isEmpty() || alias.contains(" ") || alias.contains(",")) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_NAME",
                    Strings.sm().getString("manager2.configInvalidName", alias));
        }
        if (Arrays.asList(host.findAliases()).contains(alias)) {
            throw duplicate(alias);
        }
        host.addAlias(alias);
        log(Strings.sm().getString("manager2.configAuditAdd", "alias", alias));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", alias));
    }


    /**
     * A service in this Tomcat version holds exactly one engine (its {@code container}), so adding a service creates
     * the service together with a new engine of the same name.
     */
    private void addService(HttpServletResponse response, NodeRef parent, Map<String, Object> body) throws Exception {

        if (!(parent.component instanceof Server)) {
            throw badParent("service");
        }
        String name = requiredName(body.get("name"));
        if (server.findService(name) != null) {
            throw duplicate(name);
        }
        StandardService service = new StandardService();
        service.setName(name);
        StandardEngine engine = new StandardEngine();
        engine.setName(name);
        engine.setRealm(new MemoryRealm());
        service.setContainer(engine);
        Server serverComponent = (Server) parent.component;
        addChecked(name, service, () -> serverComponent.addService(service),
                () -> serverComponent.removeService(service));
        log(Strings.sm().getString("manager2.configAuditAdd", "service", name));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", name));
    }


    private void addHost(HttpServletResponse response, NodeRef parent, Map<String, Object> body) throws Exception {

        if (!(parent.component instanceof StandardEngine engine)) {
            throw badParent("host");
        }
        String name = requiredName(body.get("name"));
        if (engine.findChild(name) instanceof Host) {
            throw duplicate(name);
        }

        String appBase = string(body.get("appBase"));
        if (appBase == null || appBase.isEmpty()) {
            appBase = name;
        }
        File appBaseFile = new File(appBase);
        if (!appBaseFile.isAbsolute()) {
            appBaseFile = new File(engine.getCatalinaBase(), appBaseFile.getPath());
        }
        appBaseFile = appBaseFile.getCanonicalFile();
        if (!appBaseFile.getPath().startsWith(engine.getCatalinaBase().getPath() + File.separator) &&
                !appBaseFile.getPath().equals(engine.getCatalinaBase().getPath())) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH",
                    Strings.sm().getString("manager2.configInvalidPath", appBase));
        }
        if (!appBaseFile.mkdirs() && !appBaseFile.isDirectory()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH",
                    Strings.sm().getString("manager2.configInvalidPath", appBase));
        }

        StandardHost host = new StandardHost();
        host.setName(name);
        host.setAppBase(appBase);
        host.addLifecycleListener(new HostConfig());
        for (String alias : stringList(body.get("aliases"))) {
            host.addAlias(alias);
        }
        host.setAutoDeploy(bool(body.get("autoDeploy"), true));
        host.setDeployOnStartup(bool(body.get("deployOnStartup"), true));
        host.setDeployXML(bool(body.get("deployXML"), true));
        host.setUnpackWARs(bool(body.get("unpackWARs"), true));
        host.setCopyXML(bool(body.get("copyXML"), false));
        addChecked(name, host, () -> engine.addChild(host), () -> engine.removeChild(host));
        log(Strings.sm().getString("manager2.configAuditAdd", "host", name));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", name));
    }


    private void addContext(HttpServletResponse response, NodeRef parent, Map<String, Object> body) throws Exception {

        if (!(parent.component instanceof Host)) {
            throw badParent("context");
        }
        Host host = (Host) parent.component;
        String path = string(body.get("path"));
        if (path == null || !path.startsWith("/") || path.length() < 2 || path.contains("..") || path.contains(" ")) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH",
                    Strings.sm().getString("manager2.configInvalidPath", path));
        }
        if (host.findChild(path) instanceof Context) {
            throw duplicate(path);
        }

        StandardContext context = new StandardContext();
        context.setPath(path);
        context.setName(path);
        // A docBase is interpreted exactly like in server.xml: a relative
        // value is resolved against the appBase of the host. The default
        // (as for HostConfig) is the context path below the appBase.
        String docBase = string(body.get("docBase"));
        if (docBase != null && !docBase.isEmpty()) {
            context.setDocBase(docBase);
        } else {
            context.setDocBase(path.length() > 1 ? path.substring(1) : "ROOT");
        }
        context.setParent(host);
        File docBaseFile = contextDocBaseFile(context);
        if (docBaseFile != null && !docBaseFile.getName().endsWith(".war") && !docBaseFile.isDirectory() &&
                !docBaseFile.mkdirs()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH",
                    Strings.sm().getString("manager2.configDocBaseMissing", docBaseFile.getPath()));
        }
        String displayName = string(body.get("displayName"));
        if (displayName != null && !displayName.isEmpty()) {
            context.setDisplayName(displayName);
        }
        context.addLifecycleListener(new ContextConfig());
        addChecked(path, context, () -> host.addChild(context), () -> host.removeChild(context));
        log(Strings.sm().getString("manager2.configAuditAdd", "context", path));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", path));
    }


    /**
     * The resolved location of the context document base (a relative {@code docBase} is resolved against the appBase of
     * the host).
     */
    private static File contextDocBaseFile(StandardContext context) {
        File file = new File(context.getDocBase());
        if (!file.isAbsolute() && context.getParent() instanceof Host host) {
            file = new File(host.getAppBaseFile(), file.getPath());
        }
        return file;
    }


    private void addWrapper(HttpServletResponse response, NodeRef parent, Map<String, Object> body) throws Exception {

        if (!(parent.component instanceof Context)) {
            throw badParent("wrapper");
        }
        Context context = (Context) parent.component;
        String servletClass = string(body.get("servletClass"));
        if (servletClass == null || servletClass.isEmpty()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_NAME",
                    Strings.sm().getString("manager2.configInvalidName", "servletClass"));
        }
        String name = string(body.get("name"));
        if (name == null || name.isEmpty()) {
            name = servletClass;
        }
        if (!SAFE_NAME.matcher(name).matches()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_NAME",
                    Strings.sm().getString("manager2.configInvalidName", name));
        }
        if (context.findChild(name) instanceof Wrapper) {
            throw duplicate(name);
        }

        StandardWrapper wrapper = new StandardWrapper();
        wrapper.setName(name);
        wrapper.setServletClass(servletClass);
        wrapper.setLoadOnStartup(intValue(body.get("loadOnStartup"), 0));
        addChecked(name, wrapper, () -> context.addChild(wrapper), () -> context.removeChild(wrapper));
        for (String pattern : stringList(body.get("urlPatterns"))) {
            context.addServletMapping(pattern, name);
        }
        log(Strings.sm().getString("manager2.configAuditAdd", "wrapper", name));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", name));
    }


    private void addValve(HttpServletResponse response, NodeRef parent, Map<String, Object> body) throws Exception {

        if (!(parent.component instanceof Container)) {
            throw badParent("valve");
        }
        String className = string(body.get("className"));
        if (className == null || className.isEmpty()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_NAME",
                    Strings.sm().getString("manager2.configInvalidName", "className"));
        }
        Valve valve;
        try {
            // Valves are server level classes: never use the webapp class
            // loader.
            valve = (Valve) Class.forName(className, true, server.getClass().getClassLoader()).getConstructor()
                    .newInstance();
        } catch (Exception e) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_CLASS",
                    Strings.sm().getString("manager2.configInvalidClass", className));
        }
        Pipeline pipeline = ((Container) parent.component).getPipeline();
        addChecked(className, valve, () -> pipeline.addValve(valve), () -> pipeline.removeValve(valve));
        log(Strings.sm().getString("manager2.configAuditAdd", "valve", className));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", className));
    }


    private void addListener(HttpServletResponse response, NodeRef parent, Map<String, Object> body) throws Exception {

        // Every component that implements Lifecycle accepts a lifecycle
        // listener (containers, valves, connectors, executors, ...). An
        // alias is excluded: it is just a name on the host, not a
        // component that holds listeners.
        if ("alias".equals(parent.type) || !(parent.component instanceof Lifecycle lifecycle)) {
            throw badParent("listener");
        }
        String className = string(body.get("className"));
        if (className == null || className.isEmpty()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_NAME",
                    Strings.sm().getString("manager2.configInvalidName", "className"));
        }
        LifecycleListener listener;
        try {
            // Listeners are server level classes: never use the webapp
            // class loader.
            listener = (LifecycleListener) Class.forName(className, true, server.getClass().getClassLoader())
                    .getConstructor().newInstance();
        } catch (Exception e) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_CLASS",
                    Strings.sm().getString("manager2.configInvalidClass", className));
        }
        // A listener is registered, not started: the parent never drives
        // its lifecycle (a listener that is itself a Lifecycle, such as a
        // valve used as a listener, legitimately stays not started).
        try {
            lifecycle.addLifecycleListener(listener);
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.config"), e);
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "ADD_FAILED",
                    Strings.sm().getString("manager2.configAddFailed", className, rootMessage(e)));
        }
        log(Strings.sm().getString("manager2.configAuditAdd", "listener", className));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", className));
    }


    // ------------------------------------------- Cluster components


    /**
     * Instantiate a cluster component (a class from the clustering jars) using the server class loader, or a sensible
     * default when no class name is given.
     */
    private <T> T newClusterComponent(String className, String defaultClassName, Class<T> type) throws ConfigException {
        String name = (className == null || className.isEmpty()) ? defaultClassName : className;
        try {
            return type
                    .cast(Class.forName(name, true, server.getClass().getClassLoader()).getConstructor().newInstance());
        } catch (Exception e) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_CLASS",
                    Strings.sm().getString("manager2.configInvalidClass", name));
        }
    }


    /**
     * Add a cluster to a container (engine, host or context). The cluster is a lifecycle component: when the container
     * is running, {@code setCluster} starts it (which starts the channel and applies the cluster defaults), so the
     * start is verified and rolled back on failure.
     */
    private void addCluster(HttpServletResponse response, NodeRef parent, Map<String, Object> body) throws Exception {
        if (!(parent.component instanceof Container container)) {
            throw badParent("cluster");
        }
        String className = string(body.get("className"));
        String label = (className == null || className.isEmpty()) ? SimpleTcpCluster.class.getName() : className;
        CatalinaCluster cluster = newClusterComponent(className, SimpleTcpCluster.class.getName(),
                CatalinaCluster.class);
        addChecked(label, cluster, () -> container.setCluster(cluster), () -> container.setCluster(null));
        log(Strings.sm().getString("manager2.configAuditAdd", "cluster", label));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", label));
    }


    /**
     * Add a cluster valve (a {@code Valve} that implements {@code ClusterValve}) to the cluster.
     */
    private void addClusterValve(HttpServletResponse response, NodeRef parent, Map<String, Object> body)
            throws Exception {
        if (!(parent.component instanceof CatalinaCluster cluster)) {
            throw badParent("clusterValve");
        }
        String className = string(body.get("className"));
        if (className == null || className.isEmpty()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_NAME",
                    Strings.sm().getString("manager2.configInvalidName", "className"));
        }
        Valve valve;
        try {
            valve = (Valve) Class.forName(className, true, server.getClass().getClassLoader()).getConstructor()
                    .newInstance();
        } catch (Exception e) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_CLASS",
                    Strings.sm().getString("manager2.configInvalidClass", className));
        }
        if (!(valve instanceof ClusterValve)) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_CLASS",
                    Strings.sm().getString("manager2.configInvalidClass", className));
        }
        try {
            cluster.addValve(valve);
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.config"), e);
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "ADD_FAILED",
                    Strings.sm().getString("manager2.configAddFailed", className, rootMessage(e)));
        }
        log(Strings.sm().getString("manager2.configAuditAdd", "clusterValve", className));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", className));
    }


    /**
     * Replace the channel of the cluster.
     */
    private void addClusterChannel(HttpServletResponse response, NodeRef parent, Map<String, Object> body)
            throws Exception {
        if (!(parent.component instanceof CatalinaCluster cluster)) {
            throw badParent("channel");
        }
        String className = string(body.get("className"));
        String label = (className == null || className.isEmpty()) ? GroupChannel.class.getName() : className;
        Channel channel = newClusterComponent(className, GroupChannel.class.getName(), Channel.class);
        Channel previous = cluster.getChannel();
        try {
            cluster.setChannel(channel);
        } catch (Exception e) {
            cluster.setChannel(previous);
            throw addFailed(label, e);
        }
        log(Strings.sm().getString("manager2.configAuditAdd", "channel", label));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", label));
    }


    /**
     * Replace the membership service of the channel.
     */
    private void addClusterMembership(HttpServletResponse response, NodeRef parent, Map<String, Object> body)
            throws Exception {
        if (!(parent.component instanceof ManagedChannel managed)) {
            throw badParent("membership");
        }
        String className = string(body.get("className"));
        String label = (className == null || className.isEmpty()) ? McastService.class.getName() : className;
        MembershipService membership = newClusterComponent(className, McastService.class.getName(),
                MembershipService.class);
        MembershipService previous = managed.getMembershipService();
        try {
            managed.setMembershipService(membership);
        } catch (Exception e) {
            managed.setMembershipService(previous);
            throw addFailed(label, e);
        }
        log(Strings.sm().getString("manager2.configAuditAdd", "membership", label));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", label));
    }


    /**
     * Replace the sender of the channel.
     */
    private void addClusterSender(HttpServletResponse response, NodeRef parent, Map<String, Object> body)
            throws Exception {
        if (!(parent.component instanceof ManagedChannel managed)) {
            throw badParent("sender");
        }
        String className = string(body.get("className"));
        String label = (className == null || className.isEmpty()) ? ReplicationTransmitter.class.getName() : className;
        ChannelSender sender = newClusterComponent(className, ReplicationTransmitter.class.getName(),
                ChannelSender.class);
        ChannelSender previous = managed.getChannelSender();
        try {
            managed.setChannelSender(sender);
        } catch (Exception e) {
            managed.setChannelSender(previous);
            throw addFailed(label, e);
        }
        log(Strings.sm().getString("manager2.configAuditAdd", "sender", label));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", label));
    }


    /**
     * Replace the receiver of the channel.
     */
    private void addClusterReceiver(HttpServletResponse response, NodeRef parent, Map<String, Object> body)
            throws Exception {
        if (!(parent.component instanceof ManagedChannel managed)) {
            throw badParent("receiver");
        }
        String className = string(body.get("className"));
        String label = (className == null || className.isEmpty())
                ? "org.apache.catalina.tribes.transport.nio.NioReceiver"
                : className;
        ChannelReceiver receiver = newClusterComponent(className,
                "org.apache.catalina.tribes.transport.nio.NioReceiver", ChannelReceiver.class);
        ChannelReceiver previous = managed.getChannelReceiver();
        try {
            managed.setChannelReceiver(receiver);
        } catch (Exception e) {
            managed.setChannelReceiver(previous);
            throw addFailed(label, e);
        }
        log(Strings.sm().getString("manager2.configAuditAdd", "receiver", label));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", label));
    }


    /**
     * Add an interceptor to the channel.
     */
    private void addInterceptor(HttpServletResponse response, NodeRef parent, Map<String, Object> body)
            throws Exception {
        if (!(parent.component instanceof ManagedChannel managed)) {
            throw badParent("interceptor");
        }
        String className = string(body.get("className"));
        if (className == null || className.isEmpty()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_NAME",
                    Strings.sm().getString("manager2.configInvalidName", "className"));
        }
        ChannelInterceptor interceptor = newClusterComponent(className, null, ChannelInterceptor.class);
        try {
            managed.addInterceptor(interceptor);
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.config"), e);
            throw addFailed(className, e);
        }
        log(Strings.sm().getString("manager2.configAuditAdd", "interceptor", className));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", className));
    }


    /**
     * Replace the deployer of the cluster.
     */
    private void addClusterDeployer(HttpServletResponse response, NodeRef parent, Map<String, Object> body)
            throws Exception {
        if (!(parent.component instanceof CatalinaCluster cluster)) {
            throw badParent("deployer");
        }
        String className = string(body.get("className"));
        String label = (className == null || className.isEmpty()) ? "org.apache.catalina.ha.deploy.FarmWarDeployer"
                : className;
        ClusterDeployer deployer = newClusterComponent(className, "org.apache.catalina.ha.deploy.FarmWarDeployer",
                ClusterDeployer.class);
        ClusterDeployer previous = cluster.getClusterDeployer();
        try {
            cluster.setClusterDeployer(deployer);
        } catch (Exception e) {
            cluster.setClusterDeployer(previous);
            throw addFailed(label, e);
        }
        log(Strings.sm().getString("manager2.configAuditAdd", "deployer", label));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", label));
    }


    /**
     * Replace the manager template of the cluster.
     */
    private void addClusterManager(HttpServletResponse response, NodeRef parent, Map<String, Object> body)
            throws Exception {
        if (!(parent.component instanceof SimpleTcpCluster tcp)) {
            throw badParent("clusterManager");
        }
        String className = string(body.get("className"));
        String label = (className == null || className.isEmpty()) ? "org.apache.catalina.ha.session.DeltaManager"
                : className;
        ClusterManager manager = newClusterComponent(className, "org.apache.catalina.ha.session.DeltaManager",
                ClusterManager.class);
        ClusterManager previous = tcp.getManagerTemplate();
        try {
            tcp.setManagerTemplate(manager);
        } catch (Exception e) {
            tcp.setManagerTemplate(previous);
            throw addFailed(label, e);
        }
        log(Strings.sm().getString("manager2.configAuditAdd", "clusterManager", label));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", label));
    }


    /**
     * Replace the transport of the sender (a replication transmitter).
     */
    private void addClusterTransport(HttpServletResponse response, NodeRef parent, Map<String, Object> body)
            throws Exception {
        if (!(parent.component instanceof ReplicationTransmitter transmitter)) {
            throw badParent("transport");
        }
        String className = string(body.get("className"));
        String label = (className == null || className.isEmpty())
                ? "org.apache.catalina.tribes.transport.nio.PooledParallelSender"
                : className;
        MultiPointSender transport = newClusterComponent(className,
                "org.apache.catalina.tribes.transport.nio.PooledParallelSender", MultiPointSender.class);
        MultiPointSender previous = transmitter.getTransport();
        try {
            transmitter.setTransport(transport);
        } catch (Exception e) {
            transmitter.setTransport(previous);
            throw addFailed(label, e);
        }
        log(Strings.sm().getString("manager2.configAuditAdd", "transport", label));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", label));
    }


    /**
     * Add a cluster listener to the cluster.
     */
    private void addClusterListener(HttpServletResponse response, NodeRef parent, Map<String, Object> body)
            throws Exception {
        if (!(parent.component instanceof CatalinaCluster cluster)) {
            throw badParent("clusterListener");
        }
        String className = string(body.get("className"));
        if (className == null || className.isEmpty()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_NAME",
                    Strings.sm().getString("manager2.configInvalidName", "className"));
        }
        ClusterListener listener = newClusterComponent(className, null, ClusterListener.class);
        try {
            cluster.addClusterListener(listener);
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.config"), e);
            throw addFailed(className, e);
        }
        log(Strings.sm().getString("manager2.configAuditAdd", "clusterListener", className));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", className));
    }


    private static ConfigException addFailed(String label, Exception e) {
        return new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "ADD_FAILED",
                Strings.sm().getString("manager2.configAddFailed", label, rootMessage(e)));
    }


    /**
     * Add a realm: to a container (the container's own realm) or to a combined realm (a sub realm). The realm class is
     * a server level class and is identified by its class name.
     */
    private void addRealm(HttpServletResponse response, NodeRef parent, Map<String, Object> body) throws Exception {

        String className = string(body.get("className"));
        if (className == null || className.isEmpty()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_NAME",
                    Strings.sm().getString("manager2.configInvalidName", "className"));
        }
        Realm realm;
        try {
            // Realms are server level classes: never use the webapp class
            // loader.
            realm = (Realm) Class.forName(className, true, server.getClass().getClassLoader()).getConstructor()
                    .newInstance();
        } catch (Exception e) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_CLASS",
                    Strings.sm().getString("manager2.configInvalidClass", className));
        }

        if (parent.component instanceof Container container) {
            addContainerRealm(response, container, realm, className);
        } else if (parent.component instanceof CombinedRealm combined) {
            addSubRealm(response, combined, realm, className);
        } else {
            throw badParent("realm");
        }
    }


    private void addContainerRealm(HttpServletResponse response, Container container, Realm realm, String className)
            throws Exception {

        if (ownRealm(container) != null) {
            throw duplicate(className);
        }
        Realm oldRealm = container.getRealm();
        boolean running = container.getState().isAvailable();
        try {
            // setRealm wires the realm to the container and, on a running
            // container, starts it.
            container.setRealm(realm);
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.config"), e);
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "ADD_FAILED",
                    Strings.sm().getString("manager2.configAddFailed", className, rootMessage(e)));
        }
        // A realm that does not start on a running container is not
        // useful; roll the change back. On a stopped container the realm
        // is started when the container starts, so no check is needed.
        if (running && realm instanceof Lifecycle lifecycle && !lifecycle.getState().isAvailable()) {
            container.setRealm(oldRealm);
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "START_FAILED",
                    Strings.sm().getString("manager2.configStartFailed", className, Strings.sm().getString("manager2.configNotStarted")));
        }
        log(Strings.sm().getString("manager2.configAuditAdd", "realm", className));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", className));
    }


    private void addSubRealm(HttpServletResponse response, CombinedRealm combined, Realm realm, String className)
            throws Exception {

        // The XML parser accepts at most this many nested Realm elements;
        // keep runtime additions within the same bound so that the state
        // still round trips through server.xml.
        if (nestedRealmDepth(combined) >= MAX_NESTED_REALM_LEVELS) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_VALUE",
                    Strings.sm().getString("manager2.configMaxNestedRealms"));
        }
        boolean running = combined.getState().isAvailable();
        try {
            combined.addRealm(realm);
            // Mirror what the XML flow achieves when the container
            // attaches the combined realm: give the sub realm a container
            // (for logging) and a distinct realm path (for JMX naming),
            // and start it while the combined realm is running (the
            // combined realm only starts sub realms at its own start).
            Container owner = combined.getContainer();
            if (owner != null) {
                realm.setContainer(owner);
                if (realm instanceof RealmBase realmBase) {
                    realmBase
                            .setRealmPath(combined.getRealmPath() + "/realm" + (combined.getNestedRealms().length - 1));
                }
            }
            if (running && realm instanceof Lifecycle lifecycle) {
                lifecycle.start();
            }
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.config"), e);
            combined.removeRealm(realm);
            if (realm instanceof Lifecycle lifecycle && lifecycle.getState().isAvailable()) {
                try {
                    lifecycle.stop();
                } catch (Exception ignored) {
                    // Best effort; the removal failure is already logged.
                }
            }
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "ADD_FAILED",
                    Strings.sm().getString("manager2.configAddFailed", className, rootMessage(e)));
        }
        log(Strings.sm().getString("manager2.configAuditAdd", "realm", className));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", className));
    }


    /**
     * The number of realm levels in the chain starting at the given realm (the realm itself counts as one), following
     * the first sub realm of each combined realm.
     */
    private static int nestedRealmDepth(Realm realm) {
        int depth = 0;
        for (Realm current = realm; current != null; current = firstSubRealm(current)) {
            depth++;
        }
        return depth;
    }


    private static Realm firstSubRealm(Realm realm) {
        if (!(realm instanceof CombinedRealm combined)) {
            return null;
        }
        Realm[] nested = combined.getNestedRealms();
        return nested.length > 0 ? nested[0] : null;
    }


    /**
     * Add (replace) one of the sub components that a context holds exactly one of: the manager, the resources, the
     * loader or the cookie processor. A context always has one of each (the defaults are created at context start), so
     * the current instance is replaced by a new instance of the given class. The component class is a server level
     * class and is identified by its class name.
     */
    private void addContextComponent(HttpServletResponse response, NodeRef parent, Map<String, Object> body,
            String type) throws Exception {

        if (!(parent.component instanceof Context context)) {
            throw badParent(type);
        }
        // Replacing the session manager (or the class loader) of this
        // web application's own context would destroy the admin session
        // (or the classes of the running application) mid-request.
        if (context == selfContext && ("manager".equals(type) || "loader".equals(type))) {
            throw self();
        }
        String className = string(body.get("className"));
        if (className == null || className.isEmpty()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_NAME",
                    Strings.sm().getString("manager2.configInvalidName", "className"));
        }
        Object component;
        try {
            // These are server level classes: never use the webapp class
            // loader.
            Object instance = Class.forName(className, true, server.getClass().getClassLoader()).getConstructor()
                    .newInstance();
            switch (type) {
                case "manager" -> component = (Manager) instance;
                case "resources" -> component = (WebResourceRoot) instance;
                case "loader" -> component = (Loader) instance;
                default -> component = (CookieProcessor) instance;
            }
        } catch (Exception e) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_CLASS",
                    Strings.sm().getString("manager2.configInvalidClass", className));
        }

        boolean running = context.getState().isAvailable();
        switch (type) {
            case "manager" -> {
                Manager oldManager = context.getManager();
                try {
                    // setManager wires the manager to the context and, on
                    // a running context, stops the old manager and starts
                    // the new one.
                    context.setManager((Manager) component);
                } catch (Exception e) {
                    log(Strings.sm().getString("manager2.error.config"), e);
                    throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "ADD_FAILED",
                            Strings.sm().getString("manager2.configAddFailed", className, rootMessage(e)));
                }
                // A manager that does not start on a running context is
                // not useful; roll the change back. On a stopped context
                // the manager is started when the context starts.
                if (running && component instanceof Lifecycle lifecycle && !lifecycle.getState().isAvailable()) {
                    context.setManager(oldManager);
                    throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "START_FAILED",
                            Strings.sm().getString("manager2.configStartFailed", className, Strings.sm().getString("manager2.configNotStarted")));
                }
            }
            case "loader" -> {
                Loader oldLoader = context.getLoader();
                try {
                    // setLoader wires the loader to the context and, on a
                    // running context, stops the old loader and starts
                    // the new one.
                    context.setLoader((Loader) component);
                } catch (Exception e) {
                    log(Strings.sm().getString("manager2.error.config"), e);
                    throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "ADD_FAILED",
                            Strings.sm().getString("manager2.configAddFailed", className, rootMessage(e)));
                }
                if (running && component instanceof Lifecycle lifecycle && !lifecycle.getState().isAvailable()) {
                    context.setLoader(oldLoader);
                    throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "START_FAILED",
                            Strings.sm().getString("manager2.configStartFailed", className, Strings.sm().getString("manager2.configNotStarted")));
                }
            }
            case "resources" -> {
                // The context refuses to change its resources while it
                // is running: the resource tree of a live web
                // application cannot be swapped out from under it.
                if (running) {
                    throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "CONTEXT_RUNNING",
                            Strings.sm().getString("manager2.configContextMustBeStopped", displayName(context, "context")));
                }
                try {
                    // setResources wires the resources to the context.
                    // Their lifecycle is driven by the context (start).
                    context.setResources((WebResourceRoot) component);
                } catch (Exception e) {
                    log(Strings.sm().getString("manager2.error.config"), e);
                    throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "ADD_FAILED",
                            Strings.sm().getString("manager2.configAddFailed", className, rootMessage(e)));
                }
            }
            default -> {
                // The cookie processor has no lifecycle; the context
                // uses it from the moment it is set.
                try {
                    context.setCookieProcessor((CookieProcessor) component);
                } catch (Exception e) {
                    log(Strings.sm().getString("manager2.error.config"), e);
                    throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "ADD_FAILED",
                            Strings.sm().getString("manager2.configAddFailed", className, rootMessage(e)));
                }
            }
        }
        log(Strings.sm().getString("manager2.configAuditAdd", type, className));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", className));
    }


    /**
     * Add (replace) the session id generator of a manager. The manager uses it from the moment it is set; on a running
     * manager the old generator is stopped and the new one started.
     */
    private void addSessionIdGenerator(HttpServletResponse response, NodeRef parent, Map<String, Object> body)
            throws Exception {

        if (!(parent.component instanceof Manager manager)) {
            throw badParent("sessionIdGenerator");
        }
        String className = string(body.get("className"));
        if (className == null || className.isEmpty()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_NAME",
                    Strings.sm().getString("manager2.configInvalidName", "className"));
        }
        SessionIdGenerator generator;
        try {
            // Session id generators are server level classes: never use
            // the webapp class loader.
            generator = (SessionIdGenerator) Class.forName(className, true, server.getClass().getClassLoader())
                    .getConstructor().newInstance();
        } catch (Exception e) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_CLASS",
                    Strings.sm().getString("manager2.configInvalidClass", className));
        }

        // The Manager interface does not extend Lifecycle; all standard
        // managers do (ManagerBase), so the state is read conditionally.
        boolean running = manager instanceof Lifecycle && ((Lifecycle) manager).getState().isAvailable();
        SessionIdGenerator old = manager.getSessionIdGenerator();
        boolean oldRunning = running && old instanceof Lifecycle && ((Lifecycle) old).getState().isAvailable();
        try {
            if (oldRunning) {
                ((Lifecycle) old).stop();
            }
            manager.setSessionIdGenerator(generator);
            // The manager stamps its jvm route onto the generator at
            // start; mirror that for a live replacement.
            if (manager instanceof ManagerBase managerBase) {
                generator.setJvmRoute(managerBase.getJvmRoute());
            }
            if (running && generator instanceof Lifecycle lifecycle) {
                lifecycle.start();
            }
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.config"), e);
            // Best effort rollback: stop the new generator (if it
            // started) and restore the previous one (the setter rejects
            // null, so "none" cannot be restored).
            try {
                if (generator instanceof Lifecycle lifecycle && lifecycle.getState().isAvailable()) {
                    lifecycle.stop();
                }
                if (manager.getSessionIdGenerator() == generator && old != null) {
                    manager.setSessionIdGenerator(old);
                }
                if (oldRunning && old instanceof Lifecycle lifecycle && !lifecycle.getState().isAvailable()) {
                    lifecycle.start();
                }
            } catch (Exception ignored) {
                // Best effort; the failure is already logged.
            }
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "ADD_FAILED",
                    Strings.sm().getString("manager2.configAddFailed", className, rootMessage(e)));
        }
        log(Strings.sm().getString("manager2.configAuditAdd", "sessionIdGenerator", className));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", className));
    }


    /**
     * Add one JNDI entry to a {@code NamingResourcesImpl}. The entry takes effect in the live JNDI environment of the
     * server (the global context) or of the context ({@code java:comp/env}) immediately: the add fires the property
     * change event the {@code NamingContextListener} reacts to.
     * <p>
     * The request body carries the entry fields: {@code name} (the JNDI name), {@code jndiType} (the type of the
     * object) and the type specific fields ({@code auth}, {@code factory}, ...). A nested {@code params} object is
     * applied as the free form string parameters of the entry; for the first party JNDI factories with a closed set of
     * parameters the values are validated against the type the factory parses them as.
     */
    private void addNamingEntry(HttpServletResponse response, NodeRef parent, Map<String, Object> body, String type)
            throws Exception {

        if (!(parent.component instanceof NamingResourcesImpl namingResources)) {
            throw badParent(type);
        }
        // Resource links are only part of a context JNDI environment;
        // they are not parsed from <GlobalNamingResources>.
        if ("resourceLink".equals(type) && namingResources.getContainer() instanceof Server) {
            throw badParent(type);
        }
        String name = string(body.get("name"));
        if (name == null || name.isEmpty()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_ID",
                    Strings.sm().getString("manager2.configInvalidId"));
        }
        if (findNamingEntry(namingResources, type, name) != null) {
            throw duplicate(name);
        }
        String jndiType = string(body.get("jndiType"));
        if (jndiType == null || jndiType.isEmpty()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "MISSING_FIELD",
                    Strings.sm().getString("manager2.configJndiTypeRequired", type));
        }

        ResourceBase entry;
        switch (type) {
            case "resource" -> {
                ContextResource resource = new ContextResource();
                resource.setName(name);
                resource.setType(jndiType);
                String auth = string(body.get("auth"));
                if (auth != null) {
                    resource.setAuth(auth);
                }
                resource.setSingleton(bool(body.get("singleton"), true));
                String closeMethod = string(body.get("closeMethod"));
                if (closeMethod != null) {
                    resource.setCloseMethod(closeMethod);
                }
                String lookupName = string(body.get("lookupName"));
                if (lookupName != null) {
                    resource.setLookupName(lookupName);
                }
                String description = string(body.get("description"));
                if (description != null) {
                    resource.setDescription(description);
                }
                entry = resource;
            }
            case "resourceLink" -> {
                ContextResourceLink link = new ContextResourceLink();
                link.setName(name);
                link.setType(jndiType);
                String global = string(body.get("global"));
                if (global == null || global.isEmpty()) {
                    throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "MISSING_FIELD",
                            Strings.sm().getString("manager2.configJndiGlobalRequired"));
                }
                link.setGlobal(global);
                String description = string(body.get("description"));
                if (description != null) {
                    link.setDescription(description);
                }
                entry = link;
            }
            case "resourceEnvRef" -> {
                ContextResourceEnvRef resourceEnvRef = new ContextResourceEnvRef();
                resourceEnvRef.setName(name);
                resourceEnvRef.setType(jndiType);
                resourceEnvRef.setOverride(bool(body.get("override"), true));
                String description = string(body.get("description"));
                if (description != null) {
                    resourceEnvRef.setDescription(description);
                }
                entry = resourceEnvRef;
            }
            case "environment" -> {
                ContextEnvironment environment = new ContextEnvironment();
                environment.setName(name);
                environment.setType(jndiType);
                String value = string(body.get("value"));
                if (value != null) {
                    environment.setValue(value);
                }
                environment.setOverride(bool(body.get("override"), true));
                String description = string(body.get("description"));
                if (description != null) {
                    environment.setDescription(description);
                }
                entry = environment;
            }
            case "ejb" -> {
                ContextEjb ejb = new ContextEjb();
                ejb.setName(name);
                ejb.setType(jndiType);
                String home = string(body.get("home"));
                if (home != null) {
                    ejb.setHome(home);
                }
                String link = string(body.get("link"));
                if (link != null) {
                    ejb.setLink(link);
                }
                String remote = string(body.get("remote"));
                if (remote != null) {
                    ejb.setRemote(remote);
                }
                String description = string(body.get("description"));
                if (description != null) {
                    ejb.setDescription(description);
                }
                entry = ejb;
            }
            case "localEjb" -> {
                ContextLocalEjb localEjb = new ContextLocalEjb();
                localEjb.setName(name);
                localEjb.setType(jndiType);
                String local = string(body.get("local"));
                if (local != null) {
                    localEjb.setLocal(local);
                }
                String home = string(body.get("home"));
                if (home != null) {
                    localEjb.setHome(home);
                }
                String link = string(body.get("link"));
                if (link != null) {
                    localEjb.setLink(link);
                }
                String description = string(body.get("description"));
                if (description != null) {
                    localEjb.setDescription(description);
                }
                entry = localEjb;
            }
            default -> {
                ContextService service = new ContextService();
                service.setName(name);
                service.setType(jndiType);
                String serviceInterface = string(body.get("interface"));
                if (serviceInterface != null) {
                    service.setInterface(serviceInterface);
                }
                String displayname = string(body.get("displayname"));
                if (displayname != null) {
                    service.setDisplayname(displayname);
                }
                String wsdlfile = string(body.get("wsdlfile"));
                if (wsdlfile != null) {
                    service.setWsdlfile(wsdlfile);
                }
                String description = string(body.get("description"));
                if (description != null) {
                    service.setDescription(description);
                }
                entry = service;
            }
        }

        // The factory: a string parameter of a resource, a first class
        // attribute of a resource link.
        String factory = string(body.get("factory"));
        if (factory != null && !factory.isEmpty()) {
            checkFactoryLoadable(factory);
            if (entry instanceof ContextResourceLink link) {
                link.setFactory(factory);
            } else if (entry instanceof ContextResource) {
                entry.setProperty("factory", factory);
            }
        }
        // The generic string parameters of the entry (the ResourceBase
        // property map). For the types whose factory is one of the first
        // party factories with a closed set of options the values are
        // validated against that set; for the others (and for open set
        // factories) they are stored as-is.
        applyNamingParams(entry, factory, body.get("params"));

        try {
            addNamingEntryTo(namingResources, type, entry);
        } catch (Exception e) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "ADD_FAILED",
                    Strings.sm().getString("manager2.configAddFailed", name, rootMessage(e)));
        }
        log(Strings.sm().getString("manager2.configAuditAdd", type, name));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", name));
    }


    /**
     * Apply the free form string parameters of a new JNDI entry, validating the values against the closed parameter set
     * of the first party factory (the factories parse them at JNDI lookup time, which is too late for a useful error
     * message).
     */
    private void applyNamingParams(ResourceBase entry, String factory, Object params) throws ConfigException {
        if (!(params instanceof Map<?, ?> map) || map.isEmpty()) {
            return;
        }
        List<ExplicitAttribute> options = factoryOptions(factory);
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String key = String.valueOf(e.getKey());
            String value = string(e.getValue());
            if (value == null) {
                continue;
            }
            if (options != null) {
                for (ExplicitAttribute option : options) {
                    if (option.getName().equals(key)) {
                        validateParamValue(key, value, option.getType());
                        break;
                    }
                }
            }
            entry.setProperty(key, value);
        }
    }


    private static void validateParamValue(String name, String value, String type) throws ConfigException {
        try {
            String trimmed = value.trim();
            switch (type) {
                case "int" -> Integer.parseInt(trimmed);
                case "long" -> Long.parseLong(trimmed);
                case "boolean" -> {
                    String v = trimmed.toLowerCase(Locale.ROOT);
                    if (!"true".equals(v) && !"false".equals(v)) {
                        throw new NumberFormatException(trimmed);
                    }
                }
                default -> {
                    // A string.
                }
            }
        } catch (NumberFormatException e) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "SET_FAILED",
                    Strings.sm().getString("manager2.configSetFailed", name, Strings.sm().getString("manager2.configInvalidType", type)));
        }
    }


    private void checkFactoryLoadable(String factory) throws ConfigException {
        try {
            // The factory is instantiated with the container class
            // loader at JNDI lookup time.
            Class.forName(factory, false, server.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_CLASS",
                    Strings.sm().getString("manager2.configFactoryInvalid", factory));
        }
    }


    private void addConnector(HttpServletResponse response, NodeRef parent, Map<String, Object> body) throws Exception {

        if (!(parent.component instanceof StandardService service)) {
            throw badParent("connector");
        }
        String protocol = string(body.get("protocol"));
        if (protocol == null || protocol.isEmpty()) {
            protocol = "HTTP/1.1";
        }
        Integer port = intValue(body.get("port"), -1);
        if (port < 1 || port > 65535) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_VALUE",
                    Strings.sm().getString("manager2.configInvalidValue", "port"));
        }
        Connector connector = new Connector(protocol);
        connector.setPort(port);
        addChecked(connectorLabel(connector), connector, () -> service.addConnector(connector),
                () -> service.removeConnector(connector));
        log(Strings.sm().getString("manager2.configAuditAdd", "connector", protocol + " (port " + port + ")"));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", protocol + " (port " + port + ")"));
    }


    private void addExecutor(HttpServletResponse response, NodeRef parent, Map<String, Object> body) throws Exception {

        if (!(parent.component instanceof StandardService service)) {
            throw badParent("executor");
        }
        String name = requiredName(body.get("name"));
        for (Executor executor : service.findExecutors()) {
            if (executor.getName().equals(name)) {
                throw duplicate(name);
            }
        }
        StandardThreadExecutor executor = new StandardThreadExecutor();
        executor.setName(name);
        Integer maxThreads = intValue(body.get("maxThreads"), -1);
        if (maxThreads > 0) {
            executor.setMaxThreads(maxThreads);
        }
        Integer minSpare = intValue(body.get("minSpareThreads"), -1);
        if (minSpare >= 0) {
            executor.setMinSpareThreads(minSpare);
        }
        // The thread pool refuses to start when the minimum number of
        // threads exceeds the maximum (the defaults are 25 and 200, so an
        // explicit maxThreads below the default minimum must be rejected).
        if (executor.getMinSpareThreads() > executor.getMaxThreads()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_VALUE",
                    Strings.sm().getString("manager2.configExecutorInvalid"));
        }
        addChecked(name, executor, () -> service.addExecutor(executor), () -> service.removeExecutor(executor));
        log(Strings.sm().getString("manager2.configAuditAdd", "executor", name));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", name));
    }


    /**
     * Add an SSL host configuration (one TLS virtual host) to a connector. Adding the first SSL host configuration
     * enables TLS on the connector. A running connector is restarted so that the TLS configuration takes effect; when
     * the restart fails (typically because no valid certificate is configured) the change is rolled back and the
     * connector is left running as it was.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void addSslHostConfig(HttpServletResponse response, NodeRef parent, Map<String, Object> body)
            throws Exception {

        if (!(parent.component instanceof Connector connector)) {
            throw badParent("sslHostConfig");
        }
        AbstractHttp11Protocol http11 = sslProtocolHandler(connector);
        if (http11 == null) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "BAD_PARENT",
                    Strings.sm().getString("manager2.configSslUnsupported", connector.getProtocolHandlerClassName()));
        }
        if (isSelfConnector(connector)) {
            throw self();
        }
        String hostName = string(body.get("hostName"));
        if (hostName == null || hostName.isEmpty()) {
            // The default host configuration name ("_default_") is not
            // exposed by a public constant.
            hostName = "_default_";
        }
        if (!SAFE_NAME.matcher(hostName).matches()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_NAME",
                    Strings.sm().getString("manager2.configInvalidName", hostName));
        }
        String hostNameLower = hostName.toLowerCase(Locale.ENGLISH);
        SSLHostConfig[] existing = connector.findSslHostConfigs();
        if (existing != null) {
            for (SSLHostConfig hostConfig : existing) {
                if (hostConfig.getHostName().equals(hostNameLower)) {
                    throw duplicate(hostNameLower);
                }
            }
        }

        boolean wasSslEnabled = http11.isSSLEnabled();
        boolean wasRunning = connector.getState().isAvailable();

        SSLHostConfig sslHostConfig = new SSLHostConfig();
        sslHostConfig.setHostName(hostNameLower);
        // An optional initial certificate. On a running connector that
        // is not TLS enabled yet it is required: a connector without
        // any certificate cannot complete a single TLS handshake.
        boolean certificateProvided = body.get("certificate") instanceof Map;
        if (wasRunning && !wasSslEnabled && !certificateProvided) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_VALUE",
                    Strings.sm().getString("manager2.configSslCertificateRequired"));
        }
        if (certificateProvided) {
            SSLHostConfigCertificate certificate = buildCertificate(sslHostConfig,
                    (Map<String, Object>) body.get("certificate"));
            try {
                sslHostConfig.addCertificate(certificate);
            } catch (IllegalArgumentException e) {
                throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_VALUE",
                        Strings.sm().getString("manager2.configInvalidValue", "certificate"));
            }
        }

        try {
            connector.addSslHostConfig(sslHostConfig);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "ADD_FAILED",
                    Strings.sm().getString("manager2.configAddFailed", hostNameLower, rootMessage(e)));
        }

        if (wasRunning && !wasSslEnabled) {
            // The endpoint is already bound, so its TLS configuration
            // (SSL implementation and the SSL contexts, which is where
            // the keystores are loaded) is initialized here instead of
            // at bind time. This validates the new TLS configuration
            // without interrupting the connector: from this point on,
            // new connections are served over TLS.
            try {
                endpointOf(connector).initialiseSsl();
            } catch (Exception e) {
                // TLS is switched off first: the endpoint refuses to
                // remove its default host configuration while it is
                // still serving TLS.
                http11.setSSLEnabled(false);
                try {
                    endpointOf(connector).removeSslHostConfig(hostNameLower);
                } catch (Exception e2) {
                    log(Strings.sm().getString("manager2.configRollbackFailed", hostNameLower, rootMessage(e2)), e2);
                }
                throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "ADD_FAILED",
                        Strings.sm().getString("manager2.configAddFailed", hostNameLower, rootMessage(e)));
            }
        }

        log(Strings.sm().getString("manager2.configAuditAdd", "sslHostConfig", hostNameLower));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", hostNameLower));
    }


    /**
     * Add an upgrade protocol (e.g. the HTTP/2 one) to a connector through {@code AbstractHttp11Protocol
     * .addUpgradeProtocol}. An upgrade protocol is only referenced when the connector is initialised, so the new
     * instance does not take effect on a running connector: it becomes active the next time the connector is
     * (re)started (see the lifecycle operations). No live activation is attempted.
     */
    private void addUpgradeProtocol(HttpServletResponse response, NodeRef parent, Map<String, Object> body)
            throws Exception {

        if (!(parent.component instanceof Connector connector)) {
            throw badParent("upgradeProtocol");
        }
        if (http11ProtocolHandler(connector) == null) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "BAD_PARENT",
                    Strings.sm().getString("manager2.configUpgradeUnsupported", connector.getProtocolHandlerClassName()));
        }
        String className = string(body.get("className"));
        if (className == null || className.isEmpty()) {
            // The only UpgradeProtocol shipped with Tomcat is the HTTP/2 one.
            className = "org.apache.coyote.http2.Http2Protocol";
        }
        UpgradeProtocol upgradeProtocol;
        try {
            // Upgrade protocols are server level classes: never use the webapp
            // class loader.
            upgradeProtocol = (UpgradeProtocol) Class.forName(className, true, server.getClass().getClassLoader())
                    .getConstructor().newInstance();
        } catch (Exception e) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_CLASS",
                    Strings.sm().getString("manager2.configInvalidClass", className));
        }
        // The protocol handler keeps the protocols in a plain list without
        // checking for duplicates; a second protocol with the same name would
        // be silently shadowed when the connector is next initialised, so
        // reject it here.
        String label = upgradeProtocolLabel(upgradeProtocol);
        for (UpgradeProtocol existing : connector.findUpgradeProtocols()) {
            if (label.equals(upgradeProtocolLabel(existing))) {
                throw duplicate(label);
            }
        }
        connector.addUpgradeProtocol(upgradeProtocol);
        log(Strings.sm().getString("manager2.configAuditAdd", "upgradeProtocol", label));
        Api.ok(response, Strings.sm().getString("manager2.configUpgradeProtocolAdded", label));
    }


    /**
     * Add a certificate configuration to an SSL host configuration. On a running, TLS enabled connector the new
     * certificate is applied at once (the SSL context of the virtual host is re-created, which also validates the
     * keystore); when that fails the certificate is rolled back.
     */
    @SuppressWarnings("rawtypes")
    private void addCertificate(HttpServletResponse response, NodeRef parent, Map<String, Object> body)
            throws Exception {

        if (!(parent.component instanceof SSLHostConfig sslHostConfig)) {
            throw badParent("certificate");
        }
        SSLHostConfigCertificate certificate = buildCertificate(sslHostConfig, body);
        try {
            sslHostConfig.addCertificate(certificate);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "ADD_FAILED",
                    Strings.sm().getString("manager2.configAddFailed", certificateLabel(certificate), rootMessage(e)));
        }

        Connector connector = connectorOfSsl(sslHostConfig);
        AbstractHttp11Protocol http11 = (connector != null) ? sslProtocolHandler(connector) : null;
        if (connector != null && connector.getState().isAvailable() && http11 != null && http11.isSSLEnabled()) {
            try {
                endpointOf(connector).reloadSslHostConfig(sslHostConfig.getHostName());
            } catch (Exception e) {
                sslHostConfig.getCertificates().remove(certificate);
                throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "ADD_FAILED",
                        Strings.sm().getString("manager2.configAddFailed", certificateLabel(certificate), rootMessage(e)));
            }
        }

        log(Strings.sm().getString("manager2.configAuditAdd", "certificate", certificateLabel(certificate)));
        Api.ok(response, Strings.sm().getString("manager2.configAdded", certificateLabel(certificate)));
    }


    /**
     * Build a (not yet registered) certificate configuration from a JSON object. The same builder is used for the
     * certificate of a new SSL host configuration and for standalone certificates.
     */
    private static SSLHostConfigCertificate buildCertificate(SSLHostConfig sslHostConfig, Map<String, Object> body)
            throws ConfigException {
        String typeName = string(body.get("type"));
        SSLHostConfigCertificate.Type type;
        if (typeName == null || typeName.isEmpty()) {
            type = SSLHostConfigCertificate.Type.UNDEFINED;
        } else {
            try {
                type = SSLHostConfigCertificate.Type.valueOf(typeName.trim().toUpperCase(Locale.ENGLISH));
            } catch (IllegalArgumentException e) {
                throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_NAME",
                        Strings.sm().getString("manager2.configInvalidName", typeName));
            }
        }
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(sslHostConfig, type);
        setIfPresent(certificate, "certificateKeystoreFile", body);
        setIfPresent(certificate, "certificateKeystorePassword", body);
        setIfPresent(certificate, "certificateKeystorePasswordFile", body);
        setIfPresent(certificate, "certificateKeystoreType", body);
        setIfPresent(certificate, "certificateKeystoreProvider", body);
        setIfPresent(certificate, "certificateKeyAlias", body);
        setIfPresent(certificate, "certificateKeyPassword", body);
        setIfPresent(certificate, "certificateKeyPasswordFile", body);
        setIfPresent(certificate, "certificateFile", body);
        setIfPresent(certificate, "certificateChainFile", body);
        setIfPresent(certificate, "certificateKeyFile", body);
        return certificate;
    }


    /**
     * Call one string setter on the target when the body carries a non-empty value for the property.
     */
    private static void setIfPresent(Object target, String name, Map<String, Object> body) throws ConfigException {
        Object value = body.get(name);
        if (value == null) {
            return;
        }
        String s = String.valueOf(value);
        if (s.isEmpty()) {
            return;
        }
        try {
            target.getClass().getMethod("set" + capitalize(name), String.class).invoke(target, s);
        } catch (Exception e) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_VALUE",
                    Strings.sm().getString("manager2.configInvalidValue", name));
        }
    }


    /**
     * The protocol handler of the connector when it is the HTTP/1.1 variant, otherwise {@code null} (for example an AJP
     * connector).
     */
    @SuppressWarnings("rawtypes")
    private static AbstractHttp11Protocol http11ProtocolHandler(Connector connector) {
        if (connector.getProtocolHandler() instanceof AbstractHttp11Protocol http11) {
            return http11;
        }
        return null;
    }


    /**
     * The protocol handler of the connector when it is the HTTP/1.1 variant that supports TLS, otherwise {@code null}
     * (for example an AJP connector).
     */
    @SuppressWarnings("rawtypes")
    private static AbstractHttp11Protocol sslProtocolHandler(Connector connector) {
        return http11ProtocolHandler(connector);
    }


    /**
     * Whether the connector's protocol handler has TLS enabled.
     */
    @SuppressWarnings("rawtypes")
    private static boolean isSslEnabled(Connector connector) {
        AbstractHttp11Protocol http11 = sslProtocolHandler(connector);
        return http11 != null && http11.isSSLEnabled();
    }


    /**
     * The connector that owns the given SSL host configuration, or {@code null} when it is not attached to any
     * connector.
     */
    private Connector connectorOfSsl(SSLHostConfig sslHostConfig) {
        for (Service service : server.findServices()) {
            for (Connector connector : service.findConnectors()) {
                SSLHostConfig[] hostConfigs = connector.findSslHostConfigs();
                if (hostConfigs == null) {
                    continue;
                }
                for (SSLHostConfig candidate : hostConfigs) {
                    if (candidate == sslHostConfig) {
                        return connector;
                    }
                }
            }
        }
        return null;
    }


    /**
     * The endpoint of the connector's protocol handler. The endpoint (and with it the SSL host configuration operations
     * that are not on the {@code ProtocolHandler} interface, such as {@code removeSslHostConfig} and
     * {@code reloadSslHostConfig}) is only reachable through the protected accessor of {@code AbstractProtocol}, so it
     * is invoked reflectively.
     */
    private AbstractEndpoint<?, ?> endpointOf(Connector connector) throws ConfigException {
        try {
            Method m = AbstractProtocol.class.getDeclaredMethod("getEndpoint");
            m.setAccessible(true);
            return (AbstractEndpoint<?, ?>) m.invoke(connector.getProtocolHandler());
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.config"), e);
            throw new ConfigException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SERVER_UNAVAILABLE",
                    Strings.sm().getString("manager2.configServerUnavailable"));
        }
    }


    /**
     * Whether the connector routes requests for the host that deploys this web application (the connector must never be
     * stopped or restarted, as that would destroy the admin session mid-request).
     */
    private boolean isSelfConnector(Connector connector) {
        Service service = connector.getService();
        return service != null && service.getContainer() != null && selfHost.getParent() == service.getContainer();
    }


    @SuppressWarnings("rawtypes")
    private void removeChild(HttpServletResponse response, Map<String, Object> body) throws Exception {

        String id = string(body.get("id"));
        if (id == null) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_ID",
                    Strings.sm().getString("manager2.configInvalidId"));
        }
        NodeRef ref = resolve(id);

        // Guards against removing the components that host this web
        // application (the session would be destroyed mid-request). An
        // alias is excluded: it is not a container, so removing one cannot
        // change where this web application is deployed, even on the
        // manager's own host.
        if (!"alias".equals(ref.type) && ref.component == selfContext) {
            throw self();
        }
        if (!"alias".equals(ref.type) && ref.component == selfHost) {
            throw self();
        }
        // The last service cannot be removed. Checked before the service
        // self-host check: with a single service both apply and the
        // structural invariant is the more informative answer.
        if (ref.type.equals("service") && server.findServices().length <= 1) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "LAST_SERVICE",
                    Strings.sm().getString("manager2.configLastService"));
        }
        if (ref.type.equals("service") && containsSelf((StandardService) ref.component)) {
            throw self();
        }
        if (ref.type.equals("engine") && ref.component instanceof Engine engine &&
                engine.findChild(selfHost.getName()) instanceof Host) {
            throw self();
        }

        if (ref.type.equals("valve") && isBasicValve(ref)) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "BASIC_COMPONENT",
                    Strings.sm().getString("manager2.configBasicValve"));
        }
        // The sub components a context holds exactly one of (and the
        // manager's session id generator) are required: they cannot be
        // removed, only replaced.
        if (Set.of("manager", "resources", "loader", "cookieProcessor", "sessionIdGenerator").contains(ref.type)) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "REQUIRED_COMPONENT",
                    Strings.sm().getString("manager2.configRequiredComponent", ref.type));
        }
        // The JNDI naming resources of the server and of a context are
        // always present and required: they cannot be removed.
        if (ref.type.equals("namingResources")) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "REQUIRED_COMPONENT",
                    Strings.sm().getString("manager2.configRequiredNamingResources"));
        }
        // Cluster valves and channel interceptors are repeatable sub
        // components that have no removal API on a running cluster; only
        // the cluster itself and its single-valued sub components can be
        // detached.
        if (ref.type.equals("clusterValve") || ref.type.equals("interceptor")) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "REMOVE_NOT_SUPPORTED",
                    Strings.sm().getString("manager2.configRemoveNotSupported", ref.type));
        }
        // A service holds exactly one engine (its container); it cannot be
        // removed while it still contains hosts.
        if (ref.type.equals("engine") && ref.component instanceof Engine engine && countHosts(engine) > 0) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "NOT_EMPTY",
                    Strings.sm().getString("manager2.configNotEmpty", "engine"));
        }
        // A directly attached realm can only be removed when the container
        // falls back to a parent realm afterwards; otherwise the container
        // (and everything below it) would have no realm at all.
        if (ref.type.equals("realm") && ref.parent instanceof Container container && fallbackRealm(container) == null) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "LAST_REALM",
                    Strings.sm().getString("manager2.configLastRealm"));
        }

        String label = ref.aliasValue != null ? ref.aliasValue : displayName(ref.component, ref.type);
        if (Set.of("host", "context", "service", "engine", "connector", "executor", "wrapper", "valve", "sslHostConfig",
                "upgradeProtocol", "realm", "cluster", "resource", "resourceLink", "resourceEnvRef", "environment",
                "ejb", "localEjb", "serviceRef").contains(ref.type)) {
            String confirm = string(body.get("confirm"));
            if (!label.equals(confirm)) {
                throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "CONFIRM_REQUIRED",
                        Strings.sm().getString("manager2.configConfirmRequired", label));
            }
        }

        if ("sslHostConfig".equals(ref.type)) {
            removeSslHostConfig(response, ref, label);
            return;
        }
        if ("certificate".equals(ref.type)) {
            removeCertificate(response, ref);
            return;
        }

        try {
            switch (ref.type) {
                case "host" -> ((Container) ref.parent).removeChild((Host) ref.component);
                case "context" -> ((Container) ref.parent).removeChild((Context) ref.component);
                case "wrapper" -> ((Container) ref.parent).removeChild((Wrapper) ref.component);
                case "valve" -> ((Container) ref.parent).getPipeline().removeValve((Valve) ref.component);
                case "connector" -> ((StandardService) ref.parent).removeConnector((Connector) ref.component);
                case "executor" -> ((StandardService) ref.parent).removeExecutor((Executor) ref.component);
                case "alias" -> ((Host) ref.component).removeAlias(ref.aliasValue);
                case "listener" ->
                    ((LifecycleBase) ref.parent).removeLifecycleListener((LifecycleListener) ref.component);
                case "upgradeProtocol" -> {
                    AbstractHttp11Protocol http11 = http11ProtocolHandler((Connector) ref.parent);
                    if (http11 == null) {
                        throw notFound();
                    }
                    http11.removeUpgradeProtocol((UpgradeProtocol) ref.component);
                }
                case "realm" -> removeRealm((Realm) ref.component, ref.parent);
                case "engine" -> ((StandardService) ref.parent).setContainer(null);
                case "service" -> server.removeService((Service) ref.component);
                case "resource" -> removeNamingEntry((NamingResourcesImpl) ref.parent, "resource",
                        ((ResourceBase) ref.component).getName());
                case "resourceLink" -> removeNamingEntry((NamingResourcesImpl) ref.parent, "resourceLink",
                        ((ResourceBase) ref.component).getName());
                case "resourceEnvRef" -> removeNamingEntry((NamingResourcesImpl) ref.parent, "resourceEnvRef",
                        ((ResourceBase) ref.component).getName());
                case "environment" -> removeNamingEntry((NamingResourcesImpl) ref.parent, "environment",
                        ((ResourceBase) ref.component).getName());
                case "ejb" -> removeNamingEntry((NamingResourcesImpl) ref.parent, "ejb",
                        ((ResourceBase) ref.component).getName());
                case "localEjb" -> removeNamingEntry((NamingResourcesImpl) ref.parent, "localEjb",
                        ((ResourceBase) ref.component).getName());
                case "serviceRef" -> removeNamingEntry((NamingResourcesImpl) ref.parent, "serviceRef",
                        ((ResourceBase) ref.component).getName());
                case "cluster" -> ((Container) ref.parent).setCluster(null);
                case "channel" -> ((CatalinaCluster) ref.parent).setChannel(null);
                case "membership" -> ((ManagedChannel) ref.parent).setMembershipService(null);
                case "sender" -> ((ManagedChannel) ref.parent).setChannelSender(null);
                case "receiver" -> ((ManagedChannel) ref.parent).setChannelReceiver(null);
                case "deployer" -> ((CatalinaCluster) ref.parent).setClusterDeployer(null);
                case "clusterManager" -> ((SimpleTcpCluster) ref.parent).setManagerTemplate(null);
                case "transport" -> ((ReplicationTransmitter) ref.parent).setTransport(null);
                case "clusterListener" ->
                    ((CatalinaCluster) ref.parent).removeClusterListener((ClusterListener) ref.component);
                case "member" -> removeClusterMember(ref);
                default -> throw notFound();
            }
        } catch (ConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "REMOVE_FAILED",
                    Strings.sm().getString("manager2.configRemoveFailed",
                            e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }

        log(Strings.sm().getString("manager2.configAuditRemove", ref.type, label));
        Api.ok(response, Strings.sm().getString("manager2.configRemoved", label));
    }


    /**
     * Remove a static member (the local member or a static member) from a static membership service or interceptor.
     */
    private void removeClusterMember(NodeRef ref) {
        Member member = (Member) ref.component;
        if (ref.parent instanceof StaticMembershipService sms) {
            if (sms.getLocalMember(false) == member) {
                sms.setLocalMember(null);
            } else {
                sms.removeStaticMember((StaticMember) member);
            }
        } else if (ref.parent instanceof StaticMembershipInterceptor smi) {
            if (smi.getLocalMember(false) == member) {
                smi.setLocalMember(null);
            } else {
                smi.removeStaticMember(member);
            }
        }
    }


    /**
     * Remove a realm. A directly attached realm is detached from its container (the container then falls back to the
     * parent realm) and a sub realm is removed from its combined realm and stopped.
     */
    private static void removeRealm(Realm realm, Object parent) throws Exception {
        if (parent instanceof CombinedRealm combined) {
            if (!combined.removeRealm(realm)) {
                throw notFound();
            }
            if (realm instanceof Lifecycle lifecycle && lifecycle.getState().isAvailable()) {
                lifecycle.stop();
            }
        } else if (parent instanceof Container container) {
            // Detach: the container falls back to the parent realm.
            container.setRealm(null);
        } else {
            throw notFound();
        }
    }


    /**
     * Remove an SSL host configuration from its connector. When this disables TLS on the connector (no configurations
     * left) and the connector is running, it is restarted so that it serves plain HTTP again, with a rollback that
     * restores the configuration.
     */
    @SuppressWarnings("rawtypes")
    private void removeSslHostConfig(HttpServletResponse response, NodeRef ref, String label) throws Exception {

        if (!(ref.component instanceof SSLHostConfig sslHostConfig) || !(ref.parent instanceof Connector connector)) {
            throw notFound();
        }
        if (isSelfConnector(connector)) {
            throw self();
        }
        AbstractHttp11Protocol http11 = sslProtocolHandler(connector);
        boolean sslEnabled = isSslEnabled(connector);
        boolean running = connector.getState().isAvailable();
        int remaining = (connector.findSslHostConfigs() == null) ? 0 : connector.findSslHostConfigs().length - 1;
        if (sslEnabled && running && remaining > 0 &&
                sslHostConfig.getHostName().equalsIgnoreCase(endpointOf(connector).getDefaultSSLHostConfigName())) {
            // The default host configuration is the fallback for
            // handshakes without a matching SNI name; it cannot be
            // removed from a running, TLS enabled connector while other
            // configurations remain.
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "SSL_DEFAULT",
                    Strings.sm().getString("manager2.configSslDefault"));
        }

        boolean disablesSsl = sslEnabled && remaining == 0;
        if (disablesSsl) {
            // TLS is disabled for this connector from now on. The flag
            // is switched off before the removal because the endpoint
            // refuses to remove its default host configuration while it
            // is still serving TLS.
            http11.setSSLEnabled(false);
        }
        try {
            endpointOf(connector).removeSslHostConfig(sslHostConfig.getHostName());
        } catch (IllegalArgumentException e) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "REMOVE_FAILED",
                    Strings.sm().getString("manager2.configRemoveFailed", rootMessage(e)));
        }
        // When the removal disabled TLS no further action is needed: the
        // endpoint decides per accepted connection whether it is TLS, so
        // new connections are served over plain HTTP from this point on
        // (in-flight TLS connections complete normally).

        log(Strings.sm().getString("manager2.configAuditRemove", ref.type, label));
        Api.ok(response, Strings.sm().getString("manager2.configRemoved", label));
    }


    /**
     * Remove a certificate configuration from its SSL host configuration. On a running, TLS enabled connector the
     * change is applied at once (the SSL context of the virtual host is re-created) and rolled back when the remaining
     * configuration does not validate. The last certificate of such a connector cannot be removed: the connector would
     * no longer be able to start.
     */
    private void removeCertificate(HttpServletResponse response, NodeRef ref) throws Exception {

        if (!(ref.component instanceof SSLHostConfigCertificate certificate) ||
                !(ref.parent instanceof SSLHostConfig sslHostConfig)) {
            throw notFound();
        }
        Connector connector = connectorOfSsl(sslHostConfig);
        boolean live = connector != null && connector.getState().isAvailable() && isSslEnabled(connector);
        if (live && sslHostConfig.getCertificates().size() <= 1) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "SSL_LAST_CERTIFICATE",
                    Strings.sm().getString("manager2.configSslLastCertificate", connectorLabel(connector)));
        }

        Set<SSLHostConfigCertificate> certificates = sslHostConfig.getCertificates();
        certificates.remove(certificate);
        if (live) {
            try {
                endpointOf(connector).reloadSslHostConfig(sslHostConfig.getHostName());
            } catch (Exception e) {
                certificates.add(certificate);
                throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "REMOVE_FAILED",
                        Strings.sm().getString("manager2.configRemoveFailed", rootMessage(e)));
            }
        }

        String label = displayName(certificate, "certificate");
        log(Strings.sm().getString("manager2.configAuditRemove", "certificate", label));
        Api.ok(response, Strings.sm().getString("manager2.configRemoved", label));
    }


    private boolean containsSelf(StandardService service) {
        Engine engine = service.getContainer();
        return engine != null && engine.findChild(selfHost.getName()) instanceof Host;
    }


    private static int countHosts(Engine engine) {
        int count = 0;
        for (Container child : engine.findChildren()) {
            if (child instanceof Host) {
                count++;
            }
        }
        return count;
    }


    private boolean isBasicValve(NodeRef ref) {
        // The first valve of a pipeline is the basic valve and cannot be
        // removed.
        if (ref.parent instanceof Container container) {
            Valve[] valves = container.getPipeline().getValves();
            return valves.length > 0 && valves[0] == ref.component;
        }
        return false;
    }


    private static ConfigException self() {
        return new ConfigException(HttpServletResponse.SC_FORBIDDEN, "SELF_COMPONENT",
                Strings.sm().getString("manager2.configSelfComponent"));
    }


    private static ConfigException duplicate(String name) {
        return new ConfigException(HttpServletResponse.SC_CONFLICT, "DUPLICATE",
                Strings.sm().getString("manager2.configDuplicate", name));
    }


    private static ConfigException badParent(String type) {
        return new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "BAD_PARENT",
                Strings.sm().getString("manager2.configParentInvalid", type));
    }


    // -------------------------------------------------------- Lifecycle ops


    /**
     * Start, stop or restart one component of the server tree.
     * <p>
     * A {@code start} or {@code stop} of a component that would interrupt access to this web application itself (see
     * {@link #affectsSelf(NodeRef)}) is refused (403 {@code SELF_COMPONENT}): the request would lose its way back. A
     * {@code restart} (a stop, when the component is running, followed by a start) remains possible for those
     * components: the client's connection may be interrupted during the operation, but the component is running again
     * at the end and the client reconnects (re-logging in when the admin session was reset by the restart).
     */
    private void lifecycle(HttpServletResponse response, Map<String, Object> body) throws Exception {

        String id = string(body.get("id"));
        String op = string(body.get("op"));
        if (id == null || op == null || op.isEmpty()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_ID",
                    Strings.sm().getString("manager2.configInvalidId"));
        }
        boolean start = "start".equals(op);
        boolean stop = "stop".equals(op);
        boolean restart = "restart".equals(op);
        if (!start && !stop && !restart) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_OP",
                    Strings.sm().getString("manager2.configInvalidOp", op));
        }

        NodeRef ref = resolve(id);
        if (!(ref.component instanceof Lifecycle lifecycle)) {
            String label = ref.aliasValue != null ? ref.aliasValue : displayName(ref.component, ref.type);
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "NOT_A_LIFECYCLE",
                    Strings.sm().getString("manager2.configNotALifecycle", label));
        }
        if (!restart && affectsSelf(ref)) {
            throw self();
        }
        String label = ref.aliasValue != null ? ref.aliasValue : displayName(ref.component, ref.type);

        if (stop) {
            if (!lifecycle.getState().isAvailable()) {
                Api.ok(response, Strings.sm().getString("manager2.configAlreadyStopped", label));
                return;
            }
            stopChecked(lifecycle, label);
            log(Strings.sm().getString("manager2.configAuditLifecycle", "stopped", label));
            Api.ok(response, Strings.sm().getString("manager2.configStopped", label));
            return;
        }

        if (restart && lifecycle.getState().isAvailable()) {
            stopChecked(lifecycle, label);
        }
        if (start && lifecycle.getState().isAvailable()) {
            Api.ok(response, Strings.sm().getString("manager2.configAlreadyRunning", label));
            return;
        }
        startChecked(lifecycle, label);
        log(Strings.sm().getString("manager2.configAuditLifecycle", restart ? "restarted" : "started", label));
        Api.ok(response, Strings.sm().getString(restart ? "manager2.configRestarted" : "manager2.configStarted", label));
    }


    /**
     * Stop the component, raising a controlled error when it cannot be stopped or does not stop.
     */
    private void stopChecked(Lifecycle lifecycle, String label) throws ConfigException {
        try {
            lifecycle.stop();
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.config"), e);
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "STOP_FAILED",
                    Strings.sm().getString("manager2.configStopFailed", label, rootMessage(e)));
        }
        if (lifecycle.getState().isAvailable()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "STOP_FAILED",
                    Strings.sm().getString("manager2.configStopFailed", label, Strings.sm().getString("manager2.configNotStopped")));
        }
    }


    /**
     * Start the component, raising a controlled error when it cannot be started or does not start.
     */
    private void startChecked(Lifecycle lifecycle, String label) throws ConfigException {
        try {
            lifecycle.start();
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.config"), e);
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "START_FAILED",
                    Strings.sm().getString("manager2.configStartFailed", label, rootMessage(e)));
        }
        if (!lifecycle.getState().isAvailable()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "START_FAILED",
                    Strings.sm().getString("manager2.configStartFailed", label, Strings.sm().getString("manager2.configNotStarted")));
        }
    }


    /**
     * Whether stopping or starting this component would interrupt access to this web application: the component is the
     * server itself, or the service, engine, host, context, connector or wrapper that route or host the requests of
     * this web application. A {@code restart} of such a component remains possible (the client reconnects at the end of
     * the operation).
     */
    private boolean affectsSelf(NodeRef ref) {
        Object component = ref.component;
        return switch (ref.type) {
            case "server" -> true;
            case "service" -> component instanceof StandardService service && containsSelf(service);
            case "engine" -> component instanceof Engine engine && engine.findChild(selfHost.getName()) instanceof Host;
            case "host" -> component == selfHost;
            case "context" -> component == selfContext;
            case "connector" -> component instanceof Connector connector && isSelfConnector(connector);
            case "wrapper" -> component instanceof Wrapper wrapper && wrapper.getParent() == selfContext;
            default -> false;
        };
    }


    // ------------------------------------------------------------- Store


    /**
     * Build a ready to use {@link StoreConfig} that writes the live state of this server. The storeconfig MBean is not
     * required (and is not registered unless the {@code StoreConfigLifecycleListener} is configured in server.xml), so
     * the instance is created on demand from the default classpath registry.
     */
    private StoreConfig buildStoreConfig() throws Exception {
        StoreLoader loader = new StoreLoader();
        loader.load(null);
        StoreConfig storeConfig = new StoreConfig();
        storeConfig.setRegistry(loader.getRegistry());
        storeConfig.setServer(server);
        return storeConfig;
    }


    private void store(HttpServletResponse response) throws Exception {

        File confDir = new File(storeBase(), "conf");
        Set<String> before = serverXmlBackups(confDir);

        StoreConfig storeConfig = buildStoreConfig();
        // Same sequence as StoreConfig.store(Server), but keeping each
        // context in its current storage location (see
        // storeServerPreservingContexts).
        StoreFileMover mover = new StoreFileMover(storeBase(), storeConfig.getServerFilename(),
                storeConfig.getRegistry().getEncoding());
        try (PrintWriter writer = mover.getWriter()) {
            storeServerPreservingContexts(storeConfig, writer);
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.config"), e);
            throw new ConfigException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "STORE_FAILED",
                    Strings.sm().getString("manager2.configStoreFailed"));
        }
        mover.move();

        Set<String> after = serverXmlBackups(confDir);
        after.removeAll(before);
        String backup = after.isEmpty() ? null : after.iterator().next();

        log(Strings.sm().getString("manager2.configAuditStore", backup));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", Boolean.TRUE);
        payload.put("message", Strings.sm().getString("manager2.configStored", "conf/server.xml", backup == null ? "-" : backup));
        payload.put("file", "conf/server.xml");
        payload.put("backup", backup);
        Api.json(response, payload);
    }


    /**
     * The Catalina base directory that the store writes to. Normally the base of the running server. The
     * {@code manager2.store.base} system property overrides it (used by the integration tests to redirect the write to
     * a throw-away directory).
     */
    private static String storeBase() {
        String base = System.getProperty("manager2.store.base");
        return (base != null && !base.isEmpty()) ? base : Bootstrap.getCatalinaBase();
    }


    /**
     * The names of the storeconfig backup files ({@code server.xml.*}) in the conf directory.
     */
    private static Set<String> serverXmlBackups(File confDir) {
        Set<String> result = new HashSet<>();
        File[] files = confDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().startsWith("server.xml.")) {
                    result.add(file.getName());
                }
            }
        }
        return result;
    }


    private void preview(HttpServletResponse response) throws Exception {
        StoreConfig storeConfig = buildStoreConfig();
        StringWriter writer = new StringWriter();
        CapturingContextSF capturing = previewServerPreservingContexts(storeConfig, writer);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("xml", writer.toString());
        payload.put("files", new ArrayList<String>(capturing.getCaptured().keySet()));
        // The save restarts the manager (and resets the admin session) only
        // when the file of the context this manager runs in is rewritten.
        payload.put("restartsManager", selfContext != null && selfContext.getConfigFile() != null);
        Api.json(response, payload);
    }


    /**
     * Store the live server state to the given writer, keeping every context in its current storage location, as
     * regular StoreConfig does. The external context files are written to disk.
     */
    private void storeServerPreservingContexts(StoreConfig storeConfig, PrintWriter writer) throws Exception {
        storeServer(storeConfig, writer, null);
    }


    /**
     * Read-only variant of {@link #storeServerPreservingContexts} used by the store preview: the external context files
     * are captured in memory (see {@link CapturingContextSF}) instead of being written to disk, so the preview never
     * modifies the configuration on disk.
     */
    private CapturingContextSF previewServerPreservingContexts(StoreConfig storeConfig, StringWriter writer)
            throws Exception {
        CapturingContextSF capturing = new CapturingContextSF();
        storeServer(storeConfig, new PrintWriter(writer), capturing);
        return capturing;
    }


    /**
     * Store the live server state to the given writer, keeping each context in the location it currently lives in, as
     * regular StoreConfig does: a context backed by its own configuration file is written back to that file, a context
     * defined inline stays inline in {@code server.xml}.
     * <p>
     * The flags are set so that external contexts are written to their file ({@code storeSeparate=true},
     * {@code externalAllowed=true}) while inline contexts are not turned into new external files
     * ({@code externalOnly=false}). The previous flag values (and the store factory, when {@code capturing} is used)
     * are restored afterwards. When {@code capturing} is non-null (the read-only preview), the external files are
     * captured in memory by that factory instead of being written.
     */
    private void storeServer(StoreConfig storeConfig, PrintWriter writer, CapturingContextSF capturing)
            throws Exception {
        StoreFileMover mover =
                new StoreFileMover(Bootstrap.getCatalinaBase(), storeConfig.getServerFilename(), storeConfig.getRegistry().getEncoding());

        StoreDescription desc = storeConfig.getRegistry().findDescription(StandardContext.class);
        boolean oldSeparate = desc.isStoreSeparate();
        boolean oldAllowed = desc.isExternalAllowed();
        boolean oldOnly = desc.isExternalOnly();
        IStoreFactory oldFactory = desc.getStoreFactory();
        try {
            desc.setStoreSeparate(true);
            desc.setExternalAllowed(true);
            desc.setExternalOnly(false);
            if (capturing != null) {
                capturing.setRegistry(storeConfig.getRegistry());
                if (oldFactory != null) {
                    capturing.setStoreAppender(oldFactory.getStoreAppender());
                }
                desc.setStoreFactory(capturing);
            }
            StringWriter buffer = new StringWriter();
            storeConfig.store(new PrintWriter(buffer), -2, server);
            writer.write(XMLFormatPreserver.preserve(mover.getConfigOld(), buffer.toString(),
                    storeConfig.getRegistry().getEncoding()));
        } finally {
            desc.setStoreSeparate(oldSeparate);
            desc.setExternalAllowed(oldAllowed);
            desc.setExternalOnly(oldOnly);
            desc.setStoreFactory(oldFactory);
        }
    }


    /**
     * A {@link StandardContextSF} that stores external context files into in-memory buffers instead of on disk, for the
     * read-only store preview. Writing the (watched) context files would restart the running contexts, so during a
     * preview they are only captured.
     */
    private static final class CapturingContextSF extends StandardContextSF {

        private final Map<String, String> captured = new LinkedHashMap<>();

        public Map<String, String> getCaptured() {
            return captured;
        }

        @Override
        public void store(PrintWriter aWriter, int indent, Object aContext) throws Exception {
            if (aContext instanceof StandardContext context && getRegistry() != null) {
                StoreDescription desc = getRegistry().findDescription(context.getClass());
                if (desc != null && desc.isStoreSeparate() && desc.isExternalAllowed()) {
                    URL configFile = context.getConfigFile();
                    if (configFile == null && !desc.isExternalOnly() && !context.getDeployedFromServerXml()) {
                        // The store creates a new configuration file for a context that was not deployed from
                        // server.xml. Compute the path that file would get, without setting it on the context.
                        Host host = (Host) context.getParent();
                        ContextName cn = new ContextName(context.getName(), false);
                        File config = new File(host.getConfigBaseFile(), cn.getBaseName() + ".xml");
                        configFile = config.toURI().toURL();
                    }
                    if (configFile != null) {
                        // Capture the (new) external context file in memory instead of writing it. The element is
                        // written inline (storeSeparate is temporarily off) so the separate-file branch of
                        // StandardContextSF.store is not re-entered.
                        StringWriter buffer = new StringWriter();
                        PrintWriter w = new PrintWriter(buffer);
                        storeXMLHead(w);
                        boolean savedSeparate = desc.isStoreSeparate();
                        desc.setStoreSeparate(false);
                        try {
                            super.store(w, -2, aContext);
                        } finally {
                            desc.setStoreSeparate(savedSeparate);
                        }
                        w.flush();
                        captured.put(displayPath(configFile), buffer.toString());
                        return;
                    }
                }
            }
            super.store(aWriter, indent, aContext);
        }
    }


    /**
     * A context configuration file path for display: relative to the Catalina base where possible, otherwise the
     * absolute path.
     */
    private static String displayPath(URL configFile) {
        try {
            File file = new File(configFile.toURI());
            String base = Bootstrap.getCatalinaBase();
            if (base != null && !base.isEmpty()) {
                String rel = new File(base).toPath().relativize(file.toPath()).toString();
                if (!rel.startsWith("..")) {
                    return rel.replace(File.separatorChar, '/');
                }
            }
            return file.getPath().replace(File.separatorChar, '/');
        } catch (Exception e) {
            return String.valueOf(configFile);
        }
    }


    // ------------------------------------------------------------ Helpers


    private void requireServer() throws ConfigException {
        if (server == null) {
            throw new ConfigException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SERVER_UNAVAILABLE",
                    Strings.sm().getString("manager2.configServerUnavailable"));
        }
    }


    private static String path(HttpServletRequest request) {
        String path = request.getServletPath();
        String info = request.getPathInfo();
        if (info != null && !info.isEmpty()) {
            path = path + info;
        }
        if (path == null || path.isEmpty()) {
            path = "/api/config";
        }
        return path;
    }


    private static Map<String, Object> readJson(HttpServletRequest request) throws IOException {
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (body.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return new JSONParser(body).parseObject();
        } catch (Exception e) {
            throw new IllegalArgumentException(Strings.sm().getString("manager2.invalidJson", e.getMessage()), e);
        }
    }


    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }


    private static boolean bool(Object value, boolean defaultValue) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return defaultValue;
    }


    private static Integer intValue(Object value, int defaultValue) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isEmpty()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }


    private static List<String> stringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String s = String.valueOf(item).trim();
                if (!s.isEmpty()) {
                    result.add(s);
                }
            }
        } else if (value instanceof String s && !s.isEmpty()) {
            for (String part : s.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }


    private static String requiredName(Object value) throws ConfigException {
        String name = string(value);
        if (name == null || name.isEmpty() || !SAFE_NAME.matcher(name).matches()) {
            throw new ConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_NAME",
                    Strings.sm().getString("manager2.configInvalidName", name));
        }
        return name;
    }


    private static String capitalize(String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }


    /**
     * Encode a value for use as a node id segment. The value is URL encoded and the {@code %2F} sequences (originating
     * from {@code /} characters, e.g. in context paths) are replaced with a bare {@code +}, since Tomcat rejects
     * {@code %2F} in the request URI. A literal {@code +} in the value is percent encoded, so the replacement is
     * unambiguous.
     */
    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("%2F", "+");
    }


    /**
     * Encode a context path for use as a node id segment. The root context (whose path is the empty string) is encoded
     * as a bare {@code +}, the same segment that a path of {@code /} would produce, since {@link #dec(String)} turns a
     * bare {@code +} back into a context path.
     */
    private static String encContextPath(String path) {
        if (path.isEmpty()) {
            return "+";
        }
        return enc(path);
    }


    /**
     * The inverse of {@link #enc(String)}.
     */
    private static String dec(String value) {
        return URLDecoder.decode(value.replace("+", "%2F"), StandardCharsets.UTF_8);
    }


    /**
     * A controlled failure of one API operation, carrying the HTTP status, the machine readable code and the (already
     * localized) message.
     */
    private static final class ConfigException extends Exception {

        @Serial
        private static final long serialVersionUID = 1L;

        private final int status;

        private final String code;


        ConfigException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
