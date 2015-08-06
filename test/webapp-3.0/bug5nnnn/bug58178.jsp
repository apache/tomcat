<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib prefix="tags" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<html>
<head>
<title>Catch Tag Example</title>
</head>
<body>

<tags:bug58178>
   <fmt:parseNumber var="parsedNum" value="aaa" />
</tags:bug58178>
Parsed value: <c:out value="${parsedNum}"/>
</html>