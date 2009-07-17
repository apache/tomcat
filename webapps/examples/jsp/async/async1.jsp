<%@page session="false"%>
Output from async1.jsp
<%
System.out.println("Inside Async 1");
  if (request.isAsyncStarted()) {
    request.getAsyncContext().complete();
  }
%>
