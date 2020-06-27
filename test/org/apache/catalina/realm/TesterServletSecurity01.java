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
package org.apache.catalina.realm;

import jakarta.servlet.annotation.HttpConstraint;
import jakarta.servlet.annotation.HttpMethodConstraint;
import jakarta.servlet.annotation.ServletSecurity;

import org.apache.tomcat.util.descriptor.web.SecurityConstraint;

@ServletSecurity(value=@HttpConstraint,
        httpMethodConstraints={
                @HttpMethodConstraint(value="POST",
                        rolesAllowed=TestRealmBase.ROLE1),
                @HttpMethodConstraint(value="PUT",
                        rolesAllowed=SecurityConstraint.ROLE_ALL_ROLES),
                @HttpMethodConstraint(value="TRACE",
                        rolesAllowed=SecurityConstraint.ROLE_ALL_AUTHENTICATED_USERS)})
public class TesterServletSecurity01 {
    // Class is NO-OP. It is only used to 'host' the annotation.
}
