/*
 * Created on Nov 2, 2004
 */
package com.googlecode.jlaunch;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;

/**
 * @author Matt Hicks
 */
public class JException extends JDialog implements ActionListener {
	private static final long serialVersionUID = 2163614276085397434L;

	private Throwable t;
    private JTextPane generic;
    
    protected JException(Frame frame, Throwable t) {
        super(frame);
        this.t = t;
        setSize(300, 150);
        setResizable(false);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		Dimension windowSize = getSize();
		double centerWidth = screenSize.width / 2;
		double centerHeight = screenSize.height / 2;
		if (frame != null) {
		    setModal(true);
			Point frameLocation = frame.getLocation();
			Dimension frameSize = frame.getSize();
			if ((frameSize.height > 0) && (frameSize.width > 0)) {
				centerWidth = frameLocation.getX() + (frameSize.width / 2);
				centerHeight = frameLocation.getY() + (frameSize.height / 2);
			}
		}
		setLocation((int)centerWidth - (windowSize.width / 2), (int)centerHeight - (windowSize.height / 2));
        String title = t.getClass().getName().substring(t.getClass().getName().lastIndexOf(".") + 1);
        //setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle(title);
        getContentPane().setLayout(new BorderLayout());
        
        JLabel label = new JLabel("An error has occurred:");
        label.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 2));
        getContentPane().add(BorderLayout.NORTH, label);
        
        generic = new JTextPane();
        generic.setContentType("text/html");
        generic.setText("<span style=\"font-family: arial,sans-serif; font-size: small;\">" + t.getClass().getName() + " (" + t.getMessage() + ")</span>");
        generic.setBorder(BorderFactory.createEmptyBorder(0, 10, 5, 10));
        generic.setEditable(false);
        JScrollPane genericScroller = new JScrollPane(generic);
        genericScroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        getContentPane().add(BorderLayout.CENTER, genericScroller);
        
        JPanel bottomBar = new JPanel();
        bottomBar.setLayout(new BorderLayout());
        JPanel buttons = new JPanel();
        buttons.setLayout(new GridLayout(1, 2));
        JButton details = new JButton("Details");
        details.addActionListener(this);
        JButton close = new JButton("Close");
        close.addActionListener(this);
        buttons.add(details);
        buttons.add(close);
        bottomBar.add(BorderLayout.EAST, buttons);
        getContentPane().add(BorderLayout.SOUTH, bottomBar);
        
        setVisible(true);
    }
    
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof JButton) {
            JButton button = (JButton)e.getSource();
            if (button.getText().equals("Details")) {
                setSize(450, 250);
                Point position = getLocation();
                setLocation(position.x - 75, position.y - 25);
                //generic.setContentType("text/html");
                generic.setText(getHTMLStackTrace(t));
                validate();
                repaint();
                button.setText("Simple");
            } else if (button.getText().equals("Simple")) {
                setSize(300, 150);
                Point position = getLocation();
                setLocation(position.x + 75, position.y + 25);
                generic.setText("<span style=\"font-family: arial,sans-serif; font-size: small;\">" + t.getClass().getName() + " (" + t.getMessage() + ")</span>");
                validate();
                repaint();
                button.setText("Details");
            } else if (button.getText().equals("Close")) {
                setVisible(false);
                dispose();
            }
        }
    }
        
    public static void showException(Component c, Throwable t) {
        Frame frame = null;
        if (c != null) {
            frame = JOptionPane.getFrameForComponent(c);
        }
        new JException(frame, t);
    }
    
    public static String getHTMLStackTrace(Throwable e) {
        return getHTMLStackTrace(e, false);
    }
    
    public static String getHTMLStackTrace(Throwable e, boolean causedBy) {
        StringBuffer buffer = new StringBuffer();
        if (causedBy) buffer.append("&#160;&#160;&#160;&#160;");
        buffer.append("<span style=\"font-family: arial,sans-serif; font-size: small;\"><b><font color=\"#dd1111\">" + e.getClass().getName() + " (" + e.getMessage() + ")<br/><br/>Trace Follows:</font></b><br>");
        StackTraceElement[] trace = e.getStackTrace();
        String source;
        for (int i = 0; i < trace.length; i++) {
            if (trace[i].getFileName() != null) {
                source = "<b>" + trace[i].getFileName() + ":" + trace[i].getLineNumber() + "</b>";
            } else {
                source = "<i>Unknown Source</i>";
            }
            if (causedBy) buffer.append("&#160;&#160;&#160;&#160;");
            buffer.append("&#160;&#160;&#160;&#160;" + trace[i].getClassName() + "." + trace[i].getMethodName() + "(" + source + ")<br>");
        }
        buffer.append("</span>");
        if (e.getCause() != null) {
            buffer.append("<span style=\"font-family: arial,sans-serif; font-size: small;\"><b>Caused by:</b><br>");
            buffer.append(getHTMLStackTrace(e.getCause(), true));
        }
        return buffer.toString();
    }
}
