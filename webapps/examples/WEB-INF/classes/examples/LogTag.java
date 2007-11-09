/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package examples;


import javax.servlet.jsp.*;

import java.io.IOException;

/**
 * Log the contents of the body. Could be used to handle errors etc. 
 */
public class LogTag 
    extends ExampleTagBase
{
    boolean toBrowser = false;
    
    public void setToBrowser(String value) {
        if (value == null)
            toBrowser = false;
        else if (value.equalsIgnoreCase("true"))
            toBrowser = true;
        else
            toBrowser = false;
    }

    public int doStartTag() throws JspException {
        return EVAL_BODY_BUFFERED;
    }
    
    public int doAfterBody() throws JspException {
        try {
            String s = bodyOut.getString();
            System.err.println(s);
            if (toBrowser)
                bodyOut.writeOut(bodyOut.getEnclosingWriter());
            return SKIP_BODY;
        } catch (IOException ex) {
            throw new JspTagException(ex.toString());
        }
    }
}

    
        
    
