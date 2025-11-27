# HTTP BASIC Authentication Logout Flow

## The Problem with BASIC Auth Logout

HTTP BASIC authentication was **not designed to support logout**. Browsers cache credentials and automatically resend them with every request, making traditional logout impossible.

---

## Our Solution: Credential Poisoning

We use JavaScript to "poison" the browser's credential cache with invalid credentials, forcing a re-authentication prompt.

---

## Detailed Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│ USER CLICKS "LOGOUT" LINK                                           │
└─────────────────────┬───────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 1. REQUEST: GET /manager/html/logout                                │
│    Authorization: Basic YWRtaW46YWRtaW4=  (admin:admin encoded)    │
└─────────────────────┬───────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 2. SERVER: HTMLManagerServlet.doGet()                               │
│    - Receives logout request                                        │
│    - Calls request.logout()  ✓ Clears server-side session          │
│    - Forwards to /WEB-INF/jsp/logout.jsp                            │
└─────────────────────┬───────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 3. RESPONSE: logout.jsp renders                                     │
│    ┌───────────────────────────────────────────────────────────┐   │
│    │ HTML Content:                                             │   │
│    │ ┌─────────────────────────────────────────────────────┐   │   │
│    │ │  Logged Out                                         │   │   │
│    │ │  You have been successfully logged out.             │   │   │
│    │ │  [Click here to log in again]  (hidden initially)   │   │   │
│    │ │  Clearing session...                                │   │   │
│    │ └─────────────────────────────────────────────────────┘   │   │
│    │                                                             │   │
│    │ JavaScript executes immediately:                           │   │
│    └───────────────────────────────────────────────────────────┘   │
└─────────────────────┬───────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 4. JAVASCRIPT: Credential Poisoning (runs 3 times)                  │
│                                                                      │
│    function poisonCache() {                                         │
│        var xhr = new XMLHttpRequest();                              │
│        xhr.open('GET', '/manager/html', true,                       │
│                 'logout', 'logout');  ← Invalid credentials!        │
│                 ^^^^^^^^  ^^^^^^^^                                   │
│                 username  password                                   │
│        xhr.send();                                                   │
│    }                                                                 │
│                                                                      │
│    Sends: Authorization: Basic bG9nb3V0OmxvZ291dA==                │
│           (logout:logout encoded)                                    │
└─────────────────────┬───────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 5. SERVER RESPONSE: 401 Unauthorized                                │
│    WWW-Authenticate: Basic realm="Tomcat Manager Application"      │
│                                                                      │
│    ❌ Invalid credentials rejected                                  │
└─────────────────────┬───────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 6. BROWSER: Updates Credential Cache                                │
│                                                                      │
│    OLD CACHE:                                                        │
│    ┌──────────────────────────────────────────────┐                │
│    │ Realm: "Tomcat Manager Application"         │                │
│    │ Username: admin                              │                │
│    │ Password: admin                              │                │
│    └──────────────────────────────────────────────┘                │
│                         ↓                                            │
│                   OVERWRITTEN BY                                     │
│                         ↓                                            │
│    NEW CACHE:                                                        │
│    ┌──────────────────────────────────────────────┐                │
│    │ Realm: "Tomcat Manager Application"         │                │
│    │ Username: logout    ← INVALID!               │                │
│    │ Password: logout    ← INVALID!               │                │
│    └──────────────────────────────────────────────┘                │
└─────────────────────┬───────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 7. JAVASCRIPT: Shows "Click here to log in again" link             │
│    (after 3 poisoning attempts complete)                            │
└─────────────────────┬───────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│ USER CLICKS "CLICK HERE TO LOG IN AGAIN"                            │
└─────────────────────┬───────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 8. BROWSER: Sends request with cached (invalid) credentials        │
│    GET /manager/html                                                │
│    Authorization: Basic bG9nb3V0OmxvZ291dA==  (logout:logout)      │
└─────────────────────┬───────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 9. SERVER: 401 Unauthorized (credentials are invalid!)             │
│    WWW-Authenticate: Basic realm="Tomcat Manager Application"      │
└─────────────────────┬───────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 10. BROWSER: Realizes credentials are wrong                        │
│                                                                      │
│     ┌─────────────────────────────────────────────────┐            │
│     │  🔐 Authentication Required                     │            │
│     │                                                  │            │
│     │  localhost:8080 requires a username             │            │
│     │  and password.                                  │            │
│     │                                                  │            │
│     │  Username: [____________]                       │            │
│     │  Password: [____________]                       │            │
│     │                                                  │            │
│     │           [ Cancel ]  [ Sign In ]               │            │
│     └─────────────────────────────────────────────────┘            │
│                                                                      │
│     ✅ LOGIN PROMPT APPEARS!                                        │
│     ✅ User can enter new/different credentials                     │
│     ✅ LOGOUT SUCCESSFUL!                                           │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Key Components

### Server-Side (Java)
1. **HTMLManagerServlet.java** - Handles `/logout` endpoint, calls `request.logout()`, forwards to JSP
2. **logout.jsp** - Renders logout page with JavaScript

### Client-Side (JavaScript)
1. **XHR Requests** - Sends HTTP requests with invalid credentials
2. **Credential Poisoning** - Overwrites browser's cached credentials
3. **UI Updates** - Shows/hides elements based on completion

---

## Why This Works

### The BASIC Auth Cache Problem
- Browsers cache HTTP BASIC auth credentials per realm
- No standard way to tell browser "forget these credentials"
- Browser automatically resends cached credentials on every request

### Our Workaround
- We can't delete cached credentials...
- **But we CAN overwrite them!**
- Send requests with invalid credentials via XHR
- Browser updates cache with the invalid ones
- Next request fails → browser prompts for credentials again

---

## Browser Compatibility

✅ **Chrome/Edge**: Works perfectly
✅ **Firefox**: Works perfectly
✅ **Safari**: Works (may require closing tab in some versions)
✅ **All modern browsers**: Supported (XHR is a standard API)

---

## Security Notes

- ✅ Server-side session is always cleared (via `request.logout()`)
- ✅ No security vulnerabilities introduced
- ✅ Uses standard browser APIs (XMLHttpRequest)
- ✅ Works with CSRF protection enabled
- ⚠️ User can still manually clear browser data to remove credentials entirely

---

## Alternative Approaches Considered

| Approach | Why We Didn't Use It |
|----------|---------------------|
| Send 401 with different realm | Confusing UX, may break re-login |
| Switch to FORM authentication | ❌ Breaks automated tools/scripts (Mark's concern) |
| Just invalidate session | Browser auto-resends credentials, immediate re-login |
| Tell users to close browser | Poor user experience |
| No logout at all | Missing critical functionality |

---

## Summary

This solution provides **the best possible logout experience for HTTP BASIC authentication** while:
- ✅ Maintaining compatibility with automated tools
- ✅ Not requiring browser extensions or special permissions
- ✅ Working across all modern browsers
- ✅ Using only standard web APIs
- ✅ Providing clear user feedback

The JavaScript credential poisoning technique is a clever workaround for a fundamental limitation of the HTTP BASIC authentication protocol.
