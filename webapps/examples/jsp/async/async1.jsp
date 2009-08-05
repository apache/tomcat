<%@page session="false"%>
Output from async1.jsp
Type is <%=request.getDispatcherType()%>
<%
System.out.println("Inside Async 1");
  if (request.isAsyncStarted()) {
    request.getAsyncContext().complete();
  }
%>
Completed async request at <%=new java.sql.Date()%>