/*
 * Copyright  2000-2009 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); 
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. 
 *
 */
package org.apache.tomcat.util.bcel.verifier;

import java.awt.Color;
import org.apache.tomcat.util.bcel.Repository;
import org.apache.tomcat.util.bcel.classfile.JavaClass;

/**
 * A class for simple graphical class file verification.
 * Use the main(String []) method with fully qualified
 * class names as arguments to use it as a stand-alone
 * application.
 * Use the VerifyDialog(String) constructor to use this
 * class in your application.
 * [This class was created using VisualAge for Java,
 * but it does not work under VAJ itself (Version 3.02 JDK 1.2)]
 * @version $Id$
 * @author Enver Haase
 * @see #main(String[])
 * @see #VerifyDialog(String)
 */
public class VerifyDialog extends javax.swing.JDialog {

    /** Machine-generated. */
    private javax.swing.JPanel ivjJDialogContentPane = null;
    /** Machine-generated. */
    private javax.swing.JPanel ivjPass1Panel = null;
    /** Machine-generated. */
    private javax.swing.JPanel ivjPass2Panel = null;
    /** Machine-generated. */
    private javax.swing.JPanel ivjPass3Panel = null;
    /** Machine-generated. */
    private javax.swing.JButton ivjPass1Button = null;
    /** Machine-generated. */
    private javax.swing.JButton ivjPass2Button = null;
    /** Machine-generated. */
    private javax.swing.JButton ivjPass3Button = null;
    /** Machine-generated. */
    IvjEventHandler ivjEventHandler = new IvjEventHandler();
    /**
     * The class to verify. Default set to 'java.lang.Object'
     * in case this class is instantiated via one of the many
     * machine-generated constructors.
     */
    private String class_name = "java.lang.Object";
    /**
     * This field is here to count the number of open VerifyDialog
     * instances so the JVM can be exited afer every Dialog had been
     * closed.
     */
    private static int classes_to_verify;

    /** Machine-generated. */
    class IvjEventHandler implements java.awt.event.ActionListener {

        public void actionPerformed( java.awt.event.ActionEvent e ) {
            if (e.getSource() == VerifyDialog.this.getPass1Button()) {
                connEtoC1(e);
            }
            if (e.getSource() == VerifyDialog.this.getPass2Button()) {
                connEtoC2(e);
            }
            if (e.getSource() == VerifyDialog.this.getPass3Button()) {
                connEtoC3(e);
            }
            if (e.getSource() == VerifyDialog.this.getFlushButton()) {
                connEtoC4(e);
            }
        };
    };

    /** Machine-generated. */
    private javax.swing.JButton ivjFlushButton = null;


    /** Machine-generated. */
    public VerifyDialog() {
        super();
        initialize();
    }


    /** Machine-generated. */
    public VerifyDialog(java.awt.Dialog owner) {
        super(owner);
    }


    /** Machine-generated. */
    public VerifyDialog(java.awt.Dialog owner, String title) {
        super(owner, title);
    }


    /** Machine-generated. */
    public VerifyDialog(java.awt.Dialog owner, String title, boolean modal) {
        super(owner, title, modal);
    }


    /** Machine-generated. */
    public VerifyDialog(java.awt.Dialog owner, boolean modal) {
        super(owner, modal);
    }


    /** Machine-generated. */
    public VerifyDialog(java.awt.Frame owner) {
        super(owner);
    }


    /** Machine-generated. */
    public VerifyDialog(java.awt.Frame owner, String title) {
        super(owner, title);
    }


    /** Machine-generated. */
    public VerifyDialog(java.awt.Frame owner, String title, boolean modal) {
        super(owner, title, modal);
    }


    /** Machine-generated. */
    public VerifyDialog(java.awt.Frame owner, boolean modal) {
        super(owner, modal);
    }


    /**
     * Use this constructor if you want a possibility to verify other
     * class files than java.lang.Object.
     * @param fully_qualified_class_name java.lang.String
     */
    public VerifyDialog(String fully_qualified_class_name) {
        super();
        int dotclasspos = fully_qualified_class_name.lastIndexOf(".class");
        if (dotclasspos != -1) {
            fully_qualified_class_name = fully_qualified_class_name.substring(0, dotclasspos);
        }
        fully_qualified_class_name = fully_qualified_class_name.replace('/', '.');
        class_name = fully_qualified_class_name;
        initialize();
    }


