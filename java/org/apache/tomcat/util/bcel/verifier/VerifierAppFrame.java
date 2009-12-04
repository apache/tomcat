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

import java.awt.AWTEvent;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import org.apache.tomcat.util.bcel.Repository;
import org.apache.tomcat.util.bcel.classfile.JavaClass;

/**
 * This class implements a machine-generated frame for use with
 * the GraphicalVerfifier.
 *
 * @version $Id$
 * @author Enver Haase
 * @see GraphicalVerifier
 */
public class VerifierAppFrame extends JFrame {

    JPanel contentPane;
    JSplitPane jSplitPane1 = new JSplitPane();
    JPanel jPanel1 = new JPanel();
    JPanel jPanel2 = new JPanel();
    JSplitPane jSplitPane2 = new JSplitPane();
    JPanel jPanel3 = new JPanel();
    JList classNamesJList = new JList();
    GridLayout gridLayout1 = new GridLayout();
    JPanel messagesPanel = new JPanel();
    GridLayout gridLayout2 = new GridLayout();
    JMenuBar jMenuBar1 = new JMenuBar();
    JMenu jMenu1 = new JMenu();
    JScrollPane jScrollPane1 = new JScrollPane();
    JScrollPane messagesScrollPane = new JScrollPane();
    JScrollPane jScrollPane3 = new JScrollPane();
    GridLayout gridLayout4 = new GridLayout();
    JScrollPane jScrollPane4 = new JScrollPane();
    CardLayout cardLayout1 = new CardLayout();
    private String JUSTICE_VERSION = "JustIce by Enver Haase";
    private String current_class;
    GridLayout gridLayout3 = new GridLayout();
    JTextPane pass1TextPane = new JTextPane();
    JTextPane pass2TextPane = new JTextPane();
    JTextPane messagesTextPane = new JTextPane();
    JMenuItem newFileMenuItem = new JMenuItem();
    JSplitPane jSplitPane3 = new JSplitPane();
    JSplitPane jSplitPane4 = new JSplitPane();
    JScrollPane jScrollPane2 = new JScrollPane();
    JScrollPane jScrollPane5 = new JScrollPane();
    JScrollPane jScrollPane6 = new JScrollPane();
    JScrollPane jScrollPane7 = new JScrollPane();
    JList pass3aJList = new JList();
    JList pass3bJList = new JList();
    JTextPane pass3aTextPane = new JTextPane();
    JTextPane pass3bTextPane = new JTextPane();
    JMenu jMenu2 = new JMenu();
    JMenuItem whatisMenuItem = new JMenuItem();
    JMenuItem aboutMenuItem = new JMenuItem();


