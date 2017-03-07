<%--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
--%>
<%@ taglib prefix="tags" tagdir="/WEB-INF/tags" %>
<%!
static class TestListener implements javax.el.ELContextListener {

    private int jspCount = 0;
    private int tagCount = 0;

    @Override
    public void contextCreated(javax.el.ELContextEvent event) {
        javax.el.ELContext elContext = event.getELContext();
        if (elContext instanceof org.apache.jasper.el.ELContextImpl) {
            jspCount++;
        } else {
            tagCount++;
        }
        (new Exception()).printStackTrace();
    }

    public int getJspCount() {
        return jspCount;
    }

    public int getTagCount() {
        return tagCount;
    }
}

static TestListener listener = new TestListener();

private boolean listenerAdded;
%>
<%
synchronized(this) {
    if (!listenerAdded) {
        JspFactory factory = JspFactory.getDefaultFactory();
        JspApplicationContext jspApplicationContext = factory.getJspApplicationContext(application);
        jspApplicationContext.addELContextListener(listener);
        listenerAdded = true;
    }
}
%>
<html>
<body>
<p>JSP count: <%= listener.getJspCount() %></p>
<p>Tag count: <%= listener.getTagCount() %></p>
<tags:bug58178b />
<p>JSP count: <%= listener.getJspCount() %></p>
<p>Tag count: <%= listener.getTagCount() %></p>
</html>