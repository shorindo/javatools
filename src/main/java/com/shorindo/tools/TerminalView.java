package com.shorindo.tools;

import java.awt.AWTEvent;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Map;

public class TerminalView extends Terminal {
	private static final Logger LOG = Logger.getLogger(TerminalView.class);
	private static TerminalView terminal;
	private Canvas canvas;
	private Dimension bbox = new Dimension();

	public static void main(String[] args) {
		terminal = new TerminalView("UTF-8", 80, 25);
		terminal.start();
		LOG.info("start");
	}

	public TerminalView(String charset, int cols, int rows) {
		super(charset, cols, rows);
	}

	public void start() {
		final Frame frame = new Frame();
		canvas = new Canvas() {
			Graphics imageBuffer;
			int width;
			int height;
			Image back;

			@Override
			public void paint(Graphics g) {
				if (imageBuffer == null) {
					Dimension size = getSize();
					back = createImage(size.width, size.height);
					imageBuffer = back.getGraphics();
					imageBuffer.setFont(new Font("ＭＳ ゴシック", Font.PLAIN, 24));
					FontMetrics fm = imageBuffer.getFontMetrics();
					width = fm.charWidth('あ') / 2;
					height = fm.getAscent();
					bbox = new Dimension(width, height);
				}
				try {
					imageBuffer.clearRect(0, 0, 800, 600);
					char[][] textBuffer = terminal.getBuffer();
					for (int i = 0; i < textBuffer.length; i++) {
						char[] line = textBuffer[i];
						for (int j = 0; j < line.length; j++) {
							char ch = line[j];
							if (ch > 0) {
								//LOG.info("ch=" + ch);
								show(String.valueOf(ch), i, j);
							}
						}
					}
					getGraphics().drawImage(back, 0, 0, this);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			public void show(String s, int row, int col) {
				int offset = width * col;
				for (int i = 0; i < s.length(); i++) {
					char ch = s.charAt(i);
					imageBuffer.drawString(String.valueOf(ch), offset, height * (row + 1));
					if (ch < 0xff) 
						offset += width;
					else
						offset += width * 2;
				}
			}
		};
		canvas.requestFocus();
		canvas.addKeyListener(new KeyListener() {
			@Override
			public void keyTyped(KeyEvent e) {
				terminal.put(e.getKeyChar());
				canvas.repaint();
			}

			@Override
			public void keyPressed(KeyEvent e) {
			}

			@Override
			public void keyReleased(KeyEvent e) {
			}
		});
		canvas.addComponentListener(new ComponentListener() {
			@Override
			public void componentResized(ComponentEvent e) {
				LOG.info("componentResized(" + e.getComponent().getWidth() +
						"," + e.getComponent().getHeight() + ")");
				//canvas.setPreferredSize(new Dimension(getCols() * width, getRows() * height));
			}
			
			@Override
			public void componentMoved(ComponentEvent e) {
				//LOG.info("componentMoved");
			}

			@Override
			public void componentShown(ComponentEvent e) {
				LOG.info("componentShown");
				//canvas.setPreferredSize(new Dimension(getCols() * width, getRows() * height));
			}

			@Override
			public void componentHidden(ComponentEvent e) {
				LOG.info("componentHidden");
			}
		});
		frame.add(canvas);
		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
		        System.exit(0);
		    }
		});
		frame.setVisible(true);

		canvas.setFont(new Font("ＭＳ ゴシック", Font.PLAIN, 24));
		FontMetrics fm = canvas.getGraphics().getFontMetrics();
		int width = fm.charWidth('あ') / 2;
		int height = fm.getAscent();
		bbox = new Dimension(width, height);
		canvas.setSize(
				(int)bbox.getWidth() * terminal.getCols(),
				(int)bbox.getHeight() * terminal.getRows());
		frame.pack();
	}
	
}