    /** Constructor. */
    public VerifierAppFrame() {
        enableEvents(AWTEvent.WINDOW_EVENT_MASK);
        try {
            jbInit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /** Initizalization of the components. */
    private void jbInit() throws Exception {
        //setIconImage(Toolkit.getDefaultToolkit().createImage(Frame1.class.getResource("[Ihr Symbol]")));
        contentPane = (JPanel) this.getContentPane();
        contentPane.setLayout(cardLayout1);
        this.setJMenuBar(jMenuBar1);
        this.setSize(new Dimension(708, 451));
        this.setTitle("JustIce");
        jPanel1.setMinimumSize(new Dimension(100, 100));
        jPanel1.setPreferredSize(new Dimension(100, 100));
        jPanel1.setLayout(gridLayout1);
        jSplitPane2.setOrientation(JSplitPane.VERTICAL_SPLIT);
        jPanel2.setLayout(gridLayout2);
        jPanel3.setMinimumSize(new Dimension(200, 100));
        jPanel3.setPreferredSize(new Dimension(400, 400));
        jPanel3.setLayout(gridLayout4);
        messagesPanel.setMinimumSize(new Dimension(100, 100));
        messagesPanel.setLayout(gridLayout3);
        jPanel2.setMinimumSize(new Dimension(200, 100));
        jMenu1.setText("File");
        jScrollPane1.getViewport().setBackground(Color.red);
        messagesScrollPane.getViewport().setBackground(Color.red);
        messagesScrollPane.setPreferredSize(new Dimension(10, 10));
        classNamesJList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {

            public void valueChanged( ListSelectionEvent e ) {
                classNamesJList_valueChanged(e);
            }
        });
        classNamesJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jScrollPane3.setBorder(BorderFactory.createLineBorder(Color.black));
        jScrollPane3.setPreferredSize(new Dimension(100, 100));
        gridLayout4.setRows(4);
        gridLayout4.setColumns(1);
        gridLayout4.setHgap(1);
        jScrollPane4.setBorder(BorderFactory.createLineBorder(Color.black));
        jScrollPane4.setPreferredSize(new Dimension(100, 100));
        pass1TextPane.setBorder(BorderFactory.createRaisedBevelBorder());
        pass1TextPane.setToolTipText("");
        pass1TextPane.setEditable(false);
        pass2TextPane.setBorder(BorderFactory.createRaisedBevelBorder());
        pass2TextPane.setEditable(false);
        messagesTextPane.setBorder(BorderFactory.createRaisedBevelBorder());
        messagesTextPane.setEditable(false);
        newFileMenuItem.setText("New...");
        newFileMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(78,
                java.awt.event.KeyEvent.CTRL_MASK, true));
        newFileMenuItem.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed( ActionEvent e ) {
                newFileMenuItem_actionPerformed(e);
            }
        });
        pass3aTextPane.setEditable(false);
        pass3bTextPane.setEditable(false);
        pass3aJList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {

            public void valueChanged( ListSelectionEvent e ) {
                pass3aJList_valueChanged(e);
            }
        });
        pass3bJList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {

            public void valueChanged( ListSelectionEvent e ) {
                pass3bJList_valueChanged(e);
            }
        });
        jMenu2.setText("Help");
        whatisMenuItem.setText("What is...");
        whatisMenuItem.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed( ActionEvent e ) {
                whatisMenuItem_actionPerformed(e);
            }
        });
        aboutMenuItem.setText("About");
        aboutMenuItem.addActionListener(new java.awt.event.ActionListener() {

            public void actionPerformed( ActionEvent e ) {
                aboutMenuItem_actionPerformed(e);
            }
        });
        jSplitPane2.add(messagesPanel, JSplitPane.BOTTOM);
        messagesPanel.add(messagesScrollPane, null);
        messagesScrollPane.getViewport().add(messagesTextPane, null);
        jSplitPane2.add(jPanel3, JSplitPane.TOP);
        jPanel3.add(jScrollPane3, null);
        jScrollPane3.getViewport().add(pass1TextPane, null);
        jPanel3.add(jScrollPane4, null);
        jPanel3.add(jSplitPane3, null);
        jSplitPane3.add(jScrollPane2, JSplitPane.LEFT);
        jScrollPane2.getViewport().add(pass3aJList, null);
        jSplitPane3.add(jScrollPane5, JSplitPane.RIGHT);
        jScrollPane5.getViewport().add(pass3aTextPane, null);
        jPanel3.add(jSplitPane4, null);
        jSplitPane4.add(jScrollPane6, JSplitPane.LEFT);
        jScrollPane6.getViewport().add(pass3bJList, null);
        jSplitPane4.add(jScrollPane7, JSplitPane.RIGHT);
        jScrollPane7.getViewport().add(pass3bTextPane, null);
        jScrollPane4.getViewport().add(pass2TextPane, null);
        jSplitPane1.add(jPanel2, JSplitPane.TOP);
        jPanel2.add(jScrollPane1, null);
        jSplitPane1.add(jPanel1, JSplitPane.BOTTOM);
        jPanel1.add(jSplitPane2, null);
        jScrollPane1.getViewport().add(classNamesJList, null);
        jMenuBar1.add(jMenu1);
        jMenuBar1.add(jMenu2);
        contentPane.add(jSplitPane1, "jSplitPane1");
        jMenu1.add(newFileMenuItem);
        jMenu2.add(whatisMenuItem);
        jMenu2.add(aboutMenuItem);
        jSplitPane2.setDividerLocation(300);
        jSplitPane3.setDividerLocation(150);
        jSplitPane4.setDividerLocation(150);
    }


    /** Overridden to stop the application on a closing window. */
    protected void processWindowEvent( WindowEvent e ) {
        super.processWindowEvent(e);
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            System.exit(0);
        }
    }


    synchronized void classNamesJList_valueChanged( ListSelectionEvent e ) {
        if (e.getValueIsAdjusting()) {
            return;
        }
        current_class = classNamesJList.getSelectedValue().toString();
        try {
            verify();
        } catch (ClassNotFoundException ex) {
            // FIXME: report the error using the GUI
            ex.printStackTrace();
        }
        classNamesJList.setSelectedValue(current_class, true);
    }


    private void verify() throws ClassNotFoundException {
        setTitle("PLEASE WAIT");
        Verifier v = VerifierFactory.getVerifier(current_class);
        v.flush(); // Don't cache the verification result for this class.
        VerificationResult vr;
        vr = v.doPass1();
        if (vr.getStatus() == VerificationResult.VERIFIED_REJECTED) {
            pass1TextPane.setText(vr.getMessage());
            pass1TextPane.setBackground(Color.red);
            pass2TextPane.setText("");
            pass2TextPane.setBackground(Color.yellow);
            pass3aTextPane.setText("");
            pass3aJList.setListData(new Object[0]);
            pass3aTextPane.setBackground(Color.yellow);
            pass3bTextPane.setText("");
            pass3bJList.setListData(new Object[0]);
            pass3bTextPane.setBackground(Color.yellow);
        } else { // Must be VERIFIED_OK, Pass 1 does not know VERIFIED_NOTYET
            pass1TextPane.setBackground(Color.green);
            pass1TextPane.setText(vr.getMessage());
            vr = v.doPass2();
            if (vr.getStatus() == VerificationResult.VERIFIED_REJECTED) {
                pass2TextPane.setText(vr.getMessage());
                pass2TextPane.setBackground(Color.red);
                pass3aTextPane.setText("");
                pass3aTextPane.setBackground(Color.yellow);
                pass3aJList.setListData(new Object[0]);
                pass3bTextPane.setText("");
                pass3bTextPane.setBackground(Color.yellow);
                pass3bJList.setListData(new Object[0]);
            } else { // must be Verified_OK, because Pass1 was OK (cannot be Verified_NOTYET).
                pass2TextPane.setText(vr.getMessage());
                pass2TextPane.setBackground(Color.green);
                JavaClass jc = Repository.lookupClass(current_class);
                /*
                 boolean all3aok = true;
                 boolean all3bok = true;
                 String all3amsg = "";
                 String all3bmsg = "";
                 */
                String[] methodnames = new String[jc.getMethods().length];
                for (int i = 0; i < jc.getMethods().length; i++) {
                    methodnames[i] = jc.getMethods()[i].toString().replace('\n', ' ').replace('\t',
                            ' ');
                }
                pass3aJList.setListData(methodnames);
                pass3aJList.setSelectionInterval(0, jc.getMethods().length - 1);
                pass3bJList.setListData(methodnames);
                pass3bJList.setSelectionInterval(0, jc.getMethods().length - 1);
            }
        }
        String[] msgs = v.getMessages();
        messagesTextPane.setBackground(msgs.length == 0 ? Color.green : Color.yellow);
        String allmsgs = "";
        for (int i = 0; i < msgs.length; i++) {
            msgs[i] = msgs[i].replace('\n', ' ');
            allmsgs += msgs[i] + "\n\n";
        }
        messagesTextPane.setText(allmsgs);
        setTitle(current_class + " - " + JUSTICE_VERSION);
    }


    void newFileMenuItem_actionPerformed( ActionEvent e ) {
        String classname = JOptionPane
                .showInputDialog("Please enter the fully qualified name of a class or interface to verify:");
        if ((classname == null) || (classname.equals(""))) {
            return;
        }
        VerifierFactory.getVerifier(classname); // let observers do the rest.
        classNamesJList.setSelectedValue(classname, true);
    }


    synchronized void pass3aJList_valueChanged( ListSelectionEvent e ) {
        if (e.getValueIsAdjusting()) {
            return;
        }
        Verifier v = VerifierFactory.getVerifier(current_class);
        String all3amsg = "";
        boolean all3aok = true;
        boolean rejected = false;
        for (int i = 0; i < pass3aJList.getModel().getSize(); i++) {
            if (pass3aJList.isSelectedIndex(i)) {
                VerificationResult vr = v.doPass3a(i);
                if (vr.getStatus() == VerificationResult.VERIFIED_REJECTED) {
                    all3aok = false;
                    rejected = true;
                }
                JavaClass jc = null;
                try {
                    jc = Repository.lookupClass(v.getClassName());
                    all3amsg += "Method '" + jc.getMethods()[i] + "': "
                            + vr.getMessage().replace('\n', ' ') + "\n\n";
                } catch (ClassNotFoundException ex) {
                    // FIXME: handle the error
                    ex.printStackTrace();
                }
            }
        }
        pass3aTextPane.setText(all3amsg);
        pass3aTextPane.setBackground(all3aok ? Color.green : (rejected ? Color.red : Color.yellow));
    }


    synchronized void pass3bJList_valueChanged( ListSelectionEvent e ) {
        if (e.getValueIsAdjusting()) {
            return;
        }
        Verifier v = VerifierFactory.getVerifier(current_class);
        String all3bmsg = "";
        boolean all3bok = true;
        boolean rejected = false;
        for (int i = 0; i < pass3bJList.getModel().getSize(); i++) {
            if (pass3bJList.isSelectedIndex(i)) {
                VerificationResult vr = v.doPass3b(i);
                if (vr.getStatus() == VerificationResult.VERIFIED_REJECTED) {
                    all3bok = false;
                    rejected = true;
                }
                JavaClass jc = null;
                try {
                    jc = Repository.lookupClass(v.getClassName());
                    all3bmsg += "Method '" + jc.getMethods()[i] + "': "
                            + vr.getMessage().replace('\n', ' ') + "\n\n";
                } catch (ClassNotFoundException ex) {
                    // FIXME: handle the error
                    ex.printStackTrace();
                }
            }
        }
        pass3bTextPane.setText(all3bmsg);
        pass3bTextPane.setBackground(all3bok ? Color.green : (rejected ? Color.red : Color.yellow));
    }


    void aboutMenuItem_actionPerformed( ActionEvent e ) {
        JOptionPane
                .showMessageDialog(
                        this,
                        "JustIce is a Java class file verifier.\nIt was implemented by Enver Haase in 2001, 2002.\n<http://jakarta.apache.org/bcel/index.html>",
                        JUSTICE_VERSION, JOptionPane.INFORMATION_MESSAGE);
    }


    void whatisMenuItem_actionPerformed( ActionEvent e ) {
        JOptionPane
                .showMessageDialog(
                        this,
                        "The upper four boxes to the right reflect verification passes according to The Java Virtual Machine Specification.\nThese are (in that order): Pass one, Pass two, Pass three (before data flow analysis), Pass three (data flow analysis).\nThe bottom box to the right shows (warning) messages; warnings do not cause a class to be rejected.",
                        JUSTICE_VERSION, JOptionPane.INFORMATION_MESSAGE);
    }
}
