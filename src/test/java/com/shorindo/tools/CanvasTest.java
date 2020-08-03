package com.shorindo.tools;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.TextArea;
import java.awt.TextComponent;
import java.awt.TextField;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CanvasTest extends WindowAdapter {
	private static final Logger LOG = Logger.getLogger(CanvasTest.class);
	public static void main(String[] args) {
		new CanvasTest().start();
	}
	
	public void start() {
		Frame frame = new Frame();
		Canvas canvas = new Canvas() {
			Map<Character,Integer> widths = new HashMap<>();
			int width;
			int height;
			Image back;
			Graphics buffer;

			@Override
			public void paint(Graphics g) {
				if (buffer == null) {
					Dimension size = getSize();
					back = createImage(size.width, size.height);
					buffer = back.getGraphics();
					//buffer.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 24));
					buffer.setFont(new Font("ＭＳ ゴシック", Font.PLAIN, 24));
					FontMetrics fm = buffer.getFontMetrics();
					width = fm.charWidth('あ') / 2;
					height = fm.getAscent();
				}
				try {
					put("1234567890", 0, 0);
					put("abcdeWBCDE", 1, 0);
					put("あいうえお", 2, 0);
					put("漢字はKanjiです", 3, 0);
					put(new String(new byte[] { 0x1b, '[', 'H' }), 4, 0);

					getGraphics().drawImage(back, 0, 0, this);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			public void put(String s, int row, int col) {
				int offset = width * col;
				for (int i = 0; i < s.length(); i++) {
					char ch = s.charAt(i);
					buffer.drawString(String.valueOf(ch), offset, height * (row + 1));
					if (ch < 0xff) 
						offset += width;
					else
						offset += width * 2;
				}
			}

			public void put(char ch, int row, int col) {
				Graphics g = this.getGraphics();
				g.drawString(String.valueOf(ch), width * col, height * row);
			}
		};
		canvas.setSize(800, 600);
		canvas.addKeyListener(new KeyListener() {

			@Override
			public void keyTyped(KeyEvent e) {
				LOG.info("keyTyped()");
			}

			@Override
			public void keyPressed(KeyEvent e) {
				//LOG.info("keyPressed()");
			}

			@Override
			public void keyReleased(KeyEvent e) {
				//LOG.info("keyReleased()");
			}
			
		});
		canvas.addInputMethodListener(new InputMethodListener() {

			@Override
			public void inputMethodTextChanged(InputMethodEvent event) {
				LOG.info("inputMethodTextChanged()");
			}

			@Override
			public void caretPositionChanged(InputMethodEvent event) {
				LOG.info("caretPositionChanged()");
			}
			
		});
		canvas.requestFocus();
		canvas.enableInputMethods(true);
		frame.addWindowListener(this);
		frame.add(canvas);
		frame.pack();
		frame.setVisible(true);
	}
	
	public void windowClosing(WindowEvent e) {
        System.exit(0);
    }
}
