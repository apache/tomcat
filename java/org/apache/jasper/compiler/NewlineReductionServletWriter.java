package org.apache.jasper.compiler;

import java.io.PrintWriter;

/**
 * This class filters duplicate newlines instructions from the compiler output,
 * and therefore from the runtime JSP. The duplicates typically happen because
 * the compiler has multiple branches that write them, but they operate
 * independently and don't realize that the previous output was identical.
 *
 * Removing these lines makes the JSP more efficient by executing fewer operations during runtime.
 *
 * @author Engebretson, John
 * @author Kamnani, Jatin
 *
 */
public class NewlineReductionServletWriter extends ServletWriter {
    private static final String NEWLINE_WRITE_TEXT = "out.write('\\n');";

    private boolean lastWriteWasNewline;

    public NewlineReductionServletWriter(PrintWriter writer) {
        super(writer);
    }

    @Override
    public void printil(String s) {
        if (s.equals(NEWLINE_WRITE_TEXT)) {
            if (lastWriteWasNewline) {
                // do nothing
                return;
            } else {
                lastWriteWasNewline = true;
            }
        } else {
            lastWriteWasNewline = false;
        }
        super.printil(s);
    }

}