    /** Machine-generated. */
    private void connEtoC1( java.awt.event.ActionEvent arg1 ) {
        try {
            // user code begin {1}
            // user code end
            this.pass1Button_ActionPerformed(arg1);
            // user code begin {2}
            // user code end
        } catch (java.lang.Throwable ivjExc) {
            // user code begin {3}
            // user code end
            handleException(ivjExc);
        }
    }


    /** Machine-generated. */
    private void connEtoC2( java.awt.event.ActionEvent arg1 ) {
        try {
            // user code begin {1}
            // user code end
            this.pass2Button_ActionPerformed(arg1);
            // user code begin {2}
            // user code end
        } catch (java.lang.Throwable ivjExc) {
            // user code begin {3}
            // user code end
            handleException(ivjExc);
        }
    }


    /** Machine-generated. */
    private void connEtoC3( java.awt.event.ActionEvent arg1 ) {
        try {
            // user code begin {1}
            // user code end
            this.pass4Button_ActionPerformed(arg1);
            // user code begin {2}
            // user code end
        } catch (java.lang.Throwable ivjExc) {
            // user code begin {3}
            // user code end
            handleException(ivjExc);
        }
    }


    /** Machine-generated. */
    private void connEtoC4( java.awt.event.ActionEvent arg1 ) {
        try {
            // user code begin {1}
            // user code end
            this.flushButton_ActionPerformed(arg1);
            // user code begin {2}
            // user code end
        } catch (java.lang.Throwable ivjExc) {
            // user code begin {3}
            // user code end
            handleException(ivjExc);
        }
    }


    /** Machine-generated. */
    public void flushButton_ActionPerformed( java.awt.event.ActionEvent actionEvent ) {
        VerifierFactory.getVerifier(class_name).flush();
        Repository.removeClass(class_name); // Make sure it will be reloaded.
        getPass1Panel().setBackground(Color.gray);
        getPass1Panel().repaint();
        getPass2Panel().setBackground(Color.gray);
        getPass2Panel().repaint();
        getPass3Panel().setBackground(Color.gray);
        getPass3Panel().repaint();
    }


    /** Machine-generated. */
    private javax.swing.JButton getFlushButton() {
        if (ivjFlushButton == null) {
            try {
                ivjFlushButton = new javax.swing.JButton();
                ivjFlushButton.setName("FlushButton");
                ivjFlushButton.setText("Flush: Forget old verification results");
                ivjFlushButton.setBackground(java.awt.SystemColor.controlHighlight);
                ivjFlushButton.setBounds(60, 215, 300, 30);
                ivjFlushButton.setForeground(java.awt.Color.red);
                ivjFlushButton.setActionCommand("FlushButton");
                // user code begin {1}
                // user code end
            } catch (java.lang.Throwable ivjExc) {
                // user code begin {2}
                // user code end
                handleException(ivjExc);
            }
        }
        return ivjFlushButton;
    }


    /** Machine-generated. */
    private javax.swing.JPanel getJDialogContentPane() {
        if (ivjJDialogContentPane == null) {
            try {
                ivjJDialogContentPane = new javax.swing.JPanel();
                ivjJDialogContentPane.setName("JDialogContentPane");
                ivjJDialogContentPane.setLayout(null);
                getJDialogContentPane().add(getPass1Panel(), getPass1Panel().getName());
                getJDialogContentPane().add(getPass3Panel(), getPass3Panel().getName());
                getJDialogContentPane().add(getPass2Panel(), getPass2Panel().getName());
                getJDialogContentPane().add(getPass1Button(), getPass1Button().getName());
                getJDialogContentPane().add(getPass2Button(), getPass2Button().getName());
                getJDialogContentPane().add(getPass3Button(), getPass3Button().getName());
                getJDialogContentPane().add(getFlushButton(), getFlushButton().getName());
                // user code begin {1}
                // user code end
            } catch (java.lang.Throwable ivjExc) {
                // user code begin {2}
                // user code end
                handleException(ivjExc);
            }
        }
        return ivjJDialogContentPane;
    }


