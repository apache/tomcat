<%@page session="false"%>

<pre>
Use cases:

1. Simple dispatch 
 - servlet does startAsync()
 - background thread calls ctx.dispatch() 
   <a href="<%=response.encodeURL("/examples/async/async0")%>"> Async 0 </a>
 
2. Simple dispatch
 - servlet does startAsync()
 - background thread calls dispatch(/path/to/jsp)
   <a href="<%=response.encodeURL("/examples/async/async1")%>"> Async 1 </a>
 
3. Simple dispatch
 - servlet does startAsync()
 - background thread calls writes and calls complete()
   <a href="<%=response.encodeURL("/examples/async/async2")%>"> Async 2 </a>


3. Timeout s1
 - servlet does a startAsync()
 - servlet does a setAsyncTimeout
 - returns - waits for timeout to happen should return error page 
 
4. Timeout s2
 - servlet does a startAsync()
 - servlet does a setAsyncTimeout
 - servlet does a addAsyncListener
 - returns - waits for timeout to happen and listener invoked 
</pre>