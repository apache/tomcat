<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<c:catch var="error">
  <jsp:doBody/>
</c:catch>
   
<c:if test="${error != null}">
  <p>PASS<br/>
  Error detected<br/>
  The exception is : ${error} <br />
  The message is: ${error.message}</p>
</c:if>
<c:if test="${error == null}">
  <p>FAIL<br/>
  Error not detected</p>
</c:if>