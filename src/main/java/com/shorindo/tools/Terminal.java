package com.shorindo.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Terminal implements Runnable {
	private static final Logger LOG = Logger.getLogger(Terminal.class);
	private PipedOutputStream screenOutput;
	private InputStreamReader screenReader;
	private Thread th;
	private char[][] buffer;
	private int rows;
	private int cols;
	private int cr, cc;
	
	public static void main(String[] args) {
		final Terminal t = new Terminal("UTF-8", 30, 3);
		LOG.info("start");
		new Thread() {
			public void run() {
				int c;
				try {
					while ((c = System.in.read()) > 0) {
						t.getOutputStream().write(c);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}.start();
	}
	
	public Terminal(String charset, int cols, int rows) {
		this.rows = rows;
		this.cols = cols;
		this.buffer = new char[rows][cols];
		try {
			screenOutput = new PipedOutputStream();
			PipedInputStream screenInput = new PipedInputStream(screenOutput);
			screenReader = new InputStreamReader(screenInput, charset);
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
		th = new Thread(this);
		th.start();
	}

	@Override
	public void run() {
		int c;
		try {
			while ((c = screenReader.read()) != -1) {
				switch (c) {
				case 0x1b: state1(); break;
				case '\r': cmd_cr(); break;
				case '\n': cmd_do(); break;
				default: put((char)c);
				}
			}
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		}
	}
	
	public void close() {
		try {
			if (screenOutput != null)
				screenOutput.close();
		} catch (IOException e) {
			LOG.error(e.getLocalizedMessage(), e);
		}
	}
	
	public OutputStream getOutputStream() {
		return screenOutput;
	}
	
	public InputStream getInputStream() {
		return null;
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
		int w = getWidth(c);
		if (cc + w <= cols) {
			buffer[cr][cc] = c;
			cc += w;
		} else if (cr < rows - 1) {
			cr += 1;
			buffer[cr][0] = c;
			cc = w;
		} else {
			cmd_cs(1, cr + 1);
			buffer[rows - 1] = new char[cols];
			cc = 0;
			put(c);
		}
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
		if (start < 1) start = 1;
		if (end > rows - 1) end = rows - 1;
		for (int row = start - 1; row < end; row++) {
			buffer[row] = buffer[row + 1];
		}
	}

    /** タブの消去 */
	protected void cmd_ct() {
	}


	/** 一文字削除する */
	protected void cmd_dc() {
	}

    /** 一行削除する */
	protected void cmd_dl() {
	}


	/** カーソルを一行下げる */
	protected void cmd_do() {
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

	/** ESC */
	private void state1() throws IOException {
		int c = screenReader.read();
		switch (c) {
		case -1: close(); break;
		case '[': state2(); break;
		default:
		}
	}
	
	/** ESC '[' */
	private void state2() throws IOException {
		int c = screenReader.read();
		switch (c) {
		case '2': state6(); break;
		case '3': state7(); break;
		case '4': state8(); break;
		case 'H': cmd_ho(); break;
		case 'J': cmd_cd(); break;
		case 'K': cmd_ce(); break;
		case 'M': cmd_dl(); break;
		case 'P': cmd_dc(); break;
		default:
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
		switch (c) {
		case 'J': cmd_cl(); break;
		default:
		}		
	}
	
	/** ESC '[' '3' */
	private void state7() throws IOException {
		int c = screenReader.read();
		switch (c) {
		case 'g': cmd_ct(); break;
		default:
		}
	}

	/** ESC '[' '4' */
	private void state8() throws IOException {
		int c = screenReader.read();
		switch (c) {
		case 'h': cmd_im(); break;
		case 'l': cmd_ei(); break;
		default:
		}
	}
}
