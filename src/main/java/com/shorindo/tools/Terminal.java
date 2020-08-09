package com.shorindo.tools;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.TextHitInfo;
import java.awt.im.InputContext;
import java.awt.im.InputMethodRequests;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.AttributedCharacterIterator;
import java.text.AttributedCharacterIterator.Attribute;
import java.util.ArrayList;
import java.util.List;

public class Terminal {
    private static final Logger LOG = Logger.getLogger(Terminal.class);
    private String charset;
    private InputStreamReader screenReader;
    private OutputStream keyboardOutput;
    private char[][] buffer;
    private int rows;
    private int cols;
    private int cr, cc;

    public Terminal(String charset, int cols, int rows) {
        this.rows = rows;
        this.cols = cols;
        this.buffer = new char[rows][cols];
        this.charset = charset;
        this.setIn(System.in);
        this.setOut(System.out);
    }
    
    public void start() {
        Thread th = new Thread() {
            @Override
            public void run() {
                int c;
                try {
                    while ((c = screenReader.read()) != -1) {
                        //LOG.debug("run(" + (char)c + ")");
                        switch (c) {
                        case 0x1b: push(c); state1(); break;
                        case '\b': cmd_dc(); break;
                        case '\r': cmd_cr(); break;
                        case '\n': cmd_do(); break;
                        case '\t': cmd_ta(); break; 
                        default: put((char)c);
                        }
                    }
                } catch (IOException e) {
                    LOG.error(e.getMessage(), e);
                } finally {
                    LOG.info("run() finished");
                }
            }
        };
        th.start();
        window();
    }

    /**
     * キーボードからのデータの出力先
     */
    public void setOut(OutputStream os) {
        keyboardOutput = os;
    }

