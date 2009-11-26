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


package org.apache.tomcat.lite.service;


import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.tomcat.integration.DynamicObject;
import org.apache.tomcat.integration.ObjectManager;

/**
 * Send all registered JMX objects and properties as JSON.
 * 
 * Based on JMXProxy servlet, but:
 * - Async handler instead of servlet - so it works with 'raw' connector
 * - doesn't use JMX - integrates with the ObjectManager ( assuming OM 
 * provies a list of managed objects )
 * - all the reflection magic from modeler is implemented here.
 *
 * @author Costin Manolache
 */
public class JMXProxy extends ObjectManager implements Runnable  {

    static Logger log = Logger.getLogger(JMXProxy.class.getName());
    
    protected ObjectManager om;
    
    Map<Class, DynamicObject> types = new HashMap<Class, DynamicObject>();
    
    Map<String, Object> objects = new HashMap();
    
    
    public void bind(String name, Object o) {
        objects.put(name, o);
    }

    public void unbind(String name) {
        objects.remove(name);
    }

    
    public void setObjectManager(ObjectManager om) {
        this.om = om;
    }
    
    
    private DynamicObject getClassInfo(Class beanClass) {
        if (types.get(beanClass) != null) {
            return types.get(beanClass);
        }
        DynamicObject res = new DynamicObject(beanClass);
        types.put(beanClass, res);
        return res;
    }

    
    // --------------------------------------------------------- Public Methods

    public void getAttribute(PrintWriter writer, String onameStr, String att) {
        try {
            
            Object bean = objects.get(onameStr);
            Class beanClass = bean.getClass();
            DynamicObject ci = getClassInfo(beanClass);
            
            Object value = ci.getAttribute(bean, att);
            writer.println("OK - Attribute get '" + onameStr + "' - " + att
                    + "= " + escape(value.toString()));
        } catch (Exception ex) {
            writer.println("Error - " + ex.toString());
        }
    }


    public void setAttribute( PrintWriter writer,
                              String onameStr, String att, String val )
    {
        try {
            Object bean = objects.get(onameStr);
            Class beanClass = bean.getClass();
            DynamicObject ci = getClassInfo(beanClass);

            ci.setProperty(bean, att, val);
            writer.println("OK - Attribute set");
        } catch( Exception ex ) {
            writer.println("Error - " + ex.toString());
        }
    }

    public void listBeans( PrintWriter writer, String qry, boolean json )
    {
        if (json) {
            listBeansJson(writer, qry);
            return;
        }
        Set<String> names = objects.keySet();
        writer.println("OK - Number of results: " + names.size());
        writer.println();
        
        Iterator<String> it=names.iterator();
        while( it.hasNext()) {
            String oname=it.next();
            writer.println( "Name: " + oname);

            try {
                Object bean = objects.get(oname);
                Class beanClass = bean.getClass();
                DynamicObject ci = getClassInfo(beanClass);
                writer.println("modelerType: " + beanClass.getName());

                Object value=null;
                for (String attName: ci.attributeNames()) {
                    try {
                        value = ci.getAttribute(bean, attName);
                    } catch( Throwable t) {
                        System.err.println("Error getting attribute " + oname +
                            " " + attName + " " + t.toString());
                        continue;
                    }
                    if( value==null ) continue;
                    String valueString=value.toString();
                    writer.println( attName + ": " + escape(valueString));
                }
            } catch (Exception e) {
                // Ignore
            }
            writer.println();
        }

    }

    private static void json(PrintWriter writer, String name, String value) {
        writer.write("\"" + name +"\":" + "\"" + escapeJson(value) + "\",");
    }
    
   private void listBeansJson(PrintWriter writer, String qry) {
       Set<String> names = objects.keySet();
       writer.println("[");
       
       Iterator<String> it=names.iterator();
       while( it.hasNext()) {
           writer.print("{");
           String oname=it.next();
           json(writer, "name", oname);

           try {
               Object bean = objects.get(oname);
               Class beanClass = bean.getClass();
               DynamicObject ci = getClassInfo(beanClass);
               json(writer, "modelerType", beanClass.getName());

               Object value=null;
               for (String attName: ci.attributeNames()) {
                   try {
                       value = ci.getAttribute(bean, attName);
                   } catch( Throwable t) {
                       System.err.println("Error getting attribute " + oname +
                           " " + attName + " " + t.toString());
                       continue;
                   }
                   if( value==null ) continue;
                   String valueString=value.toString();
                   json(writer, attName, valueString);
               }
               writer.println("}");
           } catch (Exception e) {
               // Ignore
           }
       }
       writer.println("]");
   }
   
   public static String escapeJson(String value) {
       return value;
   }

   public static String escape(String value) {
        // The only invalid char is \n
        // We also need to keep the string short and split it with \nSPACE
        // XXX TODO
        int idx=value.indexOf( "\n" );
        if( idx < 0 ) return value;

        int prev=0;
        StringBuffer sb=new StringBuffer();
        while( idx >= 0 ) {
            appendHead(sb, value, prev, idx);

            sb.append( "\\n\n ");
            prev=idx+1;
            if( idx==value.length() -1 ) break;
            idx=value.indexOf('\n', idx+1);
        }
        if( prev < value.length() )
            appendHead( sb, value, prev, value.length());
        return sb.toString();
    }

    private static void appendHead( StringBuffer sb, String value, int start, int end) {
        if (end < 1) return;

        int pos=start;
        while( end-pos > 78 ) {
            sb.append( value.substring(pos, pos+78));
            sb.append( "\n ");
            pos=pos+78;
        }
        sb.append( value.substring(pos,end));
    }

    public boolean isSupported( String type ) {
        return true;
    }

    @Override
    public void run() {
        
    }
}
