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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.shorindo.tools.Termcap.Edge;
import com.shorindo.tools.Termcap.Node;
import com.shorindo.tools.Termcap.StateMachine;

public class Terminal {
    private static final Logger LOG = Logger.getLogger(Terminal.class);
    private String charset;
    private InputStreamReader screenReader;
    private OutputStream keyboardOutput;
    private char[][] buffer;
    private int rows;
    private int cols;
    private int cr, cc;
    private StateMachine machine;

    public Terminal(String charset, int cols, int rows) {
        this.rows = rows;
        this.cols = cols;
        this.buffer = new char[rows][cols];
        this.charset = charset;
        this.setIn(System.in);
        this.setOut(System.out);
        this.machine = new StateMachine(this);
    }
    
    public void start() {
        Thread th = new Thread() {
            @Override
            public void run() {
                int c;
                try {
                    while ((c = screenReader.read()) != -1) {
                        //LOG.debug("run(" + (char)c + ")");
                    	machine.write(c);
//                        switch (c) {
//                        case 0x1b: push(c); state1(); break;
//                        case '\b': cmd_dc(); break;
//                        case '\r': cmd_cr(); break;
//                        case '\n': cmd_do(); break;
//                        case '\t': cmd_ta(); break; 
//                        default: put((char)c);
//                        }
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
    	LOG.debug("cmd_cm(" + row + ", " + col + ")");
    	if (row >= rows) row = rows - 1;
    	else if (row < 1) row = 1;
    	if (col >= cols) col = cols - 1;
    	else if (col < 1) col = 1;
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

    /** カーソルを左へ一文字分移動する */
    protected void cmd_le() {
        LOG.debug("cmd_le()");
        if (cc > 0) {
        	cc -= 1;
        }
    }

    /** カーソルを左へN文字分移動する */
    protected void cmd_LE(int n) {
        LOG.debug("cmd_LE(" + n + ")");
        for (int i = 0; i < n; i++) {
        	cmd_le();
        }
    }

    /** md   bold モード開始 */
    protected void cmd_md() {
    	LOG.debug("cmd_md()");
    }

    /** me   so, us, mb, md, mr などのモード全てを終了する */
    protected void cmd_me() {
    	LOG.debug("cmd_me()");
    }


    /** mr   反転モード開始 */
    protected void cmd_mr() {
    	LOG.debug("cmd_mr()");
    }

    /** nd   カーソルを右に一文字分移動 */
    protected void cmd_nd() {
    	LOG.debug("cmd_nd()");
    }

    /** nw   復帰コマンド */
    protected void cmd_nw() {
    	LOG.debug("cmd_nw()");
    }

    /** rc   保存しておいたカーソル位置に復帰する */
    protected void cmd_rc() {
    	LOG.debug("cmd_rc()");
    }

    /** カーソルを右へ一文字分移動する */
    protected void cmd_ri() {
        LOG.debug("cmd_ri()");
        if (cc < cols) {
        	cc += 1;
        }
    }

    /** カーソルを右へN文字分移動する */
    protected void cmd_RI(int n) {
        LOG.debug("cmd_RI(" + n + ")");
        for (int i = 0; i < n; i++) {
        	cmd_ri();
        }
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

    /** カーソルを 1 行分上に移動 */
    protected void cmd_up() {
    	if (cr < 1) cr = 0;
    	else cr -= 1;
    }

    /** カーソルを N 行分上に移動 */
    protected void cmd_UP(int n) {
    	for (int i = 0; i < n; i++) {
    		cmd_up();
    	}
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
    	numbuffer.clear();
    	params.clear();
    }

    List<Character> seqbuffer = new ArrayList<>();
    List<Character> numbuffer = new ArrayList<>();
    List<Integer> params = new ArrayList<>();

    private void push(int c) {
    	seqbuffer.add((char)c);
    	//LOG.debug("push(" + seqbuffer + ")");
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
        case '0': 
        case '5': case '6': case '7': case '8': case '9':
        	numbuffer.add((char)c);
        	state9();
        	break;
        case '1': 
        	numbuffer.add((char)c);
        	state11();
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
        case 'H':
        	cmd_ho();
        	seqbuffer.clear();
        	break;
        case 'J':
        	cmd_cd();
        	seqbuffer.clear();
        	break;
        case 'K':
        	cmd_ce();
        	seqbuffer.clear();
        	break;
        case 'M':
        	cmd_dl();
        	seqbuffer.clear();
        	break;
        case 'P':
        	cmd_dc();
        	seqbuffer.clear();
        	break;
        default: cmd_unknown();
        }		
    }

    /** ESC '[' '2' */
    private void state6() throws IOException {
        int c = screenReader.read();
        push(c);
        switch (c) {
        case '0': case '1': case '2': case '3': case '4':
        case '5': case '6': case '7': case '8': case '9':
        	numbuffer.add((char)c);
        	state9();
        	break;
        case 'A':
        	cmd_UP(createParam());
        	break;
        case 'L':
        	cmd_AL(createParam());
        	seqbuffer.clear();
        	break;
        case 'P':
        	cmd_DC(createParam());
        	seqbuffer.clear();
        	break;
        case 'M':
        	cmd_DL(createParam());
        	seqbuffer.clear();
        	break;
        case 'B':
        	cmd_DO(createParam());
        	seqbuffer.clear();
        	break;
        case 'J':
        	cmd_cl();
        	numbuffer.clear();
        	seqbuffer.clear();
        	break;
        case ';':
        	params.add(createParam());
        	numbuffer.clear();
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
        	state9();
        	break;
        case 'g': cmd_ct(); break;
        case 'A':
        	cmd_UP(createParam());
        	break;
        case 'L':
        	cmd_AL(createParam());
        	seqbuffer.clear();
        	break;
        case 'P':
        	cmd_DC(createParam());
        	seqbuffer.clear();
        	break;
        case 'M':
        	cmd_DL(createParam());
        	seqbuffer.clear();
        	break;
        case 'B':
        	cmd_DO(createParam());
        	seqbuffer.clear();
        	break;
        case ';':
        	params.add(createParam());
        	state10();
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
        	state9();
        	break;
        case 'h': cmd_im(); break;
        case 'l': cmd_ei(); break;
        case 'A':
        	cmd_UP(createParam());
        	break;
        case 'L':
        	cmd_AL(createParam());
        	seqbuffer.clear();
        	break;
        case 'P':
        	cmd_DC(createParam());
        	seqbuffer.clear();
        	break;
        case 'M':
        	cmd_DL(createParam());
        	seqbuffer.clear();
        	break;
        case 'B':
        	cmd_DO(createParam());
        	seqbuffer.clear();
        	break;
        case ';':
        	params.add(createParam());
        	state10();
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
        	numbuffer.add((char)c);
        	state9();
        	break;
        case 'A':
        	cmd_UP(createParam());
        	break;
        case 'B':
        	cmd_DO(createParam());
        	break;
        case 'C':
        	cmd_RI(createParam());
        	break;
        case 'D':
        	cmd_LE(createParam());
        	break;
        case 'L':
        	cmd_AL(createParam());
        	break;
        case 'P':
        	cmd_DC(createParam());
        	break;
        case 'M':
        	cmd_unknown();
        	break;
        case ';':
        	params.add(createParam());
        	state10();
        	break;
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
        	numbuffer.add((char)c);
        	state10();
        	break;
        case 'H':
        	int p = createParam();
        	params.add(p);
        	cmd_cm(params.get(0), params.get(1));
        	numbuffer.clear();
        	seqbuffer.clear();
        	params.clear();
        	break;
        case 'r':
        	p = createParam();
        	params.add(p);
        	cmd_cs(params.get(0), params.get(1));
        	numbuffer.clear();
        	seqbuffer.clear();
        	params.clear();
        	break;
        default:
        	cmd_unknown();
        }
    }

    /** ESC '[' '1' */
    private void state11() throws IOException {
    	int c = screenReader.read();
    	push(c);
        switch (c) {
        case '0': case '1': case '2': case '3': case '4':
        case '5': case '6': case '7': case '8': case '9':
        	numbuffer.add((char)c);
        	state9();
        	break;
        case 'A':
        	cmd_UP(createParam());
        	break;
        case 'B':
        	cmd_DO(createParam());
        	break;
        case 'C':
        	cmd_RI(createParam());
        	break;
        case 'D':
        	cmd_LE(createParam());
        	break;
        case 'L':
        	cmd_AL(createParam());
        	break;
        case 'P':
        	cmd_DC(createParam());
        	seqbuffer.clear();
        	break;
        case 'm':
        	cmd_md();
        	numbuffer.clear();
        	seqbuffer.clear();
        	params.clear();
        	break;
        case ';':
        	params.add(createParam());
        	state10();
        	break;
        default:
        	cmd_unknown();
        }
    }
    
    private int createParam() {
    	int result = 0;
    	for (char c : numbuffer) {
    		result = (c - '0') + result * 10;
    	}
    	numbuffer.clear();
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
                //LOG.debug("keyPressed(" + e + ")");
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

    public static class StateMachine {
    	private static int INCR_PARAM = 0x1FFFF;
        private static int NUM_PARAM = 0x2FFFF;
        private List<Node> nodes;
        private List<Edge> edges;
        private Node start;
        private Node curr;
        private Terminal terminal;

        public StateMachine(Terminal terminal) {
        	this.terminal = terminal;
            nodes = new ArrayList<>();
            edges = new ArrayList<>();
            start = new Node(0);
            curr = start;
            nodes.add(start);

        	define("AL", new int[] { 0x1b, '[', NUM_PARAM, 'L' });
        	define("DC", new int[] { 0x1b, '[', NUM_PARAM, 'P' });
        	define("DL", new int[] { 0x1b, '[', NUM_PARAM, 'M' });
        	define("DO", new int[] { 0x1b, '[', NUM_PARAM, 'B' });
        	define("LE", new int[] { 0x1b, '[', NUM_PARAM, 'D' });
        	define("RI", new int[] { 0x1b, '[', NUM_PARAM, 'C' });
        	define("UP", new int[] { 0x1b, '[', NUM_PARAM, 'A' });
        	define("ae", new int[] { CTRL('O') });
        	define("al", new int[] { 0x1b, '[', 'L' });
        	define("as", new int[] { CTRL('N') });
        	define("bl", new int[] { CTRL('G') });
        	define("cd", new int[] { 0x1b, '[', 'J' });
        	define("ce", new int[] { 0x1b, '[', 'K' });
        	define("cl", new int[] { 0x1b, '[', 'H', 0x1b, '[', '2', 'J' });
        	define("cm", new int[] { 0x1b, '[', NUM_PARAM, ';', NUM_PARAM, 'H' });
        	define("cr", new int[] { '\r' });
        	define("cs", new int[] { 0x1b, '[', NUM_PARAM, ';', NUM_PARAM, 'r' });
        	define("ct", new int[] { 0x1b, '[', '3', 'g' });
        	define("dc", new int[] { 0x1b, '[', 'P' });
        	define("dl", new int[] { 0x1b, '[', 'M' });
        	define("do", new int[] { '\n' });
        	define("eA", new int[] { 0x1b, '[', ')', '0' });
        	define("ei", new int[] { 0x1b, '[', '4', 'l' });
        	//define("ho", new int[] { 0x1b, '[', 'H' });
        	define("im", new int[] { 0x1b, '[', '4', 'h' });
            //define("kd", new int[] { '\n' });
        	define("le", new int[] { CTRL('H') });
        	define("md", new int[] { 0x1b, '[', '1', 'm' });
        	define("me", new int[] { 0x1b, '[', 'm' });
        	define("ml", new int[] { 0x1b, 'l' });
        	define("mr", new int[] { 0x1b, '[', '7', 'm' });
        	define("mu", new int[] { 0x1b, 'm' });
        	define("nd", new int[] { 0x1b, '[', 'C' });
        	define("nw", new int[] { '\r', '\n' });
        	define("rc", new int[] { 0x1b, '8' });
        	define("rs", new int[] { 0x1b, '[', 'm', 0x1b, '?', '7', 'h', 0x1b, '[', '4', 'l', 0x1b, '>', 0x1b, '7', 0x1b, '[', 'r', 0x1b, '[', '1', ';', '3', ';', '4', ';', '6', 'l', 0x1b, '8' });
        	define("sc", new int[] { 0x1b, '7' });
        	define("se", new int[] { 0x1b, '[', 'm' });
        	//define("sf", new int[] { '\n' });
        	define("so", new int[] { 0x1b, '[', '7', 'm' });
        	define("sr", new int[] { 0x1b, 'M' });
        	define("ta", new int[] { CTRL('I') });
        	define("te", new int[] { 0x1b, '[', '2', 'J', 0x1b, '[', '?', '4', '7', 'l', 0x1b, 'B' });
        	define("ti", new int[] { 0x1b, '7', 0x1b, '[', '?', '4', '7', 'h' });
        	define("ue", new int[] { 0x1b, '[', 'm' });
        	define("up", new int[] { 0x1b, '[', 'A' });
        	define("us", new int[] { 0x1b, '[', '4', 'm' });
        }

        private static int CTRL(int c) {
        	return c - 64;
        }

        public void define(String action, int[] seq) {
            dig(start, action, seq);
        }

        private void dig(Node source, String action, int[] seq) {
            // LOG.debug("dig(" + source.getId() + ")");
            if (seq.length == 0) {
                source.setAction(action);
                return;
            }
            int event = seq[0];
            Optional<Edge> optEdge = edges.stream().filter(e -> {
                return e.getSource() == source && e.getEvent() == event;
            }).findFirst();
            if (optEdge.isPresent()) {
                dig(optEdge.get().getTarget(), action,
                    Arrays.copyOfRange(seq, 1, seq.length));
            } else {
                Node target = new Node(nodes.size());
                nodes.add(target);
                Edge edge = new Edge(source, target);
                edge.setEvent(event);
                edges.add(edge);
                dig(target, action, Arrays.copyOfRange(seq, 1, seq.length));

                if (event == NUM_PARAM) {
                    edge = new Edge(target, target);
                    edge.setEvent(event);
                    edges.add(edge);
                }
            }
        }

        public List<List<Node>> findPath(Node source, Set<Edge> visited) {
            // LOG.debug("findPath(" + source.getId() + ")");
            List<List<Node>> result = new ArrayList<>();
            List<Edge> sources = edges.stream().filter(e -> {
                return e.getSource() == source;
            }).collect(Collectors.toList());
            if (sources.size() == 0) {
                List<Node> nodeList = new ArrayList<>();
                nodeList.add(source);
                result.add(nodeList);
                return result;
            }
            for (Edge edge : sources) {
                if (visited.contains(edge)) {
                    continue;
                } else if (edge.getSource() == edge.getTarget()) {
                    continue;
                } else {
                    visited.add(edge);
                }
                List<List<Node>> pathList = findPath(edge.getTarget(), visited);
                for (List<Node> list : pathList) {
                    list.add(0, source);
                }
                result.addAll(pathList);
            }
            return result;
        }

        List<Integer> buffer = new ArrayList<>();
        public void write(int c) {
            buffer.add(c);
            Optional<Edge> optEdge = edges.stream()
                .filter(e -> {
                    return e.getSource() == curr && match(e.getEvent(), c);
                })
                .findFirst();
            if (optEdge.isPresent()) {
            	if (optEdge.get().getEvent() == NUM_PARAM) {
                    numbuffer.add(c);
            	} else if (numbuffer.size() > 0) {
                    int r = 0;
                    for (int i : numbuffer) {
                        r = r * 10 + (i - '0');
                    }
                    params.add(r);
                    numbuffer.clear();
            	}
            	curr = optEdge.get().getTarget();
                if (curr.getAction() != null) {
                	int p1, p2;
                	switch (curr.getAction()) {
                	case "AL":
                		p1 = params.get(0);
                		params.remove(0);
                		terminal.cmd_AL(p1);
                		break;
                	case "DC":
                		p1 = params.get(0);
                		params.remove(0);
                		terminal.cmd_DC(p1);
                		break;
                	case "DL":
                		p1 = params.get(0);
                		params.remove(0);
                		terminal.cmd_DL(p1);
                		break;
                	case "DO":
                		p1 = params.get(0);
                		params.remove(0);
                		terminal.cmd_DO(p1);
                		break;
                	case "LE":
                		p1 = params.get(0);
                		params.remove(0);
                		terminal.cmd_LE(p1);
                		break;
                	case "RI":
                		p1 = params.get(0);
                		params.remove(0);
                		terminal.cmd_RI(p1);
                		break;
                	case "UP":
                		p1 = params.get(0);
                		params.remove(0);
                		terminal.cmd_UP(p1);
                		break;
                	case "ae":
                		//terminal.cmd_ae();
                		break;
                	case "al":
                		terminal.cmd_al();
                		break;
                	case "as":
                		//terminal.cmd_as();
                		break;
                	case "bl":
                		terminal.cmd_bl();
                		break;
                	case "cd":
                		terminal.cmd_cd();
                		break;
                	case "ce":
                		//terminal.cmd_ee();
                		break;
                	case "cl":
                		terminal.cmd_cl();
                		break;
                	case "cm":
                		p1 = params.get(0);
                		p2 = params.get(1);
                		params.remove(0);
                		params.remove(0);
                		terminal.cmd_cm(p1, p2);
                		break;
                	case "cr":
                		terminal.cmd_cr();
                		break;
                	case "cs":
                		p1 = params.get(0);
                		p2 = params.get(1);
                		params.remove(0);
                		params.remove(0);
                		terminal.cmd_cs(p1, p2);
                		break;
                	case "ct":
                		terminal.cmd_ct();
                		break;
                	case "dc":
                		terminal.cmd_dc();
                		break;
                	case "dl":
                		terminal.cmd_dl();
                		break;
                	case "do":
                		terminal.cmd_do();
                		break;
                	case "eA":
                		//terminal.cmd_eA();
                		break;
                	case "ei":
                		terminal.cmd_ei();
                		break;
                	case "im":
                		terminal.cmd_im();
                		break;
                    case "kd":
                    	terminal.cmd_kd();
                		break;
                	case "le":
                		terminal.cmd_le();
                		break;
                	case "md":
                		terminal.cmd_md();
                		break;
                	case "me":
                		terminal.cmd_me();
                		break;
                	case "ml":
                		//terminal.cmd_ml();
                		break;
                	case "mr":
                		terminal.cmd_mr();
                		break;
                	case "mu":
                		//terminal.cmd_mu();
                		break;
                	case "nd":
                		terminal.cmd_nd();
                		break;
                	case "nw":
                		terminal.cmd_nw();
                		break;
                	case "rc":
                		terminal.cmd_rc();
                		break;
                	case "rs":
                		terminal.cmd_rs();
                		break;
                	case "sc":
                		terminal.cmd_sc();
                		break;
                	case "se":
                		terminal.cmd_se();
                		break;
                	case "sf":
                		terminal.cmd_sf();
                		break;
                	case "so":
                		terminal.cmd_so();
                		break;
                	case "sr":
                		terminal.cmd_sr();
                		break;
                	case "ta":
                		terminal.cmd_ta();
                		break;
                	case "te":
                		terminal.cmd_te();
                		break;
                	case "ti":
                		terminal.cmd_ti();
                		break;
                	case "ue":
                		terminal.cmd_ue();
                		break;
                	case "up":
                		terminal.cmd_up();
                		break;
                	case "us":
                		terminal.cmd_us();
                		break;
                	default:
                		LOG.debug("cmd_" + curr.getAction() + "()");
                		
                	}
                    curr = start;
                    buffer.clear();
                }
            } else {
                // バッファ + c を吐き出す
                for (int b : buffer) {
                	LOG.debug("put(" + (char)b + ")");
                    terminal.put((char)b);
                }
                curr = start;
                buffer.clear();
            }
        }

        private List<Integer> numbuffer = new ArrayList<>();
        private List<Integer> params = new ArrayList<>();
        private boolean match(int expect, int actual) {
            if (expect == NUM_PARAM && '0' <= actual && actual <= '9') {
                return true;
            } else {
                return expect == actual;
            }
        }

        public String toString() {
            Set<Edge> visited = new HashSet<>();
            StringBuffer sb = new StringBuffer();
            for (List<Node> path : findPath(start, visited)) {
                // LOG.debug("path=" + path);
                String sep = "";
                Node last = null;
                for (Node node : path) {
                    sb.append(sep + node.getId());
                    sep = " -> ";
                    last = node;
                }
                sb.append(" : " + last.getAction());
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    public static class Node {
        private int id;
        private String action;

        public Node(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getAction() {
            return action;
        }

        public String toString() {
            return String.valueOf(id);
        }
    }

    public static class Edge {
        private Node source;
        private Node target;
        private int event;

        public Edge(Node source, Node target) {
            this.source = source;
            this.target = target;
        }

        public Node getSource() {
            return source;
        }

        public Node getTarget() {
            return target;
        }

        public void setEvent(int event) {
            this.event = event;
        }

        public int getEvent() {
            return event;
        }

        public String toString() {
            return "[" + source + ", " + target + "]";
        }
    }
}
