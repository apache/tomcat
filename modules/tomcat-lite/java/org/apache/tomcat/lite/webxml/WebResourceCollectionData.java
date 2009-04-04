/**
 * 
 */
package org.apache.tomcat.lite.webxml;

import java.io.Serializable;
import java.util.ArrayList;

public class WebResourceCollectionData implements Serializable {
    public String webResourceName;
    public ArrayList urlPattern = new ArrayList();
    public ArrayList httpMethod = new ArrayList();
}