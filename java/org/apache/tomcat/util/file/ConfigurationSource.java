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
package org.apache.tomcat.util.file;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;

/**
 * Abstracts configuration file storage. Allows Tomcat embedding using the regular
 * configuration style.
 * This abstraction aims to be very simple and does not cover resource listing,
 * which is usually used for dynamic deployments that are usually not used when
 * embedding, as well as resource writing.
 */
public interface ConfigurationSource {

    /**
     * Represents a resource: a stream to the resource associated with
     * its URI.
     */
    public class Resource implements AutoCloseable {
        private final InputStream inputStream;
        private final URI uri;
        public Resource(InputStream inputStream, URI uri) {
            this.inputStream = inputStream;
            this.uri = uri;
        }
        public InputStream getInputStream() {
            return inputStream;
        }
        public URI getURI() {
            return uri;
        }
        public long getLastModified()
                throws MalformedURLException, IOException {
            return uri.toURL().openConnection().getLastModified();
        }
        @Override
        public void close() throws IOException {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    /**
     * Returns the contents of the main conf/server.xml file.
     * @return the server.xml as an InputStream
     * @throws IOException if an error occurs or if the resource does not exist
     */
    public default Resource getServerXml()
            throws IOException {
        return getConfResource("server.xml");
    }

    /**
     * Returns the contents of the shared conf/web.xml file. This usually
     * contains the declaration of the default and JSP servlets.
     * @return the web.xml as an InputStream
     * @throws IOException if an error occurs or if the resource does not exist
     */
    public default Resource getSharedWebXml()
            throws IOException {
        return getConfResource("web.xml");
    }

    /**
     * Get a resource, based on the conf path.
     * @param name The resource name
     * @return the resource as an InputStream
     * @throws IOException if an error occurs or if the resource does not exist
     */
    public default Resource getConfResource(String name)
            throws IOException {
        String fullName = "conf/" + name;
        return getResource(fullName);
    }

    /**
     * Get a resource, not based on the conf path.
     * @param name The resource name
     * @return the resource
     * @throws IOException if an error occurs or if the resource does not exist
     */
    public Resource getResource(String name)
            throws IOException;

    /**
     * Get a URI to the given resource. Unlike getResource, this will also
     * return URIs to locations where no resource exists.
     * @param name The resource name
     * @return a URI representing the resource location
     */
    public URI getURI(String name);

}
