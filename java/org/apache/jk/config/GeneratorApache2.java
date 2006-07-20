/*
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.jk.config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Vector;

import org.w3c.dom.Node;


/* Naming conventions:

JK_CONF_DIR == serverRoot/work  ( XXX /jkConfig ? )

- Each vhost has a sub-dir named after the canonycal name

- For each webapp in a vhost, there is a separate WEBAPP_NAME.jkmap

- In httpd.conf ( or equivalent servers ), in each virtual host you
should "Include JK_CONF_DIR/VHOST/jk_apache.conf". The config
file will contain the Alias declarations and other rules required
for apache operation. Same for other servers. 

- WebXml2Jk will be invoked by a config tool or automatically for each
webapp - it'll generate the WEBAPP.jkmap files and config fragments.

WebXml2Jk will _not_ generate anything else but mappings.
It should _not_ try to guess locations or anything else - that's
another components' job.

*/

/**
 *
 * @author Costin Manolache
 */
public class GeneratorApache2 implements WebXml2Jk.MappingGenerator {
    WebXml2Jk wxml;
    String vhost;
    String cpath;
    String worker;
    PrintWriter out;
    
    public void setWebXmlReader(WebXml2Jk wxml ) {
        this.wxml=wxml;
        vhost=wxml.vhost;
        cpath=wxml.cpath;
        worker=wxml.worker;
    }

    public void generateStart() throws IOException {
        File base=wxml.getJkDir();
        File outF=new File(base, "jk2.conf");
        out=new PrintWriter( new FileWriter( outF ));

        out.println("# Must be included in a virtual host context for " + vhost );

        out.println("Alias " + cpath + " \"" + wxml.docBase + "\"");
        out.println("<Directory \"" + wxml.docBase + "\" >");
        out.println("  Options Indexes FollowSymLinks");
        generateMimeMapping( out );
        generateWelcomeFiles( out);

        // If we use this instead of extension mapping for jsp we can avoid most
        // jsp security problems.
        out.println("  AddHandler jakarta-servlet2 .jsp");
        out.println("</Directory>");
        out.println();
        
        out.println("<Location \"" + cpath + "/WEB-INF\" >");
        out.println("  AllowOverride None");
        out.println("  Deny from all");
        out.println("</Location>");
        out.println();
        out.println("<Location \"" + cpath + "/META-INF\" >");
        out.println("  AllowOverride None");
        out.println("  Deny from all");
        out.println("</Location>");
        out.println();
    }

    private void generateWelcomeFiles( PrintWriter out ) {
        Vector wf= wxml.getWellcomeFiles();
        out.print("  DirectoryIndex ");
        for( int i=0; i<wf.size(); i++ ) {
            out.print( " " + (String)wf.elementAt(i));
        }
        out.println();
    }
    
    private void generateMimeMapping( PrintWriter out ) {
        Node webN=wxml.getWebXmlNode();
        for( Node mapN=WebXml2Jk.getChild( webN, "mime-mapping" );
             mapN != null; mapN = WebXml2Jk.getNext( mapN ) ) {
            String ext=WebXml2Jk.getChildContent( mapN, "extension" );
            String type=WebXml2Jk.getChildContent( mapN, "mime-type" );

            out.println("  AddType " + type + " " + ext );
        }
        

    }

    public void generateEnd() {
        out.close();
    }
    
    public void generateServletMapping( String servlet, String url ) {
        out.println( "<Location \"" + cpath + url + "\" >");
        out.println( "  SetHandler jakarta-servlet2" );
        out.println( "  JkUriSet group " + worker );
        out.println( "  JkUriSet servlet " +  servlet);
        out.println( "  JkUriSet host " +  vhost );
        out.println( "  JkUriSet context " +  cpath );
        out.println( "</Location>");
        out.println();
    }

    public void generateFilterMapping( String servlet, String url ) {
        out.println( "<Location \"" + cpath + url + "\" >");
        out.println( "  SetHandler jakarta-servlet2" );
        out.println( "  JkUriSet group " + worker );
        out.println( "  JkUriSet servlet " +  servlet);
        out.println( "  JkUriSet host " +  vhost );
        out.println( "  JkUriSet context " +  cpath );
        out.println( "</Location>");
        out.println();
    }

    public void generateLoginConfig( String loginPage,
                                     String errPage, String authM ) {
        out.println( "<Location \"" + cpath + loginPage + "\" >");
        out.println( "  SetHandler jakarta-servlet2" );
        out.println( "  JkUriSet group " + worker );
        out.println( "  JkUriSet host " +  vhost );
        out.println( "  JkUriSet context " +  cpath );
        out.println( "</Location>");
        out.println();
    }

    public void generateErrorPage( int err, String location ) {
        
    }

    // XXX Only if BASIC/DIGEST and 'integrated auth'
    public void generateConstraints( Vector urls, Vector methods, Vector roles, boolean isSSL ) {
        for( int i=0; i<urls.size(); i++ ) {
            String url=(String)urls.elementAt(i);

            out.println( "<Location \"" + cpath + url + "\" >");

            if( methods.size() > 0 ) {
                out.print("  <Limit ");
                for( int j=0; j<methods.size(); j++ ) {
                    String m=(String)methods.elementAt(j);
                    out.print( " " +  m);
                }
                out.println(  " >" );
            }

            out.println( "    AuthType basic" );
            out.print( "    Require group " );
            for( int j=0; j<roles.size(); j++ ) {
                String role=(String)roles.elementAt(j);
                out.print( " " +  role);
            }
            out.println();

            if( methods.size() > 0 ) {
                out.println("  </Limit>");
            }
            
            out.println( "</Location>");
        }
    }
}
