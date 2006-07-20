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
public class GeneratorJk1 implements WebXml2Jk.MappingGenerator {
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

    public void generateStart( ) throws IOException  {
        File base=wxml.getJkDir();
        File outF=new File(base, "jk.conf");
        out=new PrintWriter( new FileWriter( outF ));
        
        out.println("# This must be included in the virtual host section for " + vhost );
    }

    public void generateEnd() {
        out.close();
    }

    
    public void generateServletMapping( String servlet, String url ) {
        out.println( "JkMount " + cpath + url + " " + worker);
    }

    public void generateFilterMapping( String servlet, String url ) {
        out.println( "JkMount " + cpath + url + " " + worker);
    }

    public void generateLoginConfig( String loginPage,
                                        String errPage, String authM ) {
        out.println( "JkMount " + cpath + loginPage + " " + worker);
    }

    public void generateErrorPage( int err, String location ) {

    }

    public void generateMimeMapping( String ext, String type ) {

    }
    
    public void generateWelcomeFiles( Vector wf ) {

    }
                                            
    
    public void generateConstraints( Vector urls, Vector methods, Vector roles, boolean isSSL ) {
        for( int i=0; i<urls.size(); i++ ) {
            String url=(String)urls.elementAt(i);

            out.println( "JkMount " + cpath + url + " " + worker);
        }
    }
}
