/**
 *  Copyright 2015 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package org.apache.tomcat.util.providers.stream;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;


/**
 * @author <a href="mailto:kconner@redhat.com">Kevin Conner</a>
 */
public interface StreamProvider {

    public InputStream openStream(String url, Map<String, String> headers, int connectTimeout, int readTimeout) throws IOException;

}
