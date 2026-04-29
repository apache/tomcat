# Security Policy

## Before You Report - Required Self-Check

**Complete this checklist. If you answer "No" to any question, do not submit a report:**

- [ ] I have read the [Tomcat Security Model](https://tomcat.apache.org/security-model.html) and my finding doesn't require access to config files, binaries, or admin interfaces
- [ ] I have written a working Tomcat JUnit test case that compiles, runs, and demonstrates the vulnerability
- [ ] I have tested against a real Tomcat instance - this is not theoretical analysis or scanner output
- [ ] I am submitting in plain text (no PDFs, archives, videos, or formatted documents)

**If you cannot check all boxes, your report will be rejected.**

## Where to Report

**📧 security@tomcat.apache.org** - Exclusively for undisclosed security vulnerabilities in Tomcat

**Not for:** Bug reports ([Bugzilla](https://bz.apache.org/bugzilla/)), configuration help ([users list](https://tomcat.apache.org/lists.html)), theoretical issues, scanner output, or application vulnerabilities.

## Common Invalid Reports - Do Not Send

These will be **rejected without response**:

- ❌ "Tomcat allows deploying WAR files that execute code" - Web apps are trusted, this is normal
- ❌ "I can modify server.xml to change behavior" - Config files are trusted
- ❌ "Sending lots of data crashes Tomcat" - Generic DoS without non-linear consumption
- ❌ "XSS/SQLi in my deployed application" - Your app's bug, not Tomcat's
- ❌ "Manager app accessible with valid password" - Admin users are trusted
- ❌ Scanner reports without actual testing - Must verify manually with working PoC
- ❌ Theoretical vulnerabilities or AI-generated reports - Must include working test case

**Review the [security model](https://tomcat.apache.org/security-model.html) to understand what qualifies as a Tomcat vulnerability.**

## Required: Working Test Case

**Every report MUST include a complete Tomcat JUnit test case that:**
- Extends `TomcatBaseTest` or appropriate test base class
- Compiles against Tomcat source without errors
- Runs via `ant test` and demonstrates the vulnerability
- Uses real Tomcat APIs (not pseudo-code)
- Includes comments explaining the attack and impact

**The test must actually work - we will run it. If it doesn't compile or doesn't reproduce the issue, your report will be rejected.**

### Example Structure

```java
package org.apache.catalina.security;

import org.junit.Assert;
import org.junit.Test;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.Context;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Demonstrates [specific vulnerability].
 * Attack: [how it works]
 * Impact: [security consequence]
 */
public class TestSecurityIssueXXXXX extends TomcatBaseTest {

    @Test
    public void testVulnerabilityName() throws Exception {
        // Setup: Configure Tomcat to expose the vulnerability
        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addContext("", null);
        Tomcat.addServlet(ctx, "test", new YourTestServlet());
        ctx.addServletMappingDecoded("/test", "test");
        tomcat.start();

        // Attack: Send malicious request
        ByteChunk response = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/test?malicious=payload",
                        response, null);

        // Verify: Demonstrate security impact
        Assert.assertNotEquals("Should reject malicious input", 200, rc);
        // Or: Assert.assertFalse("Response leaked sensitive data",
        //                        response.toString().contains("secret"));
    }
}
```

## Required Report Information

Include all of the following:
1. **Summary** - 1-2 sentences describing the vulnerability
2. **Tomcat Version** - Exact version tested (e.g., `Apache-Tomcat/11.0.5`)
3. **Configuration** - Non-default settings needed to reproduce (if any)
4. **Impact** - Specific consequence (RCE, information disclosure, authentication bypass, etc.)
5. **Test Case** - Working JUnit test as described above

**Format:** Plain text only (email body or `.txt`/`.java` attachments). No `.zip`, `.pdf`, `.docx`, videos, or screenshots.

## What Happens Next

1. **Acknowledgment** - Usually within a few business days, if the report passes initial screening
2. **Validation** - We run your test case and assess impact against the security model
3. **Fix & Disclosure** - If valid, we develop a fix and coordinate public disclosure timing
4. **Credit** - Valid reports receive acknowledgment in security advisories

Allow reasonable time (typically 90+ days) for fix development. We'll work with you on disclosure timing.

## Security Model Quick Reference

**Trusted (not security bugs):**
- Administrative users, configuration files, binaries
- Deployed web applications (app bugs are the app's responsibility)
- Manager/Host Manager access, JMX, debugging interfaces

**Untrusted (potential security bugs):**
- HTTP/AJP connector data from clients
- Malicious requests via supported protocols

See the full [security model](https://tomcat.apache.org/security-model.html) for details.

## Published Vulnerabilities & Updates

- **Advisories:** https://tomcat.apache.org/security.html
- **Announcements:** [Mailing lists](https://tomcat.apache.org/lists.html)
- **Secure configuration:** https://tomcat.apache.org/tomcat-11.0-doc/security-howto.html

---

**Thank you for helping keep Apache Tomcat secure through high-quality, actionable vulnerability reports. 🔒**