    /** Machine-generated. */
    private javax.swing.JButton getPass1Button() {
        if (ivjPass1Button == null) {
            try {
                ivjPass1Button = new javax.swing.JButton();
                ivjPass1Button.setName("Pass1Button");
                ivjPass1Button.setText("Pass1: Verify binary layout of .class file");
                ivjPass1Button.setBackground(java.awt.SystemColor.controlHighlight);
                ivjPass1Button.setBounds(100, 40, 300, 30);
                ivjPass1Button.setActionCommand("Button1");
                // user code begin {1}
                // user code end
            } catch (java.lang.Throwable ivjExc) {
                // user code begin {2}
                // user code end
                handleException(ivjExc);
            }
        }
        return ivjPass1Button;
    }


    /** Machine-generated. */
    private javax.swing.JPanel getPass1Panel() {
        if (ivjPass1Panel == null) {
            try {
                ivjPass1Panel = new javax.swing.JPanel();
                ivjPass1Panel.setName("Pass1Panel");
                ivjPass1Panel.setLayout(null);
                ivjPass1Panel.setBackground(java.awt.SystemColor.controlShadow);
                ivjPass1Panel.setBounds(30, 30, 50, 50);
                // user code begin {1}
                // user code end
            } catch (java.lang.Throwable ivjExc) {
                // user code begin {2}
                // user code end
                handleException(ivjExc);
            }
        }
        return ivjPass1Panel;
    }


    /** Machine-generated. */
    private javax.swing.JButton getPass2Button() {
        if (ivjPass2Button == null) {
            try {
                ivjPass2Button = new javax.swing.JButton();
                ivjPass2Button.setName("Pass2Button");
                ivjPass2Button.setText("Pass 2: Verify static .class file constraints");
                ivjPass2Button.setBackground(java.awt.SystemColor.controlHighlight);
                ivjPass2Button.setBounds(100, 100, 300, 30);
                ivjPass2Button.setActionCommand("Button2");
                // user code begin {1}
                // user code end
            } catch (java.lang.Throwable ivjExc) {
                // user code begin {2}
                // user code end
                handleException(ivjExc);
            }
        }
        return ivjPass2Button;
    }


    /** Machine-generated. */
    private javax.swing.JPanel getPass2Panel() {
        if (ivjPass2Panel == null) {
            try {
                ivjPass2Panel = new javax.swing.JPanel();
                ivjPass2Panel.setName("Pass2Panel");
                ivjPass2Panel.setLayout(null);
                ivjPass2Panel.setBackground(java.awt.SystemColor.controlShadow);
                ivjPass2Panel.setBounds(30, 90, 50, 50);
                // user code begin {1}
                // user code end
            } catch (java.lang.Throwable ivjExc) {
                // user code begin {2}
                // user code end
                handleException(ivjExc);
            }
        }
        return ivjPass2Panel;
    }


    /** Machine-generated. */
    private javax.swing.JButton getPass3Button() {
        if (ivjPass3Button == null) {
            try {
                ivjPass3Button = new javax.swing.JButton();
                ivjPass3Button.setName("Pass3Button");
                ivjPass3Button.setText("Passes 3a+3b: Verify code arrays");
                ivjPass3Button.setBackground(java.awt.SystemColor.controlHighlight);
                ivjPass3Button.setBounds(100, 160, 300, 30);
                ivjPass3Button.setActionCommand("Button2");
                // user code begin {1}
                // user code end
            } catch (java.lang.Throwable ivjExc) {
                // user code begin {2}
                // user code end
                handleException(ivjExc);
            }
        }
        return ivjPass3Button;
    }


    /** Machine-generated. */
    private javax.swing.JPanel getPass3Panel() {
        if (ivjPass3Panel == null) {
            try {
                ivjPass3Panel = new javax.swing.JPanel();
                ivjPass3Panel.setName("Pass3Panel");
                ivjPass3Panel.setLayout(null);
                ivjPass3Panel.setBackground(java.awt.SystemColor.controlShadow);
                ivjPass3Panel.setBounds(30, 150, 50, 50);
                // user code begin {1}
                // user code end
            } catch (java.lang.Throwable ivjExc) {
                // user code begin {2}
                // user code end
                handleException(ivjExc);
            }
        }
        return ivjPass3Panel;
    }


    /** Machine-generated. */
    private void handleException( java.lang.Throwable exception ) {
        /* Uncomment the following lines to print uncaught exceptions to stdout */
        System.out.println("--------- UNCAUGHT EXCEPTION ---------");
        exception.printStackTrace(System.out);
    }


