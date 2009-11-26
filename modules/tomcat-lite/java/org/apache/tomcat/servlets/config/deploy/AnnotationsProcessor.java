/*
 */
package org.apache.tomcat.servlets.config.deploy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.tomcat.servlets.config.ServletContextConfig;
import org.apache.tomcat.servlets.config.ServletContextConfig.FilterData;
import org.apache.tomcat.servlets.config.ServletContextConfig.FilterMappingData;
import org.apache.tomcat.servlets.config.ServletContextConfig.ServiceData;
import org.apache.tomcat.servlets.config.ServletContextConfig.ServletData;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;


// TODO: move to 'tools' dir, don't include in runtime
// ( same for xml processor ) - use binary 
// 

// TODO: stupid ordering rules - probably will require merging configs
// and other ugliness.

// TODO: the even more brain-damaged ServletContextInitializer and 
// HandlesTypes - which requires recording all annotations for all classes,
// and worse - all the hierarchy to detect annotations on super.

/**
 * Post-compile or deploy tool: will scan classes and jars and 
 * generate an annotation file.
 * 
 * Will process:
 *  - annotations - for each class
 *  - find tld descriptors
 *  - web.xml and fragments
 *  
 * Output: a .ser file, for faster tomcat startup and a 'compete'
 * web.xml file.  
 * 
 * Tomcat should not read all classes each time it starts, or 
 * depend on bcel at runtime.
 * 
 * Servlet spec makes the worst use of annotations by requiring 
 * scanning all classes. This should be a compile-time tool only !
 * 
 * @author Costin Manolache
 */
public class AnnotationsProcessor {

    String baseDir;
    ServletContextConfig cfg;

    
    
    public AnnotationsProcessor(ServletContextConfig cfg2) {
        this.cfg = cfg2;
    }

    public void processWebapp(String baseN) throws IOException {
        if (!baseN.endsWith("/")) {
            baseN = baseN + "/";
        }
        processDir(baseN + "classes");
        
        File lib = new File(baseN + "lib");
        if (!lib.isDirectory()) {
            return;
        }
        File[] files = lib.listFiles();
        if (files == null) {
            return;
        }
        
        for (File f: files) {
            if (!f.isDirectory() && f.getName().endsWith(".jar")) {
                processJar(f.getCanonicalPath());
            }
        }        
    }
    
