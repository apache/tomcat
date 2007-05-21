/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.tribes.demos;

import java.io.Serializable;
import java.util.Map;

import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelListener;
import org.apache.catalina.tribes.ManagedChannel;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipListener;
import org.apache.catalina.tribes.tipis.AbstractReplicatedMap;
import org.apache.catalina.tribes.tipis.LazyReplicatedMap;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import javax.swing.table.TableColumn;
import org.apache.catalina.tribes.util.UUIDGenerator;
import org.apache.catalina.tribes.util.Arrays;
import java.util.Set;

/**
 * <p>Title: </p>
 *
 * <p>Description: </p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
public class MapDemo implements ChannelListener, MembershipListener{
    
    protected LazyReplicatedMap map;
    protected SimpleTableDemo table;
    
    public MapDemo(Channel channel, String mapName ) {
        map = new LazyReplicatedMap(null,channel,5000, mapName,null);
        table = SimpleTableDemo.createAndShowGUI(map,channel.getLocalMember(false).getName());
        channel.addChannelListener(this);
        channel.addMembershipListener(this);
//        for ( int i=0; i<1000; i++ ) {
//            map.put("MyKey-"+i,"My String Value-"+i);
//        }
        this.messageReceived(null,null);
    }
    
    public boolean accept(Serializable msg, Member source) {
        table.dataModel.getValueAt(-1,-1);
        return false;
    }
    
    public void messageReceived(Serializable msg, Member source) {
        
    }
    
    public void memberAdded(Member member) {
    }
    public void memberDisappeared(Member member) {
        table.dataModel.getValueAt(-1,-1);
    }
    
    public static void usage() {
        System.out.println("Tribes MapDemo.");
        System.out.println("Usage:\n\t" + 
                           "java MapDemo [channel options] mapName\n\t" +
                           "\tChannel options:" +
                           ChannelCreator.usage());
    }

    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        ManagedChannel channel = (ManagedChannel) ChannelCreator.createChannel(args);
        String mapName = "MapDemo";
        if ( args.length > 0 && (!args[args.length-1].startsWith("-"))) {
            mapName = args[args.length-1];
        }
        channel.start(channel.DEFAULT);
        Runtime.getRuntime().addShutdownHook(new Shutdown(channel));
        MapDemo demo = new MapDemo(channel,mapName);
        
        System.out.println("System test complete, time to start="+(System.currentTimeMillis()-start)+" ms. Sleeping to let threads finish.");
        Thread.sleep(60 * 1000 * 60);
    }

    public static class Shutdown
        extends Thread {
        ManagedChannel channel = null;
        public Shutdown(ManagedChannel channel) {
            this.channel = channel;
        }

        public void run() {
            System.out.println("Shutting down...");
            SystemExit exit = new SystemExit(5000);
            exit.setDaemon(true);
            exit.start();
            try {
                channel.stop(channel.DEFAULT);

            } catch (Exception x) {
                x.printStackTrace();
            }
            System.out.println("Channel stopped.");
        }
    }

    public static class SystemExit
        extends Thread {
        private long delay;
        public SystemExit(long delay) {
            this.delay = delay;
        }

        public void run() {
            try {
                Thread.sleep(delay);
            } catch (Exception x) {
                x.printStackTrace();
            }
            System.exit(0);

        }
    }

    public static class SimpleTableDemo
        extends JPanel implements ActionListener{
        private static int WIDTH = 550;
        
        private LazyReplicatedMap map;
        private boolean DEBUG = false;
        AbstractTableModel dataModel = new AbstractTableModel() {
            
            
            String[] columnNames = {
                                   "Key",
                                   "Value",
                                   "Backup Node",
                                   "isPrimary",
                                   "isProxy",
                                   "isBackup"};

            public int getColumnCount() { return columnNames.length; }
    
            public int getRowCount() {return map.sizeFull() +1; }
            
            public StringBuffer getMemberNames(Member[] members){
                StringBuffer buf = new StringBuffer();
                if ( members!=null ) {
                    for (int i=0;i<members.length; i++ ) {
                        buf.append(members[i].getName());
                        buf.append("; ");
                    }
                }
                return buf;
            }
            
            public Object getValueAt(int row, int col) {
                if ( row==-1 ) {
                    update();
                    return "";
                }
                if ( row == 0 ) return columnNames[col];
                Object[] keys = map.keySetFull().toArray();
                String key = (String)keys [row-1];
                LazyReplicatedMap.MapEntry entry = map.getInternal(key);
                switch (col) {
                    case 0: return entry.getKey();
                    case 1: return entry.getValue();
                    case 2: return getMemberNames(entry.getBackupNodes());
                    case 3: return new Boolean(entry.isPrimary());
                    case 4: return new Boolean(entry.isProxy());
                    case 5: return new Boolean(entry.isBackup());
                    default: return "";
                }
                
            }
            
            public void update() {
                fireTableDataChanged();
            }
        };
        
        JTextField txtAddKey = new JTextField(20);
        JTextField txtAddValue = new JTextField(20);
        JTextField txtRemoveKey = new JTextField(20);
        JTextField txtChangeKey = new JTextField(20);
        JTextField txtChangeValue = new JTextField(20);
        
        JTable table = null;
        public SimpleTableDemo(LazyReplicatedMap map) {
            super();
            this.map = map;
            
            this.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);

            //final JTable table = new JTable(data, columnNames);
            table = new JTable(dataModel);

            table.setPreferredScrollableViewportSize(new Dimension(WIDTH, 150));
            for ( int i=0; i<table.getColumnCount(); i++ ) {
                TableColumn tm = table.getColumnModel().getColumn(i);
                tm.setCellRenderer(new ColorRenderer());
            }


            if (DEBUG) {
                table.addMouseListener(new MouseAdapter() {
                    public void mouseClicked(MouseEvent e) {
                        printDebugData(table);
                    }
                });
            }
            
            //setLayout(new GridLayout(5, 0));
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

            //Create the scroll pane and add the table to it.
            JScrollPane scrollPane = new JScrollPane(table);

            //Add the scroll pane to this panel.
            add(scrollPane);
            
            //create a add value button
            JPanel addpanel = new JPanel();
            addpanel.setPreferredSize(new Dimension(WIDTH,30));
            addpanel.add(createButton("Add","add"));
            addpanel.add(txtAddKey);
            addpanel.add(txtAddValue);
            addpanel.setMaximumSize(new Dimension(WIDTH,30));
            add(addpanel);
            
            //create a remove value button
            JPanel removepanel = new JPanel( );
            removepanel.setPreferredSize(new Dimension(WIDTH,30));
            removepanel.add(createButton("Remove","remove"));
            removepanel.add(txtRemoveKey);
            removepanel.setMaximumSize(new Dimension(WIDTH,30));
            add(removepanel);

            //create a change value button
            JPanel changepanel = new JPanel( );
            changepanel.add(createButton("Change","change"));
            changepanel.add(txtChangeKey);
            changepanel.add(txtChangeValue);
            changepanel.setPreferredSize(new Dimension(WIDTH,30));
            changepanel.setMaximumSize(new Dimension(WIDTH,30));
            add(changepanel);


            //create sync button
            JPanel syncpanel = new JPanel( );
            syncpanel.add(createButton("Synchronize","sync"));
            syncpanel.add(createButton("Replicate","replicate"));
            syncpanel.add(createButton("Random","random"));
            syncpanel.setPreferredSize(new Dimension(WIDTH,30));
            syncpanel.setMaximumSize(new Dimension(WIDTH,30));
            add(syncpanel);


        }
        
        public JButton createButton(String text, String command) {
            JButton button = new JButton(text);
            button.setActionCommand(command);
            button.addActionListener(this);
            return button;
        }
        
        public void actionPerformed(ActionEvent e) {
            System.out.println(e.getActionCommand());
            if ( "add".equals(e.getActionCommand()) ) {
                System.out.println("Add key:"+txtAddKey.getText()+" value:"+txtAddValue.getText());
                map.put(txtAddKey.getText(),new StringBuffer(txtAddValue.getText()));
            }
            if ( "change".equals(e.getActionCommand()) ) {
                System.out.println("Change key:"+txtChangeKey.getText()+" value:"+txtChangeValue.getText());
                StringBuffer buf = (StringBuffer)map.get(txtChangeKey.getText());
                if ( buf!=null ) {
                    buf.delete(0,buf.length());
                    buf.append(txtChangeValue.getText());
                    map.replicate(txtChangeKey.getText(),true);
                } else {
                    buf = new StringBuffer();
                    buf.append(txtChangeValue.getText());
                    map.put(txtChangeKey.getText(),buf);
                }
            }
            if ( "remove".equals(e.getActionCommand()) ) {
                System.out.println("Remove key:"+txtRemoveKey.getText());
                map.remove(txtRemoveKey.getText());
            }
            if ( "sync".equals(e.getActionCommand()) ) {
                System.out.println("Syncing from another node.");
                map.transferState();
            }
            if ( "random".equals(e.getActionCommand()) ) {
                Thread t = new Thread() {
                    public void run() {
                        for (int i = 0; i < 100; i++) {
                            String key = Arrays.toString(UUIDGenerator.randomUUID(false));
                            map.put(key, new StringBuffer(key));
                            dataModel.fireTableDataChanged();
                            table.paint(table.getGraphics());
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException x) {
                                Thread.currentThread().interrupted();
                            }
                        }
                    }
                };
                t.start();
            }
            
            if ( "replicate".equals(e.getActionCommand()) ) {
                System.out.println("Replicating out to the other nodes.");
                map.replicate(true);
            }
            dataModel.getValueAt(-1,-1);
        }

        private void printDebugData(JTable table) {
            int numRows = table.getRowCount();
            int numCols = table.getColumnCount();
            javax.swing.table.TableModel model = table.getModel();

            System.out.println("Value of data: ");
            for (int i = 0; i < numRows; i++) {
                System.out.print("    row " + i + ":");
                for (int j = 0; j < numCols; j++) {
                    System.out.print("  " + model.getValueAt(i, j));
                }
                System.out.println();
            }
            System.out.println("--------------------------");
        }

        /**
         * Create the GUI and show it.  For thread safety,
         * this method should be invoked from the
         * event-dispatching thread.
         */
        public static SimpleTableDemo createAndShowGUI(LazyReplicatedMap map, String title) {
            //Make sure we have nice window decorations.
            JFrame.setDefaultLookAndFeelDecorated(true);

            //Create and set up the window.
            JFrame frame = new JFrame("SimpleTableDemo - "+title);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            //Create and set up the content pane.
            SimpleTableDemo newContentPane = new SimpleTableDemo(map);
            newContentPane.setOpaque(true); //content panes must be opaque
            frame.setContentPane(newContentPane);

            //Display the window.
            frame.setSize(450,250);
            newContentPane.setSize(450,300);
            frame.pack();
            frame.setVisible(true);
            return newContentPane;
        }
    }
    
    static class ColorRenderer extends DefaultTableCellRenderer {
        
        public ColorRenderer() {
            super();
        }

        public Component getTableCellRendererComponent
            (JTable table, Object value, boolean isSelected,
             boolean hasFocus, int row, int column) {
            Component cell = super.getTableCellRendererComponent
                             (table, value, isSelected, hasFocus, row, column);
            cell.setBackground(Color.WHITE);
            if ( row > 0 ) {
                Color color = null;
                boolean primary = ( (Boolean) table.getValueAt(row, 3)).booleanValue();
                boolean proxy = ( (Boolean) table.getValueAt(row, 4)).booleanValue();
                boolean backup = ( (Boolean) table.getValueAt(row, 5)).booleanValue();
                if (primary) color = Color.GREEN;
                else if (proxy) color = Color.RED;
                else if (backup) color = Color.BLUE;
                if ( color != null ) cell.setBackground(color);
            }
//            System.out.println("Row:"+row+" Column:"+column+" Color:"+cell.getBackground());
//            cell.setBackground(bkgndColor);
//            cell.setForeground(fgndColor);

            return cell;
        }
        
        
    }


}
