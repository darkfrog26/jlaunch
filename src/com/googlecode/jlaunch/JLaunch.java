package com.googlecode.jlaunch;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;

import javax.swing.JApplet;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class JLaunch extends JApplet {
	private static final long serialVersionUID = 8339490394369972288L;

	private JPanel panel;
	private JProgressBar progress;
	private Loader loader;
	
	public String propertiesURL;
	
	public final void init() {
		propertiesURL = getParameter("propertiesURL");
		try {
			UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
		} catch(Throwable t) {
		}
	}
	
	public final void start() {
		panel = new JPanel();
		panel.setLayout(null);
		panel.setBackground(Color.BLACK);
		
		progress = new JProgressBar(0, 100);
		progress.setFont(progress.getFont().deriveFont(Font.BOLD));
		progress.setString("Loading Configuration");
		progress.setStringPainted(true);
		progress.setIndeterminate(true);
		updateLocation();
		panel.add(progress);
		
		setLayout(new BorderLayout());
		add(panel, BorderLayout.CENTER);
		
		addComponentListener(new ComponentListener() {
			public void componentShown(ComponentEvent e) {
			}
			
			public void componentResized(ComponentEvent e) {
				updateLocation();
			}
			
			public void componentMoved(ComponentEvent e) {
			}
			
			public void componentHidden(ComponentEvent e) {
			}
		});
		
		loader = new Loader(this);
		loader.start();
	}
	
	public void setCanvas(final Canvas canvas) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				canvas.setSize(panel.getSize());
				remove(panel);
				add(canvas, BorderLayout.CENTER);
				validate();
			}
		});
	}
	
	private void updateLocation() {
		Dimension size = getSize();
		Dimension progressBarSize = new Dimension(size.width / 2, Math.max(40, size.height / 10));
		progress.setSize(progressBarSize);
		progress.setLocation((size.width / 2) - (progressBarSize.width / 2), (size.height / 2) - (progressBarSize.height / 2));
	}
	
	public void setMaxProgress(final int max) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				progress.setIndeterminate(false);
				progress.setMaximum(max);
			}
		});
	}
	
	public void setProgress(final int value, final String text) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				progress.setString(text);
				progress.setValue(value);
			}
		});
	}
	
	public void setMessage(final String text) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				progress.setString(text);
			}
		});
	}
}