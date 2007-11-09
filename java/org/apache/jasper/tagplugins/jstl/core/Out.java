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


package org.apache.jasper.tagplugins.jstl.core;

import org.apache.jasper.compiler.tagplugin.TagPlugin;
import org.apache.jasper.compiler.tagplugin.TagPluginContext;


public final class Out implements TagPlugin {
    
    public void doTag(TagPluginContext ctxt) {
        
        //these two data member are to indicate 
        //whether the corresponding attribute is specified
        boolean hasDefault=false, hasEscapeXml=false;
        hasDefault = ctxt.isAttributeSpecified("default");
        hasEscapeXml = ctxt.isAttributeSpecified("escapeXml");
        
        //strValName, strEscapeXmlName & strDefName are two variables' name 
        //standing for value, escapeXml and default attribute
        String strValName = ctxt.getTemporaryVariableName();
        String strDefName = ctxt.getTemporaryVariableName();
        String strEscapeXmlName = ctxt.getTemporaryVariableName();
        
        //according to the tag file, the value attribute is mandatory.
        ctxt.generateJavaSource("String " + strValName + " = null;");
        ctxt.generateJavaSource("if(");
        ctxt.generateAttribute("value");
        ctxt.generateJavaSource("!=null){");
        ctxt.generateJavaSource("    " + strValName + " = (");
        ctxt.generateAttribute("value");
        ctxt.generateJavaSource(").toString();");
        ctxt.generateJavaSource("}");
        
        //initiate the strDefName with null.
        //if the default has been specified, then assign the value to it;
        ctxt.generateJavaSource("String " + strDefName + " = null;\n");
        if(hasDefault){
            ctxt.generateJavaSource("if(");
            ctxt.generateAttribute("default");
            ctxt.generateJavaSource(" != null){");
            ctxt.generateJavaSource(strDefName + " = (");
            ctxt.generateAttribute("default");
            ctxt.generateJavaSource(").toString();");
            ctxt.generateJavaSource("}");
        }
        
        //initiate the strEscapeXmlName with true;
        //if the escapeXml is specified, assign the value to it;
        ctxt.generateJavaSource("boolean " + strEscapeXmlName + " = true;");
        if(hasEscapeXml){
            ctxt.generateJavaSource(strEscapeXmlName + " = Boolean.parseBoolean((");
            ctxt.generateAttribute("default");
            ctxt.generateJavaSource(").toString());");
        }
        
        //main part. 
        ctxt.generateJavaSource("if(null != " + strValName +"){");
        ctxt.generateJavaSource("    if(" + strEscapeXmlName + "){");
        ctxt.generateJavaSource("        " + strValName + " = org.apache.jasper.tagplugins.jstl.Util.escapeXml(" + strValName + ");");
        ctxt.generateJavaSource("    }");
        ctxt.generateJavaSource("    out.write(" + strValName + ");");
        ctxt.generateJavaSource("}else{");
        ctxt.generateJavaSource("    if(null != " + strDefName + "){");
        ctxt.generateJavaSource("        if(" + strEscapeXmlName + "){");
        ctxt.generateJavaSource("            " + strDefName + " = org.apache.jasper.tagplugins.jstl.Util.escapeXml(" + strDefName + ");");
        ctxt.generateJavaSource("        }");
        ctxt.generateJavaSource("        out.write(" + strDefName + ");");
        ctxt.generateJavaSource("    }else{");
        ctxt.generateBody();
        ctxt.generateJavaSource("    }");
        ctxt.generateJavaSource("}");   
    }
}
