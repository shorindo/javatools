package com.shorindo.terminal;

import java.awt.Canvas;
import java.awt.Color;
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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.TextHitInfo;
import java.awt.im.InputMethodRequests;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import com.shorindo.terminal.Terminfo.Capability;
import com.shorindo.terminal.Terminfo.TerminfoException;
import com.shorindo.tools.Logger;

public class Terminal {
    private static final Logger LOG = Logger.getLogger(Terminal.class);
    private String charset;
    private TermReader termReader;
    private OutputStream keyboardOutput;
    private DecoratedCharacter[][] buffer;
    private int rows;
    private int cols;
    private int cr, cc;
    private Settings settings = new Settings();
    private Termcap machine;
    private Thread thread;

    public Terminal(String charset, int cols, int rows) {
        this.rows = rows;
        this.cols = cols;
        this.buffer = new DecoratedCharacter[rows][cols];
        for (int i = 0; i < rows; i++) {
            this.buffer[i] = DecoratedCharacter.newArray(cols);
        }
        this.charset = charset;
        this.setIn(System.in);
        this.setOut(System.out);
        this.machine = new Termcap(this);
    }
    
    public void connect(InputStream is, OutputStream os) {
        LOG.debug("connect()");
        if (thread != null) {
            thread.interrupt();
        }
        this.setIn(is);
        this.setOut(os);
        thread = new Thread() {
            @Override
            public void run() {
                int c;
                try {
                    while ((c = termReader.read()) != -1) {
                        LOG.debug("run(" + (char)c + ")");
                        machine.write(c);
                    }
                } catch (IOException e) {
                    LOG.warn(e.getMessage());
                } finally {
                    LOG.info("run() finished");
                    //System.exit(0);
                }
            }
        };
        thread.start();
    }

    /**
     * キーボードからのデータの出力先
     */
    private void setOut(OutputStream os) {
        keyboardOutput = os;
    }

