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
package jakarta.servlet.descriptor;

/**
 * Represents a taglib descriptor definitions in the deployment descriptor.
 *
 * @since Servlet 3.0
 */
public interface TaglibDescriptor {

    /**
     * Obtain the URI for the tag library.
     *
     * @return the URI for the tag library
     */
    public String getTaglibURI();

    /**
     * Obtain the location of the tag library.
     *
     * @return the location of the tag library
     */
    public String getTaglibLocation();
}