    /**
     * スクリーンに書き込むデータの入力元
     */
    public void setIn(InputStream is) {
        try {
            screenReader = new InputStreamReader(is, charset);
        } catch (UnsupportedEncodingException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    protected char[][] getBuffer() {
        return buffer;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    /** FIXME 文字のカラムサイズを取得する */
    private int getWidth(char c) {
        return c < 0x100 ? 1 : 2;
    }

    /** １文字出力し、カーソルを文字幅分進める */
    public void put(char c) {
        //LOG.debug("put(" + c + ")");
        int w = getWidth(c);
        if (cc + w <= cols) {
            buffer[cr][cc] = c;
            cc += w;
        } else if (cr < rows - 1) {
            cr += 1;
            buffer[cr][0] = c;
            cc = w;
        } else {
            cmd_cs(1, cr);
            buffer[rows - 1] = new char[cols];
            cc = 0;
            put(c);
        }
        fireEvent(new TerminalEvent());
    }

    /** １行挿入 */
    protected void cmd_al() {
    }

    /** N行挿入 */
    protected void cmd_AL(int n) {
    }

    /** ベルを鳴らす */
    protected void cmd_bl() {
    }


    /** 画面の最後までクリア */
    protected void cmd_cd() {
    }

    /** 行の最後までクリア */
    protected void cmd_ce() {
    }

    /** 画面を消去し、カーソルをホームポジションへ */
    protected void cmd_cl() {
        LOG.debug("cmd_cl()");
        buffer = new char[rows][cols];
        cr = 0;
        cc = 0;
    }

    /** 画面上の %1 行、 %2 桁へカーソルを移動 */
    protected void cmd_cm(int row, int col) {
        this.cr = row - 1;
        this.cc = col - 1;
    }

    /** 復帰 */
    protected void cmd_cr() {
        this.cc = 0;
    }

    /** %1 行目から %2 行目までの範囲をスクロールする */
    protected void cmd_cs(int start, int end) {
        LOG.debug("cmd_cs(" + start + ", " + end + ")");
        if (start < 1) start = 1;
        if (end > rows) end = rows;
        for (int row = start - 1; row < end; row++) {
            if (row < rows - 1) {
                buffer[row] = buffer[row + 1];
            }
        }
    }

    /** タブの消去 */
    protected void cmd_ct() {
    }


    /** 一文字削除する */
    protected void cmd_dc() {
        LOG.debug("cmd_dc()");
        buffer[cr][cc] = 0;
        if (cc > 0) {
            cc -= 1;
        }
    }

    /** N文字削除する */
    protected void cmd_DC(int n) {
        LOG.debug("cmd_DC(" + n + ")");
        for (int i = 0; i < n; i++) {
        	cmd_dc();
        }
    }

    /** 一行削除する */
    protected void cmd_dl() {
        LOG.debug("cmd_dl()");
    }

    /** N行削除する */
    protected void cmd_DL(int n) {
        LOG.debug("cmd_DL(" + n + ")");
        for (int i = 0; i < n; i++) {
        	cmd_dl();
        }
    }

    /** カーソルを一行下げる */
    protected void cmd_do() {
        LOG.debug("cmd_do()");
        if (cr < rows - 1) {
            cr += 1;
        } else {
            cmd_cs(1, rows);
            buffer[rows - 1] = new char[cols];
        }
    }

    /** カーソルをN行下げる */
    protected void cmd_DO(int n) {
        LOG.debug("cmd_DO(" + n + ")");
        for (int i = 0; i < n; i++) {
        	cmd_do();
        }
    }

    /** intert モード終了 */
    protected void cmd_ei() {
    }

    /** カーソルをホームポジションに移動 */
    protected void cmd_ho() {
    }

    /** insert モード開始 */
    protected void cmd_im() {
    }

    /** バックスペースキー */
    protected void cmd_kb() {
    }

    /** kd   下カーソルキー */
    protected void cmd_kd() {
    }

    /** ke   キーパッドをオフにする */
    protected void cmd_ke() {
    }

    /** kh   home キー */
    protected void cmd_kh() {
    }

    /** kl   左カーソルキー */
    protected void cmd_kl() {
    }

    /** kr   右カーソルキー */
    protected void cmd_kr() {
    }

    /** ku   上カーソルキー */
    protected void cmd_ku() {
    }

    /** le   カーソルを左へ一文字分移動する */
    protected void cmd_le() {
        LOG.debug("cmd_le()");
        cc -= 1;
    }

    /** md   bold モード開始 */
    protected void cmd_md() {
    }

    /** me   so, us, mb, md, mr などのモード全てを終了する */
    protected void cmd_me() {
    }


    /** mr   反転モード開始 */
    protected void cmd_mr() {
    }

    /** nd   カーソルを右に一文字分移動 */
    protected void cmd_nd() {
    }

    /** nw   復帰コマンド */
    protected void cmd_nw() {
    }

    /** rc   保存しておいたカーソル位置に復帰する */
    protected void cmd_rc() {
    }

    /** rs   リセット文字列 */
    protected void cmd_rs() {
    }

    /** sc   カーソル位置を保存する */
    protected void cmd_sc() {
    }

    /** se   強調モード終了 */
    protected void cmd_se() {
    }

    /** sf   順方向の 1 行スクロール */
    protected void cmd_sf() {
    }

    /** so   強調モード開始 */
    protected void cmd_so() {
    }

    /** sr   逆スクロール */
    protected void cmd_sr() {
    }

    /** ta   次のハードウェアタブ位置へ移動 */
    protected void cmd_ta() {
        LOG.debug("cmd_ta()");
        cc = ((int)(cc / 8) + 1) * 8;
    }

    /** te   カーソル移動を用いるプログラムの終了 */
    protected void cmd_te() {
    }

    /** ti   カーソル移動を用いるプログラムの開始 */
    protected void cmd_ti() {
    }

    /** ue   下線モード終了 */
    protected void cmd_ue() {
    }

    /** up   カーソルを 1 行分上に移動 */
    protected void cmd_up() {
    }

    /** us   下線モード開始 */
    protected void cmd_us() {
    }
    
    protected void cmd_unknown() {
    	LOG.error("UNKNOWN:" + seqbuffer);
    	for (int c : seqbuffer) {
    		put((char)c);
    	}
    	seqbuffer.clear();
    }

    List<Character> seqbuffer = new ArrayList<>();
    List<Character> numbuffer = new ArrayList<>();
    List<Integer> params = new ArrayList<>();

    private void push(int c) {
    	seqbuffer.add((char)c);
    }

    /** ESC */
    private void state1() throws IOException {
        int c = screenReader.read();
        push(c);
        switch (c) {
        case '[': state2(); break;
        default: cmd_unknown();
        }
    }

    /** ESC '[' */
    private void state2() throws IOException {
        int c = screenReader.read();
        push(c);
        switch (c) {
        case '0': case '1': 
        case '5': case '6': case '7': case '8': case '9':
        	numbuffer.add((char)c);
        	state9();
        	break;
        case '2':
        	numbuffer.add((char)c);
        	state6();
        	break;
        case '3':
        	numbuffer.add((char)c);
        	state7();
        	break;
        case '4':
        	numbuffer.add((char)c);
        	state8();
        	break;
        case 'H': cmd_ho(); break;
        case 'J': cmd_cd(); break;
        case 'K': cmd_ce(); break;
        case 'M': cmd_dl(); break;
        case 'P': cmd_dc(); break;
        default: cmd_unknown();
        }		
    }

//	/** ESC '[' '2' */
//	private void state3() throws IOException {
//		int c = screenReader.read();
//		switch (c) {
//		case 0x1b: state4(); break;
//		default:
//		}		
//	}
//
//	/** ESC '[' 'H' ESC */
//	private void state4() throws IOException {
//		int c = screenReader.read();
//		switch (c) {
//		case '[': state5(); break;
//		default:
//		}		
//	}
//
//	/** ESC '[' 'H' ESC '[' */
//	private void state5() throws IOException {
//		int c = screenReader.read();
//		switch (c) {
//		case '2': state6(); break;
//		default:
//		}		
//	}

    /** ESC '[' 'H' ESC '[' '2' */
    private void state6() throws IOException {
        int c = screenReader.read();
        push(c);
        switch (c) {
        case '0': case '1': case '2': case '3': case '4':
        case '5': case '6': case '7': case '8': case '9':
        	numbuffer.add((char)c);
        	break;
        case 'L':
        	cmd_AL(createParam());
        	break;
        case 'P':
        	cmd_DC(createParam());
        	break;
        case 'M':
        	cmd_DL(createParam());
        	break;
        case 'B':
        	cmd_DO(createParam());
        	break;
        case 'J':
        	cmd_cl();
        	break;
        case ';':
        	state10();
        	break;
        default: cmd_unknown();
        }		
    }

    /** ESC '[' '3' */
    private void state7() throws IOException {
        int c = screenReader.read();
        push(c);
        switch (c) {
        case '0': case '1': case '2': case '3': case '4':
        case '5': case '6': case '7': case '8': case '9':
        	numbuffer.add((char)c);
        	break;
        case 'g': cmd_ct(); break;
        case 'L':
        case 'P':
        case 'M':
        case 'B':
        	break;
        default: cmd_unknown();
        }
    }

    /** ESC '[' '4' */
    private void state8() throws IOException {
    	int c = screenReader.read();
    	push(c);
        switch (c) {
        case '0': case '1': case '2': case '3': case '4':
        case '5': case '6': case '7': case '8': case '9':
        	numbuffer.add((char)c);
        	break;
        case 'h': cmd_im(); break;
        case 'l': cmd_ei(); break;
        case 'L':
        case 'P':
        case 'M':
        case 'B':
        	break;
        default: cmd_unknown();
        }
    }
    
    /** ESC '[' %d */
    private void state9() throws IOException {
    	int c = screenReader.read();
    	push(c);
        switch (c) {
        case '0': case '1': case '2': case '3': case '4':
        case '5': case '6': case '7': case '8': case '9':
        	break;
        case 'A':
        case 'C':
        case 'D':
        case 'L':
        case 'P':
        case 'M':
        case 'B':
        	break;
        case ';':
        default: cmd_unknown();
        }
    }
    
    /** ESC '[' %d ';' */
    private void state10() throws IOException {
    	int c = screenReader.read();
    	push(c);
        switch (c) {
        case '0': case '1': case '2': case '3': case '4':
        case '5': case '6': case '7': case '8': case '9':
        	createParam();
        	numbuffer.clear();
        	numbuffer.add((char)c);
        	state11();
        	break;
        default: cmd_unknown();
        }
    }
    
    /** ESC '[' %d ';' %d */
    private void state11() throws IOException {
    	int c = screenReader.read();
    	push(c);
        switch (c) {
        case '0': case '1': case '2': case '3': case '4':
        case '5': case '6': case '7': case '8': case '9':
        	numbuffer.add((char)c);
        	state11();
        	break;
        default: cmd_unknown();
        }
    }
    
    private int createParam() {
    	int result = 0;
    	for (char c : numbuffer) {
    		result += c - '0' + result * 10;
    	}
    	return result;
    }

    private TCanvas canvas;
    private OutputStream os = new ByteArrayOutputStream();
    private static Dimension bbox = new Dimension();
    
    public void window() {
        final TFrame frame = new TFrame();
        canvas = new TCanvas(this);
        canvas.requestFocus();
        canvas.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                try {
                    LOG.debug("keyTyped(" + e + ")");
                    byte[] b = String.valueOf(e.getKeyChar()).getBytes("UTF-8");
                    keyboardOutput.write(b);
                    keyboardOutput.flush();
                    canvas.repaint();
                } catch (UnsupportedEncodingException e1) {
                    LOG.error(e1.getMessage(), e1);
                } catch (IOException e1) {
                    LOG.error(e1.getMessage(), e1);
                }
                canvas.repaint();
            }

            @Override
            public void keyPressed(KeyEvent e) {
                LOG.debug("keyPressed(" + e + ")");
                if (e.isControlDown() && e.getKeyCode() != 17) {
                    byte[] b = new byte[] { (byte)(e.getKeyCode() - 0x40) };
                    try {
                        keyboardOutput.write(b);
                        keyboardOutput.flush();
                        canvas.repaint();
                    } catch (IOException ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                }
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
                //LOG.info("componentShown");
                //canvas.setPreferredSize(new Dimension(getCols() * width, getRows() * height));
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                //LOG.info("componentHidden");
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
            (int)bbox.getWidth() * this.getCols(),
            (int)bbox.getHeight() * this.getRows());
        frame.pack();
    }

    private static class TFrame extends Frame {
        
    }
    
    private static class TCanvas extends Canvas implements InputMethodRequests, InputMethodListener, TerminalEventListener {
        private Graphics imageBuffer;
        private int width;
        private int height;
        private Image back;
        private Terminal terminal;
        
        public TCanvas(Terminal terminal) {
            this.terminal = terminal;
            terminal.addTerminalEventListener(this);
            addInputMethodListener(this);
        }

        @Override
        public InputMethodRequests getInputMethodRequests() {
            return this;
        }

        @Override
        public void paint(Graphics g) {
            Dimension size = getSize();
            if (imageBuffer == null) {
                back = createImage(size.width, size.height);
                imageBuffer = back.getGraphics();
                imageBuffer.setFont(new Font("ＭＳ ゴシック", Font.PLAIN, 24));
                FontMetrics fm = imageBuffer.getFontMetrics();
                width = fm.charWidth('あ') / 2;
                height = fm.getAscent();
                bbox = new Dimension(width, height);
            }
            try {
                imageBuffer.clearRect(0, 0, (int)size.getWidth(), (int)size.getHeight());
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
                imageBuffer.drawLine(
                    terminal.getCurrentCol() * width, terminal.getCurrentRow() * height,
                    terminal.getCurrentCol() * width, (terminal.getCurrentRow() + 1) * height);
                getGraphics().drawImage(back, 0, 0, this);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void show(String s, int row, int col) {
            //LOG.debug("show(" + s + ", " + row + ", " + col + ")");
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

        @Override
        public Rectangle getTextLocation(TextHitInfo offset) {
            LOG.debug("getTextLocation(" + offset + ")");
            Rectangle r = new Rectangle(100, 100, 200, 100);
            return r;
        }

        @Override
        public TextHitInfo getLocationOffset(int x, int y) {
            LOG.debug("getLocationOffset");
            return null;
        }

        @Override
        public int getInsertPositionOffset() {
            LOG.debug("getInsertPositionOffset");
            return 0;
        }

        @Override
        public AttributedCharacterIterator getCommittedText(int beginIndex,
            int endIndex, Attribute[] attributes) {
            LOG.debug("getCommittedText");
            return null;
        }

        @Override
        public int getCommittedTextLength() {
            LOG.debug("getCommittedTextLength");
            return 0;
        }

        @Override
        public AttributedCharacterIterator cancelLatestCommittedText(
            Attribute[] attributes) {
            LOG.debug("cancelLatestCommittedText");
            return null;
        }

        @Override
        public AttributedCharacterIterator getSelectedText(
            Attribute[] attributes) {
            LOG.debug("getSelectedText");
            return null;
        }

        @Override
        public void inputMethodTextChanged(InputMethodEvent event) {
            LOG.debug("inputMethodTextChanged(" + event + ")");
            int committed = event.getCommittedCharacterCount();
            AttributedCharacterIterator iter = event.getText();
            if (iter != null) {
                getGraphics().clearRect(0, 0, 800, 600);
                getGraphics().drawString(iter, 0, 20);
            }
//            if (committed > 0) {
//                if (iter != null) {
//                    for (char c = iter.first(); c != CharacterIterator.DONE; c = iter.next()) {
//                        LOG.debug("c = " + c);
//                    }
//                }
//            }
        }

        @Override
        public void caretPositionChanged(InputMethodEvent event) {
            LOG.debug("caretPositionChanged(" + event + ")");
        }

        @Override
        public void onEvent(TerminalEvent event) {
            repaint();
        }
        
    }

    private List<TerminalEventListener> listeners = new ArrayList<>();
    public void addTerminalEventListener(TerminalEventListener listener) {
        listeners.add(listener);
    }

    public void fireEvent(TerminalEvent event) {
        for (TerminalEventListener l : listeners) {
            l.onEvent(event);
        }
    }

    public int getCurrentRow() {
        return cr;
    }
    
    public int getCurrentCol() {
        return cc;
    }

    public class TerminalEvent {
        
    }

    public interface TerminalEventListener {
        public void onEvent(TerminalEvent event);
    }
}