    public void processJar(String path) throws IOException {
        JarFile jar = new JarFile(path);
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.endsWith(".class")) {
                processClass(jar.getInputStream(entry),
                        "", name);
            } else if (name.equals("META-INF/services/javax.servlet.ServletContainerInitializer")) {
                
            }
            
        }
    }

    public void processDir(String base) throws IOException {
        // TODO: keep track of files to avoid loops
        processDir(new File(base));
    }
    
    public void processDir(File base) throws IOException {
        if (!base.isDirectory()) {
            return;
        }
        String baseN = base.getCanonicalPath();
        if (!baseN.endsWith("/")) {
            baseN = baseN + "/";
        }
        
        File[] files = base.listFiles();
        if (files != null) {
            for (File f: files) {
                if (f.isDirectory()) {
                    System.err.println(f);
                    processDir(f);
                } else if (f.getName().endsWith(".class")) {
                    try {
                        processClass(new FileInputStream(f), base.getCanonicalPath(),
                                f.getCanonicalPath());
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        }
        
    }
    
    private static Map<String, Object> asmList2Map(List list) {
        Map<String, Object> values = new HashMap();
        for (int i = 0; i < list.size(); i+= 2) {
            String name = (String) list.get(i);
            Object val = list.get(i + 1);
            values.put(name, val);
        }
        return values;
    }
    
    private static Map<String, AnnotationNode> annotationMap(List annL) {
        Map<String, AnnotationNode> values = new HashMap();
        if (annL != null) {
            for (Object annO: annL) {
                AnnotationNode ann = (AnnotationNode) annO;
                String name = Type.getType(ann.desc).toString();
                values.put(name, ann);
            }
        }
        return values;
    }
    
    
    
    public void processClass(InputStream classStream, 
                             String base,
                             String classFile) throws IOException {
        String classPath = classFile.substring(base.length() + 1);
        classPath = classPath.substring(0, classPath.length() - ".class".length());
        classPath = classPath.replace("/", ".");
        
        ClassReader classReader = new ClassReader(classStream);
        ClassNode cN = new ClassNode();
        classReader.accept(cN, 
                ClassReader.SKIP_CODE + ClassReader.SKIP_DEBUG + ClassReader.SKIP_FRAMES);

        String className = cN.name;
        
        Map<String, AnnotationNode> annotations = annotationMap(cN.visibleAnnotations);

        processServlets(className, annotations, cN);
        processWebFilter(className, annotations);
        
        AnnotationNode listenerA = annotations.get("javax.servlet.annotation.WebListener");
        if (listenerA != null) {
            // TODO: checks
            cfg.listenerClass.add(className);
        }
        
        
//        for (AnnotationNode mN : annotations.values()) {
//            String ann = Type.getType(mN.desc).toString();
//            Map<String, Object> values = asmList2Map(mN.values); 
//            
//            if ("javax.servlet.annotation.HandlesTypes".equals(ann)) {
//            } else if ("javax.servlet.annotation.MultipartConfig".equals(ann)) {
//            } else if ("javax.annotation.security.RunAs".equals(ann)) {
//            } else if ("javax.annotation.security.DeclareRoles".equals(ann)) {
//            } else if ("javax.annotation.security.RolesAllowed".equals(ann)) {
//            } else if ("javax.annotation.security.DenyAll".equals(ann)) {
//            } else if ("javax.annotation.security.PermitAll".equals(ann)) {
//            } else if ("javax.servlet.annotation.WebFilter".equals(ann)) {
//            } else if ("javax.servlet.annotation.WebServlet".equals(ann)) {
//                // in WebServlet
//            } else if ("javax.servlet.annotation.WebListener".equals(ann)) {
//            } else if ("javax.servlet.annotation.WebInitParam".equals(ann)) {
//                // In WebServlet, (WebFilter)
//            } else {
//                System.err.println("\n" + className + " " + Type.getType(mN.desc));
//            }
//        }

    }

    private void processServlets(String className,
            Map<String, AnnotationNode> annotations, ClassNode cn) {
        
        AnnotationNode webServletA = 
            annotations.get("javax.servlet.annotation.WebServlet");
        if (webServletA != null) {
            ServletData sd = new ServletData();
            // TODO: validity checks (implements servlet, etc)
            Map<String, Object> params = asmList2Map(webServletA.values); 
            
            processService(className, webServletA,
                    sd, params);
            
            if (params.containsKey("loadOnStartup")) {
                sd.loadOnStartup = (Integer)params.get("loadOnStartup");
            }
            if (annotations.get("javax.servlet.annotation.MultipartConfig") != null) {
                sd.multipartConfig = true;
            }
            
            AnnotationNode declareA = annotations.get("javax.annotation.security.DeclareRoles");
            if (declareA != null) {
                Map<String, Object> runAsParams = asmList2Map(declareA.values);
                ArrayList roles = (ArrayList) runAsParams.get("value");
                for (Object r: roles) {
                    sd.declaresRoles.add((String) r);
                }
            }
            
            AnnotationNode runAsA = annotations.get("javax.annotation.security.RunAs");
            if (runAsA != null) {
                Map<String, Object> runAsParams = asmList2Map(runAsA.values);
                
                sd.runAs = (String) runAsParams.get("value");
            }
            
            cfg.servlets.put(sd.name, sd);

            ArrayList urls = (ArrayList) params.get("urlPatterns");
            if (urls == null) {
                urls = (ArrayList) params.get("value");
            }

            for (Object urlO: urls) {
                cfg.servletMapping.put((String) urlO, 
                        sd.name);
            }

            // TODO: collect them, add on each of the URLs
            // TODO: also on methods
            AnnotationNode rolesA = annotations.get("javax.annotation.security.RolesAllowed");
            if (rolesA != null) {
                
            }            
            for (Object o: cn.methods) {
                MethodNode methodNode = (MethodNode) o;
                System.err.println(methodNode.desc);
            }
            
            
            
        }
    }

    private void processWebFilter(String className,
            Map<String, AnnotationNode> annotations) {
        AnnotationNode webFilterA = annotations.get("javax.servlet.annotation.WebServlet");
        if (webFilterA != null) {
            // TODO: validity checks (implements servlet, etc)
            
            FilterData sd = new FilterData();
            Map<String, Object> params = asmList2Map(webFilterA.values); 
            
            processService(className, webFilterA, sd, params);

            if (params.containsKey("asyncSupported")) {
                sd.asyncSupported = (Boolean) params.get("asyncSupported");
            }
            
            cfg.filters.put(sd.name, sd);
            
            ArrayList urls = (ArrayList) params.get("urlPatterns");
            if (urls == null) {
                urls = (ArrayList) params.get("value");
            }
            for (Object urlO: urls) {
                FilterMappingData fmap = new FilterMappingData();
                fmap.filterName = sd.name;
                fmap.urlPattern = (String) urlO;
                
                cfg.filterMappings.add(fmap);
            }
        }
    }

    private ServiceData processService(String className,
            AnnotationNode webServletA, ServiceData sd, Map<String, Object> params) {
        
        sd.name = (String) params.get("name");
        if (sd.name == null) {
            sd.name = className;
        }
        sd.className = className;
        
        if (params.containsKey("initParams")) {
            ArrayList initParamL = (ArrayList) params.get("initParams");
            for (Object initParamO: initParamL) {
                AnnotationNode initParamA = (AnnotationNode) initParamO;
                
                Map<String, Object> initParams = asmList2Map(initParamA.values);
                sd.initParams.put((String) initParams.get("name"),
                        (String) initParams.get("value"));
            }
        }
        
        if (params.containsKey("asyncSupported")) {
            sd.asyncSupported = (Boolean) params.get("asyncSupported");
        }
        return sd;
    }
    
    
}