    /**
     * スクリーンに書き込むデータの入力元
     */
    private void setIn(InputStream is) {
        try {
            termReader = new TermReader(new InputStreamReader(is, charset));
        } catch (UnsupportedEncodingException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    protected DecoratedCharacter[][] getBuffer() {
        return buffer;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }
    
    public Settings getSettings() {
        return settings;
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
            DecoratedCharacter dc = new DecoratedCharacter(c);
            dc.setFontStyle(fontStyle);
            buffer[cr][cc] = dc;
            cc += w;
        } else if (cr < rows - 1) {
            cr += 1;
            DecoratedCharacter dc = new DecoratedCharacter(c);
            dc.setFontStyle(fontStyle);
            buffer[cr][0] = dc;
            cc = w;
        } else {
            cmd_cs(1, cr);
            buffer[rows - 1] = DecoratedCharacter.newArray(cols);
            cc = 0;
            put(c);
        }
    }

    /** １行挿入 */
    protected void cmd_al() {
        LOG.debug("cmd_al");
    }

    /** N行挿入 */
    protected void cmd_AL(int n) {
        LOG.debug("cmd_AL(" + n + ")");
    }

    /** ベルを鳴らす */
    protected void cmd_bl() {
        LOG.debug("cmd_bl()");
    }


    /** 画面の最後までクリア */
    protected void cmd_cd() {
        LOG.debug("cmd_cd()");
    }

    /** 行の最後までクリア */
    protected void cmd_ce() {
        LOG.debug("cmd_ce()");
        for (int c = cc; c < cols; c++) {
            buffer[cr][c] = new DecoratedCharacter((char)0);
        }
    }
    
    /** 水平絶対位置 %1 */
    private void cmd_ch(int col) {
        LOG.debug("cmd_ch(" + col + ")");
        if (col < 1) col = 1;
        else if (col > cols) col = cols;
        this.cc = col - 1;
    }

    /** 画面を消去し、カーソルをホームポジションへ */
    protected void cmd_cl() {
        LOG.debug("cmd_cl()");
        buffer = new DecoratedCharacter[rows][cols];
        for (int i = 0; i < rows; i++) {
            buffer[i] = DecoratedCharacter.newArray(cols);
        }
        cr = 0;
        cc = 0;
    }

    /** 画面上の %1 行、 %2 桁へカーソルを移動 */
    protected void cmd_cm(int row, int col) {
        LOG.debug("cmd_cm(" + row + ", " + col + ")");
        if (row > rows) row = rows;
        else if (row < 1) row = 1;
        if (col > cols) col = cols;
        else if (col < 1) col = 1;
        this.cr = row - 1;
        this.cc = col - 1;
    }

    /** 復帰 */
    protected void cmd_cr() {
    	LOG.debug("cmd_cr()");
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
        LOG.debug("cmd_ct()");
    }

    /** 垂直絶対位置 %1 */
    private void cmd_cv(int row) {
        LOG.debug("cmd_cv(" + row + ")");
        if (row < 0) row = 1;
        else if (row > rows) row = rows;
        this.cr = row - 1;
    }

    /** 一文字削除する */
    protected void cmd_dc() {
        LOG.debug("cmd_dc()");
        buffer[cr][cc] = new DecoratedCharacter((char)0);
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
            buffer[rows - 1] = DecoratedCharacter.newArray(cols);
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
        LOG.debug("cmd_ei()");
    }

    /** カーソルをホームポジションに移動 */
    protected void cmd_ho() {
        LOG.debug("cmd_ho");
    }
    
    /** insert モード開始 */
    protected void cmd_im() {
        LOG.debug("cmd_im()");
    }

    /** バックスペースキー */
    protected void cmd_kb() {
        LOG.debug("cmd_kb");
    }

    /** 下カーソルキー */
    protected void cmd_kd() {
    }

    /** キーパッドをオフにする */
    protected void cmd_ke() {
    }

    /** home キー */
    protected void cmd_kh() {
    }

    /** 左カーソルキー */
    protected void cmd_kl() {
    }

    /** 右カーソルキー */
    protected void cmd_kr() {
    }

    /** 上カーソルキー */
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

    /** bold モード開始 */
    protected void cmd_md() {
        LOG.debug("cmd_md()");
        //fontStyle = Font.BOLD;
        fontStyle = Font.ITALIC;
    }

    /** so, us, mb, md, mr などのモード全てを終了する */
    protected void cmd_me() {
        LOG.debug("cmd_me()");
        fontStyle = Font.PLAIN;
    }


    /** 反転モード開始 */
    protected void cmd_mr() {
        LOG.debug("cmd_mr()");
    }

    /** カーソルを右に一文字分移動 */
    protected void cmd_nd() {
        LOG.debug("cmd_nd()");
    }

    /** 復帰コマンド */
    protected void cmd_nw() {
        LOG.debug("cmd_nw()");
        cmd_cr();
        cmd_do();
    }

    /** 保存しておいたカーソル位置に復帰する */
    protected void cmd_rc() {
        LOG.debug("cmd_rc(" + savedCursor + ")");
        this.cr = savedCursor.getRow();
        this.cc = savedCursor.getCol();
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

    /** リセット文字列 */
    protected void cmd_rs() {
        LOG.debug("cmd_rs()");
    }

    /** カーソル位置を保存する */
    private Cursor savedCursor;
    protected void cmd_sc() {
        LOG.debug("cmd_sc(" + cr + ", " + cc + ")");
        savedCursor = new Cursor(cr, cc);
    }

    /** 強調モード終了 */
    protected void cmd_se() {
        LOG.debug("cmd_se()");
    }

    /** 順方向の 1 行スクロール */
    protected void cmd_sf() {
        LOG.debug("cmd_sf()");
    }

    /** 強調モード開始 */
    protected void cmd_so() {
        LOG.debug("cmd_so()");
    }

    /** 逆スクロール */
    protected void cmd_sr() {
        LOG.debug("cmd_sr()");
    }

    /** 次のハードウェアタブ位置へ移動 */
    protected void cmd_ta() {
        LOG.debug("cmd_ta()");
        cc = ((int)(cc / 8) + 1) * 8;
    }

    /** カーソル移動を用いるプログラムの終了 */
    protected void cmd_te() {
        LOG.debug("cmd_te()");
    }

    /** カーソル移動を用いるプログラムの開始 */
    protected void cmd_ti() {
        LOG.debug("cmd_ti()");
    }

    /** 下線モード終了 */
    protected void cmd_ue() {
        LOG.debug("cmd_ue()");
    }

    /** カーソルを 1 行分上に移動 */
    protected void cmd_up() {
        LOG.debug("cmd_up()");
        if (cr < 1) cr = 0;
        else cr -= 1;
    }

    /** カーソルを N 行分上に移動 */
    protected void cmd_UP(int n) {
        LOG.debug("cmd_UP(" + n + ")");
        for (int i = 0; i < n; i++) {
            cmd_up();
        }
    }

    /** 下線モード開始 */
    protected void cmd_us() {
        LOG.debug("cmd_us()");
    }
    
    private TCanvas canvas;
    //private OutputStream os = new ByteArrayOutputStream();
    private static Dimension bbox = new Dimension();
    private static int fontStyle = Font.PLAIN;
    
    public void open() {
        final TFrame frame = new TFrame();
        canvas = new TCanvas(this);
        canvas.requestFocus();
        canvas.setFocusTraversalKeysEnabled(false); // TABキーを有効にする
        canvas.addKeyListener(new KeyAdapter() {
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
                int keycode = e.getKeyCode();
                if (e.isControlDown() && keycode != KeyEvent.VK_CONTROL) {
                    byte[] b = new byte[] { (byte)(keycode & 63) };
                    try {
                        keyboardOutput.write(b);
                        keyboardOutput.flush();
                        canvas.repaint();
                    } catch (IOException ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                }
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

        canvas.setFont(getSettings().getFont(fontStyle));
        FontMetrics fm = canvas.getGraphics().getFontMetrics();
        int width = fm.charWidth('あ') / 2;
        int height = fm.getAscent() + fm.getDescent();
        bbox = new Dimension(width, height);
        canvas.setSize(
            (int)bbox.getWidth() * this.getCols(),
            (int)bbox.getHeight() * this.getRows());
        canvas.setBackground(settings.getBackColor());
        canvas.setForeground(settings.getForeColor());
        frame.pack();
    }

    private static class TFrame extends Frame {
        private static final long serialVersionUID = 1L;
        
    }
    
    private static class TCanvas extends Canvas implements InputMethodRequests, InputMethodListener, KeyboardEventListener {
        private static final long serialVersionUID = 1L;
        private Graphics imageBuffer;
        private int width;
        private int height;
        private Image back;
        private Terminal terminal;
        
        public TCanvas(Terminal terminal) {
            this.terminal = terminal;
            terminal.addTerminalEventListener(this);
            //addInputMethodListener(this);
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
                imageBuffer.setFont(terminal.getSettings().getFont(fontStyle));
                FontMetrics fm = imageBuffer.getFontMetrics();
                width = fm.charWidth('あ') / 2;
                height = fm.getAscent() + fm.getDescent();
                bbox = new Dimension(width, height);
            }
            try {
                imageBuffer.clearRect(0, 0, (int)size.getWidth(), (int)size.getHeight());
                DecoratedCharacter[][] textBuffer = terminal.getBuffer();
                for (int i = 0; i < textBuffer.length; i++) {
                    DecoratedCharacter[] line = textBuffer[i];
                    for (int j = 0; j < line.length; j++) {
                        DecoratedCharacter ch = line[j];
                        if (ch == null) {
                            LOG.error("buff[" + i + "][" + j + "] is null.");
                        }
                        if (ch.getValue() > 0) {
                            //LOG.info("ch=" + ch);
                            show(ch, i, j);
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

        public void show(DecoratedCharacter ch, int row, int col) {
            //LOG.debug("show(" + s + ", " + row + ", " + col + ")");
            int offset = width * col;
            imageBuffer.setFont(terminal.getSettings().getFont(ch.getFontStyle()));
            imageBuffer.drawString(String.valueOf(ch.getValue()), offset, height * (row + 1));
            // FIXME
            if (ch.getValue() < 0xff) 
                offset += width;
            else
                offset += width * 2;
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
            //int committed = event.getCommittedCharacterCount();
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
        public void onEvent(KeyboardEvent event) {
            terminal.put((char)event.getValue());
            repaint();
        }
        
    }

    private List<KeyboardEventListener> listeners = new ArrayList<>();
    public void addTerminalEventListener(KeyboardEventListener listener) {
        listeners.add(listener);
    }

    public void fireEvent(KeyboardEvent event) {
        for (KeyboardEventListener l : listeners) {
            l.onEvent(event);
        }
    }

    public int getCurrentRow() {
        return cr;
    }
    
    public int getCurrentCol() {
        return cc;
    }
    
    private static class Cursor {
        private int row;
        private int col;

        public Cursor(int row, int col) {
            this.row = row;
            this.col = col;
        }
        public int getRow() {
            return row;
        }
        public int getCol() {
            return col;
        }
        public String toString() {
            return "[" + row + ", " + col + "]";
        }
    }
    
    private static class Settings {
        //private Color foreColor = Color.WHITE;
        private Color foreColor = Color.BLUE;
        private Color backColor = Color.WHITE;
        private Font plainFont = new Font("ＭＳ ゴシック", Font.PLAIN, 24);
        private Font boldFont = new Font("ＭＳ ゴシック", Font.BOLD, 24);
        private Font italicFont = new Font("ＭＳ ゴシック", Font.ITALIC, 24);

        public Color getForeColor() {
            return foreColor;
        }
        public Color getBackColor() {
            return backColor;
        }
        public Font getFont(int strong) {
            switch (strong) {
            case Font.BOLD:
                return boldFont;
            case Font.ITALIC:
                return italicFont;
            default:
                return plainFont;
            }
        }
    }

    private static List<Integer> numbuffer = new ArrayList<>();
    private static LinkedList<Integer> params = new LinkedList<>();

    /**
     * 
     */
    public static class Termcap {
        private List<Node> nodes;
        private List<Edge> edges;
        private Node start;
        private Terminal terminal;

        public Termcap(Terminal terminal) {
            this.terminal = terminal;
            nodes = new ArrayList<>();
            edges = new ArrayList<>();
            start = new Node(0);
            nodes.add(start);
            
            try {
                Terminfo info = Terminfo.compile(
                    "#       Reconstructed via infocmp from file: /usr/share/terminfo/78/xterm\n" +
                    "xterm|xterm terminal emulator (X Window System),\n" +
                    "  am, km, mir, msgr, xenl,\n" +
                    "  cols#80, it#8, lines#24,\n" +
                    "  acsc=``aaffggiijjkkllmmnnooppqqrrssttuuvvwwxxyyzz{{||}}~~,\n" +
                    "  bel=^G, bold=\\E[1m, clear=\\E[H\\E[2J, cr=^M,\n" +
                    "  csr=\\E[%i%p1%d;%p2%dr, cub=\\E[%p1%dD, cub1=^H,\n" +
                    "  cud=\\E[%p1%dB, cud1=^J, cuf=\\E[%p1%dC, cuf1=\\E[C,\n" +
                    "  cup=\\E[%i%p1%d;%p2%dH, cuu=\\E[%p1%dA, cuu1=\\E[A,\n" +
                    "  dch=\\E[%p1%dP, dch1=\\E[P, dl=\\E[%p1%dM, dl1=\\E[M, ed=\\E[J,\n" +
                    "  el=\\E[K, enacs=\\E)0, .home=\\E[H, ht=^I, hts=\\EH, il=\\E[%p1%dL,\n" +
                    "  il1=\\E[L, ind=^J,\n" +
                    "  is2=\\E7\\E[r\\E[m\\E[?7h\\E[?1;3;4;6l\\E[4l\\E8\\E>, kbs=^H,\n" +
                    "  kcub1=\\EOD, kcud1=\\EOB, kcuf1=\\EOC, kcuu1=\\EOA,\n" +
                    "  kdch1=\\E[3~, kf1=\\E[11~, kf10=\\E[21~, kf11=\\E[23~,\n" +
                    "  kf12=\\E[24~, kf13=\\E[25~, kf14=\\E[26~, kf15=\\E[28~,\n" +
                    "  kf16=\\E[29~, kf17=\\E[31~, kf18=\\E[32~, kf19=\\E[33~,\n" +
                    "  kf2=\\E[12~, kf20=\\E[34~, kf3=\\E[13~, kf4=\\E[14~,\n" +
                    "  kf5=\\E[15~, kf6=\\E[17~, kf7=\\E[18~, kf8=\\E[19~, kf9=\\E[20~,\n" +
                    "  kfnd=\\E[1~, kich1=\\E[2~, kmous=\\E[M, knp=\\E[6~, kpp=\\E[5~,\n" +
                    "  kslt=\\E[4~, rc=\\E8, rev=\\E[7m, ri=\\EM, rmacs=^O,\n" +
                    "  rmcup=\\E[2J\\E[?47l\\E8, rmir=\\E[4l, rmkx=\\E[?1l\\E>,\n" +
                    "  rmso=\\E[m, rmul=\\E[m,\n" +
                    "  rs2=\\E7\\E[r\\E8\\E[m\\E[?7h\\E[?1;3;4;6l\\E[4l\\E>, sc=\\E7,\n" +
                    "  sgr0=\\E[m, smacs=^N, smcup=\\E7\\E[?47h, smir=\\E[4h,\n" +
                    "  smkx=\\E[?1h\\E=, smso=\\E[7m, smul=\\E[4m, tbc=\\E[3g,\n" +
                    "  u6=\\E[%i%d;%dR, u7=\\E[6n, u8=\\E[?1;2c, u9=\\E[c,\n");
                for (Capability cap : info.getCapabilities()) {
                    if (cap.getType() == null) continue;
                    if (cap.getData() instanceof List) {
                        List<Integer> list = ((List<Integer>)cap.getData())
                            .stream()
                            .filter(i -> {
                                switch (i) {
                                case Terminfo.PARAM_I:
                                case Terminfo.PARAM_P1:
                                case Terminfo.PARAM_P2:
                                case Terminfo.PARAM_P3:
                                case Terminfo.PARAM_P4:
                                case Terminfo.PARAM_P5:
                                case Terminfo.PARAM_P6:
                                case Terminfo.PARAM_P7:
                                case Terminfo.PARAM_P8:
                                case Terminfo.PARAM_P9:
                                    return false;
                                default:
                                    return true;
                                }
                            })
                            .collect(Collectors.toList());
                        int array[] = new int[list.size()];
                        for (int i = 0; i < list.size(); i++) {
                            array[i] = list.get(i);
                        }
                        define(cap.getType().getCapCode(), array);
                    }
                }
            } catch (TerminfoException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
//            define("AB", new int[] { 0x1b, '4', '8', ';', '5', ';', NUM_PARAM, 'm' });
//            define("AF", new int[] { 0x1b, '3', '8', ';', '5', ';', NUM_PARAM, 'm' });
//            define("AL", new int[] { 0x1b, '[', NUM_PARAM, 'L' });
//            define("DC", new int[] { 0x1b, '[', NUM_PARAM, 'P' });
//            define("DL", new int[] { 0x1b, '[', NUM_PARAM, 'M' });
//            define("DO", new int[] { 0x1b, '[', NUM_PARAM, 'B' });
//            define("LE", new int[] { 0x1b, '[', NUM_PARAM, 'D' });
//            define("RI", new int[] { 0x1b, '[', NUM_PARAM, 'C' });
//            define("UP", new int[] { 0x1b, '[', NUM_PARAM, 'A' });
//            define("ae", new int[] { CTRL('O') });
//            define("al", new int[] { 0x1b, '[', 'L' });
//            define("as", new int[] { CTRL('N') });
//            define("bl", new int[] { CTRL('G') });
//            define("cd", new int[] { 0x1b, '[', 'J' });
//            define("ce", new int[] { 0x1b, '[', 'K' });
//            define("ch", new int[] { 0x1b, '[', NUM_PARAM, 'G' });
//            define("cl", new int[] { 0x1b, '[', 'H', 0x1b, '[', '2', 'J' });
//            define("cm", new int[] { 0x1b, '[', NUM_PARAM, ';', NUM_PARAM, 'H' });
//            define("cr", new int[] { '\r' });
//            define("cs", new int[] { 0x1b, '[', NUM_PARAM, ';', NUM_PARAM, 'r' });
//            define("ct", new int[] { 0x1b, '[', '3', 'g' });
//            define("cv", new int[] { 0x1b, '[', NUM_PARAM, 'd' });
//            define("dc", new int[] { 0x1b, '[', 'P' });
//            define("dl", new int[] { 0x1b, '[', 'M' });
//            define("do", new int[] { '\n' });
//            define("eA", new int[] { 0x1b, '[', ')', '0' });
//            define("ei", new int[] { 0x1b, '[', '4', 'l' });
//            //define("ho", new int[] { 0x1b, '[', 'H' });
//            define("im", new int[] { 0x1b, '[', '4', 'h' });
//            //define("kd", new int[] { '\n' });
//            define("ke", new int[] { 0x1b, '[', '?', '1', 'l', 0x1b, '>' });
//            define("ks", new int[] { 0x1b, '[', '?', '1', 'h', 0x1b, '=' });
//            define("le", new int[] { CTRL('H') });
//            define("md", new int[] { 0x1b, '[', '1', 'm' });
//            define("me", new int[] { 0x1b, '[', 'm' });
//            define("ml", new int[] { 0x1b, 'l' });
//            define("mr", new int[] { 0x1b, '[', '7', 'm' });
//            define("mu", new int[] { 0x1b, 'm' });
//            define("nd", new int[] { 0x1b, '[', 'C' });
//            define("nw", new int[] { '\r', '\n' });
//            define("rc", new int[] { 0x1b, '8' });
//            define("rs", new int[] { 0x1b, '[', 'm', 0x1b, '?', '7', 'h', 0x1b, '[', '4', 'l', 0x1b, '>', 0x1b, '7', 0x1b, '[', 'r', 0x1b, '[', '1', ';', '3', ';', '4', ';', '6', 'l', 0x1b, '8' });
//            define("sc", new int[] { 0x1b, '7' });
//            define("se", new int[] { 0x1b, '[', 'm' });
//            //define("sf", new int[] { '\n' });
//            define("so", new int[] { 0x1b, '[', '7', 'm' });
//            define("sr", new int[] { 0x1b, 'M' });
//            define("ta", new int[] { CTRL('I') });
//            define("te", new int[] { 0x1b, '[', '2', 'J', 0x1b, '[', '?', '4', '7', 'l', 0x1b, '8' });
//            define("ti", new int[] { 0x1b, '7', 0x1b, '[', '?', '4', '7', 'h' });
//            define("ue", new int[] { 0x1b, '[', 'm' });
//            define("up", new int[] { 0x1b, '[', 'A' });
//            define("us", new int[] { 0x1b, '[', '4', 'm' });
//            define("us", new int[] { 0x1b, '[', '2', '4', 'm' });
//            define("xx", new int[] { 0x1b, '[', '0', 'm' });

            LOG.debug(this.toString());
        }

//        private static int CTRL(int c) {
//            return Character.toUpperCase(c) & 63;
//        }

        public void define(String action, int[] seq) {
            define(start, action, seq);
        }

        private void define(Node source, String action, int[] seq) {
            // LOG.debug("dig(" + source.getId() + ")");
            if (seq.length == 0) {
                source.addAction(action);
                return;
            }
            int event = seq[0];
            Optional<Edge> optEdge = edges.stream().filter(e -> {
                return e.getSource() == source && e.getEvent() == event;
            }).findFirst();
            if (optEdge.isPresent()) {
                define(optEdge.get().getTarget(), action,
                    Arrays.copyOfRange(seq, 1, seq.length));
            } else {
                Node target = new Node(nodes.size());
                nodes.add(target);
                Edge edge = new Edge(source, target);
                edge.setEvent(event);
                edges.add(edge);
                source.addEdge(edge);
                define(target, action, Arrays.copyOfRange(seq, 1, seq.length));

                if (event == Terminfo.PARAM_D) {
                    edge = new Edge(target, target);
                    edge.setEvent(event);
                    edges.add(edge);
                    target.addEdge(edge);
                }
            }
        }

        public List<List<Edge>> findPath(Node source, Set<Edge> visited) {
            // LOG.debug("findPath(" + source.getId() + ")");
            List<List<Edge>> result = new ArrayList<>();
            List<Edge> edgeList = edges.stream().filter(e -> {
                return e.getSource() == source;
            }).collect(Collectors.toList());
            if (edgeList.size() == 0) {
                List<Edge> nodeList = new ArrayList<>();
                result.add(nodeList);
                return result;
            }
            for (Edge edge : edgeList) {
                if (visited.contains(edge)) {
                    continue;
                } else if (edge.getSource() == edge.getTarget()) {
                    continue;
                } else {
                    visited.add(edge);
                }
                List<List<Edge>> pathList = findPath(edge.getTarget(), visited);
                for (List<Edge> list : pathList) {
                    list.add(0, edge);
                }
                result.addAll(pathList);
            }
            return result;
        }

        List<Integer> buffer = new CopyOnWriteArrayList<>();
        public void write(int c) {
            buffer.add(c);
            try {
                Set<String> actions = start.accept(buffer);
                if (actions.size() > 0) {
                    for (String action : actions) {
                        doAction(action);
                    }
                    // FIXME 次があったらクリアしたくない
                    numbuffer.clear();
                    params.clear();
                    buffer.clear();
                }
            } catch (UnmatchException e1) {
                //LOG.debug("unmatch:" + (char)c);
                for (int b : buffer) {
                    //LOG.debug("put(" + (char)b + ")");
                    terminal.fireEvent(new KeyboardEvent(KeyboardEventType.TYPE, b));
                }
                numbuffer.clear();
                params.clear();
                buffer.clear();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        
        private void doAction(String action) {
            int p1, p2;
            switch (action) {
            case "AL":
                terminal.cmd_AL(params.removeLast());
                break;
            case "DC":
                terminal.cmd_DC(params.removeLast());
                break;
            case "DL":
                terminal.cmd_DL(params.removeLast());
                break;
            case "DO":
                terminal.cmd_DO(params.removeLast());
                break;
            case "LE":
                terminal.cmd_LE(params.removeLast());
                break;
            case "RI":
                terminal.cmd_RI(params.removeLast());
                break;
            case "UP":
                terminal.cmd_UP(params.removeLast());
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
                terminal.cmd_ce();
                break;
            case "ch":
                terminal.cmd_ch(params.removeLast());
                break;
            case "cl":
                terminal.cmd_cl();
                break;
            case "cm":
                p2 = params.removeLast();
                p1 = params.removeLast();
                terminal.cmd_cm(p1, p2);
                break;
            case "cr":
                terminal.cmd_cr();
                break;
            case "cs":
                p2 = params.removeLast();
                p1 = params.removeLast();
                terminal.cmd_cs(p1, p2);
                break;
            case "ct":
                terminal.cmd_ct();
                break;
            case "cv":
                terminal.cmd_cv(params.removeLast());
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
                LOG.debug("UNKNOWN:" + action);
            }
        }

        public String toString() {
            Set<Edge> visited = new HashSet<>();
            StringBuffer sb = new StringBuffer();
            for (List<Edge> path : findPath(start, visited)) {
                // LOG.debug("path=" + path);
                String sep = "";
                Node prev = null;
                Node next = null;
                sb.append(path.get(0).getSource());
                sb.append(" -(" + conv(path.get(0).getEvent()) + ")-> ");
                for (int i = 0; i < path.size(); i++) {
                    next = path.get(i).getTarget();
                    for (int j = 0; j < edges.size(); j++) {
                        Edge edge = edges.get(j);
                        if (edge.getSource() == prev && edge.getTarget() == next) {
                            sep = " -(" + conv(edge.getEvent()) + ")-> ";
                            break;
                        }
                    }
                    sb.append(sep + next.getId());
                    prev = next;
                }
                sb.append(" : " + next.getActions());
                sb.append("\n");
            }
            return sb.toString();
        }
        
        private String conv(int c) {
            switch (c) {
            case ('O' - 64): return "^O";
            case ('N' - 64): return "^N";
            case ('G' - 64): return "^G";
            case ('H' - 64): return "^H";
            case '\t': return "\\t";
            case '\r': return "\\r";
            case '\n': return "\\n";
            case 0x1b: return "ESC";
            case Terminfo.PARAM_D: return "NUM";
            default: return String.valueOf((char)c);
            }
        }
    }

    public static class Node {
        private int id;
        private Set<String> actions;
        private List<Edge> edges;

        public Node(int id) {
            this.id = id;
            this.actions = new HashSet<>();
            this.edges = new ArrayList<>();
        }

        public void addEdge(Edge edge) {
            edges.add(edge);
        }

        public Set<String> accept(List<Integer> buffer) throws UnmatchException, IOException {
            if (buffer.size() == 0) {
                return actions;
            }
            int c = buffer.get(0);
            //LOG.debug("consume(" + (char)c + ") <= " + this);
            List<Edge> targets = edges.stream()
                .filter(e -> {
                    if (c == e.getEvent()) {
                        return true;
                    } else if ('0' <= c && c <= '9' && e.getEvent() == Terminfo.PARAM_D) {
                        return true;
                    } else {
                        return false;
                    }
                })
                .collect(Collectors.toList());
            List<Integer> subList = buffer.subList(1, buffer.size());
            for (Edge edge : targets) {
                if (edge.getEvent() == Terminfo.PARAM_D) {
                    numbuffer.add(c);
                } else if (numbuffer.size() > 0) {
                    int r = 0;
                    for (int n : numbuffer) {
                        r = r * 10 + (n - '0');
                    }
                    params.add(r);
                    numbuffer.clear();
                }
                try {
                    Node next = edge.getTarget();
                    return next.accept(subList);
                } catch(UnmatchException e) {
                    numbuffer.clear();
                    params.clear();
                }
            }
            throw new UnmatchException();
        }

        public int getId() {
            return id;
        }

        public void addAction(String action) {
            this.actions.add(action);
        }

        public Set<String> getActions() {
            return actions;
        }

        public String toString() {
            return String.valueOf(id);
        }
    }

    public static class UnmatchException extends Exception {
		private static final long serialVersionUID = 1L;
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
    
    /**
     * 
     */
    public static class KeyboardEvent {
        private KeyboardEventType type;
        private int value;
        public KeyboardEvent(KeyboardEventType type, int value) {
            this.type = type;
            this.value = value;
        }
        public KeyboardEventType getType() {
            return type;
        }
        public int getValue() {
            return value;
        }
    }
    
    /**
     * 
     */
    public enum KeyboardEventType {
        TYPE
    }

    /**
     * 
     */
    public interface KeyboardEventListener {
        public void onEvent(KeyboardEvent event);
    }

    /**
     * 
     */
    public interface ScreenEventListener {
        public void onEvent(ScreenEvent event);
    }

    /**
     * 
     */
    public class ScreenEvent {
        private ScreenEventType type;
        private List<Integer> params;

        public ScreenEvent() {
            params = new ArrayList<>();
        }
        public ScreenEventType getType() {
            return type;
        }
        public void setType(ScreenEventType type) {
            this.type = type;
        }
        public List<Integer> getParams() {
            return params;
        }
    }
    
    /**
     * 
     */
    public enum ScreenEventType {
        TC_AL("AL"), TC_DC("DC"), TC_DL("DL"), TC_DO("DO"), TC_LE("LE"), TC_RI("RI"), TC_UP("UP"), TC_ae("ae"),
        TC_al("al"), TC_as("as"), TC_bl("bl"), TC_cd("cd"), TC_ce("ce"), TC_cl("cl"), TC_cm("cm"), TC_cr("cr"),
        TC_cs("cs"), TC_ct("ct"), TC_dc("dc"), TC_dl("dl"), TC_do("do"), TC_eA("eA"), TC_ei("ei"), TC_im("im"),
        TC_kd("kd"), TC_le("le"), TC_md("md"), TC_me("me"), TC_ml("ml"), TC_mr("mr"), TC_mu("mu"), TC_nd("nd"),
        TC_nw("nw"), TC_rc("rc"), TC_rs("rs"), TC_sc("sc"), TC_se("se"), TC_sf("sf"), TC_so("so"), TC_sr("sr"),
        TC_ta("ta"), TC_te("te"), TC_ti("ti"), TC_ue("ue"), TC_up("up"), TC_us("us");

        private String cap;

        private ScreenEventType(String cap) {
            this.cap = cap;
        }

        public String getCap() {
            return cap;
        }
    }
    
    /**
     * 
     */
    public static class DecoratedCharacter {
        private char value;
        private int fontStyle = Font.PLAIN;
        private boolean reverse = false;
        private boolean underline = false;
        private Color foreColor;
        private Color backColor;
        
        public static DecoratedCharacter[] newArray(int size) {
            DecoratedCharacter[] array = new DecoratedCharacter[size];
            for (int i = 0; i < size; i++) {
                array[i] = new DecoratedCharacter((char)0);
            }
            return array;
        }

        public DecoratedCharacter(char ch) {
            this.value = ch;
        }
        public char getValue() {
            return value;
        }
        public void setFontStyle(int style) {
            this.fontStyle = style;
        }
        public int getFontStyle() {
            return fontStyle;
        }
    }
}