    /** Machine-generated. */
    private void initConnections() throws java.lang.Exception {
        // user code begin {1}
        // user code end
        getPass1Button().addActionListener(ivjEventHandler);
        getPass2Button().addActionListener(ivjEventHandler);
        getPass3Button().addActionListener(ivjEventHandler);
        getFlushButton().addActionListener(ivjEventHandler);
    }


    /** Machine-generated. */
    private void initialize() {
        try {
            // user code begin {1}
            // user code end
            setName("VerifyDialog");
            setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
            setSize(430, 280);
            setVisible(true);
            setModal(true);
            setResizable(false);
            setContentPane(getJDialogContentPane());
            initConnections();
        } catch (java.lang.Throwable ivjExc) {
            handleException(ivjExc);
        }
        // user code begin {2}
        setTitle("'" + class_name + "' verification - JustIce / BCEL");
        // user code end
    }


    /**
     * Verifies one or more class files.
     * Verification results are presented graphically: Red means 'rejected',
     * green means 'passed' while yellow means 'could not be verified yet'.
     * @param args java.lang.String[] fully qualified names of classes to verify.
     */
    public static void main( java.lang.String[] args ) {
        classes_to_verify = args.length;
        for (int i = 0; i < args.length; i++) {
            try {
                VerifyDialog aVerifyDialog;
                aVerifyDialog = new VerifyDialog(args[i]);
                aVerifyDialog.setModal(true);
                aVerifyDialog.addWindowListener(new java.awt.event.WindowAdapter() {

                    public void windowClosing( java.awt.event.WindowEvent e ) {
                        classes_to_verify--;
                        if (classes_to_verify == 0) {
                            System.exit(0);
                        }
                    };
                });
                aVerifyDialog.setVisible(true);
            } catch (Throwable exception) {
                System.err.println("Exception occurred in main() of javax.swing.JDialog");
                exception.printStackTrace(System.out);
            }
        }
    }


    /** Machine-generated. */
    public void pass1Button_ActionPerformed( java.awt.event.ActionEvent actionEvent ) {
        Verifier v = VerifierFactory.getVerifier(class_name);
        VerificationResult vr = v.doPass1();
        if (vr.getStatus() == VerificationResult.VERIFIED_OK) {
            getPass1Panel().setBackground(Color.green);
            getPass1Panel().repaint();
        }
        if (vr.getStatus() == VerificationResult.VERIFIED_REJECTED) {
            getPass1Panel().setBackground(Color.red);
            getPass1Panel().repaint();
        }
    }


    /** Machine-generated. */
    public void pass2Button_ActionPerformed( java.awt.event.ActionEvent actionEvent ) {
        pass1Button_ActionPerformed(actionEvent);
        Verifier v = VerifierFactory.getVerifier(class_name);
        VerificationResult vr = v.doPass2();
        if (vr.getStatus() == VerificationResult.VERIFIED_OK) {
            getPass2Panel().setBackground(Color.green);
            getPass2Panel().repaint();
        }
        if (vr.getStatus() == VerificationResult.VERIFIED_NOTYET) {
            getPass2Panel().setBackground(Color.yellow);
            getPass2Panel().repaint();
        }
        if (vr.getStatus() == VerificationResult.VERIFIED_REJECTED) {
            getPass2Panel().setBackground(Color.red);
            getPass2Panel().repaint();
        }
    }


    /** Machine-generated. */
    public void pass4Button_ActionPerformed( java.awt.event.ActionEvent actionEvent ) {
        pass2Button_ActionPerformed(actionEvent);
        Color color = Color.green;
        Verifier v = VerifierFactory.getVerifier(class_name);
        VerificationResult vr = v.doPass2();
        if (vr.getStatus() == VerificationResult.VERIFIED_OK) {
            JavaClass jc = null;
            try {
                jc = Repository.lookupClass(class_name);
                int nr = jc.getMethods().length;
                for (int i = 0; i < nr; i++) {
                    vr = v.doPass3b(i);
                    if (vr.getStatus() != VerificationResult.VERIFIED_OK) {
                        color = Color.red;
                        break;
                    }
                }
            } catch (ClassNotFoundException ex) {
                // FIXME: report the error
                ex.printStackTrace();
            }
        } else {
            color = Color.yellow;
        }
        getPass3Panel().setBackground(color);
        getPass3Panel().repaint();
    }
}
