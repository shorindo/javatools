/*
 * Copyright 2020 Shorindo, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.shorindo.tools;

import static com.shorindo.tools.Terminfo.InfoTypes.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.shorindo.tools.PEGCombinator.PEGContext;
import com.shorindo.tools.PEGCombinator.PEGException;
import com.shorindo.tools.PEGCombinator.PEGNode;
import com.shorindo.tools.PEGCombinator.RuleTypes;

/**
 * terminfoソースを解析する
 */
public class Terminfo {
    private static final Logger LOG = Logger.getLogger(Terminfo.class);
    public static final int PARAM_I  = 0x1FFFF;
    public static final int PARAM_P1 = 0x2FFFF;
    public static final int PARAM_P2 = 0x3FFFF;
    public static final int PARAM_P3 = 0x4FFFF;
    public static final int PARAM_P4 = 0x5FFFF;
    public static final int PARAM_P5 = 0x6FFFF;
    public static final int PARAM_P6 = 0x7FFFF;
    public static final int PARAM_P7 = 0x8FFFF;
    public static final int PARAM_P8 = 0x9FFFF;
    public static final int PARAM_P9 = 0xA2FFFF;
    public static final int PARAM_D  = 0xBFFFF;

    private static PEGCombinator PEG = new PEGCombinator();
    static {
        PEG.define(TERMINFO, PEG.rule$ZeroOrMore(
            PEG.rule(LINE)))
            .action($$ -> {
                for (int i = 0; i < $$.get(0).length(); i++) {
                    PEGNode $i = $$.get(0).get(i).get(0).get(0);
                    if ($i.getType() == ENTRY) {
                        return $i;
                    }
                }
                return $$;
            });

        PEG.define(LINE, PEG.rule$Choice(
            PEG.rule(COMMENT),
            PEG.rule(ENTRY),
            PEG.rule(EMPTY)));

        PEG.define(COMMENT, PEG.rule$Sequence(
            PEG.rule$RegExp("#[^\n]*"),
            PEG.rule(EOL_OR_EOF)));

        PEG.define(EMPTY, PEG.rule(EOL));

        PEG.define(ENTRY,
            PEG.rule(VARIABLES),
            PEG.rule$ZeroOrMore(
                PEG.rule(DELIM),
                PEG.rule(CAPABILITY))
                .action($$ -> {
                    List<Object> childList = new ArrayList<>();
                    for (int i = 0; i < $$.length(); i++) {
                        PEGNode $i = $$.get(i).get(1);
                        childList.add($i.getValue());
                    }
                    $$.setValue(childList);
                    return $$;
                }),
            PEG.rule$Optional(PEG.rule(DELIM)))
            .action($$ -> {
                String name = $$.get(0).get(0).get(0).getValue().toString();
                Terminfo info = new Terminfo(name);
                for (Capability cap : (List<Capability>)$$.get(1).getValue()) {
                    info.addCapability(cap);
                }
                $$.setValue(info);
                return $$;
            });

        PEG.define(VARIABLES,
            PEG.rule(NAME),
            PEG.rule$ZeroOrMore(
                PEG.rule$Literal("|"),
                PEG.rule(ALIAS)));

        PEG.define(NAME, PEG.rule$RegExp("[a-zA-Z0-9\\-]+"));

        PEG.define(ALIAS, PEG.rule$RegExp("[^,]+"));

        PEG.define(DELIM, PEG.rule$RegExp("\\s*,\\s*"));

        PEG.define(CAPABILITY, PEG.rule$Choice(
            PEG.rule(NUM_CAP),
            PEG.rule(STR_CAP),
            PEG.rule(BOOL_CAP)))
            .action($$ -> {
                $$.setValue($$.get(0).getValue());
                return $$;
            });

        PEG.define(CAP_NAME, PEG.rule$RegExp("\\.?[a-zA-Z0-9]+"))
            .action($$ -> {
                $$.setValue($$.get(0).getValue());
                return $$;
            });
        
        // ブール値
        PEG.define(BOOL_CAP, PEG.rule(CAP_NAME))
            .action($$ -> {
                String capName = $$.get(0).getValue().toString();
                $$.setValue(new Capability(CapTypes.of(capName), true));
                return $$;
            });
        
        PEG.define(NUM_CAP, PEG.rule(CAP_NAME),
            PEG.rule$Literal("#"),
            PEG.rule(NUMBER))
            .action($$ -> {
                String capName = $$.get(0).getValue().toString();
                int capValue = Integer.parseInt($$.get(2).getValue().toString());
                $$.setValue(new Capability(CapTypes.of(capName), capValue));
                return $$;
            });

        PEG.define(STR_CAP, PEG.rule(CAP_NAME),
            PEG.rule$Literal("="),
            PEG.rule(CAP_VALUE))
            .action($$ -> {
                String capName = $$.get(0).getValue().toString();
                Object capValue = $$.get(2).get(0).getValue();
                $$.setValue(new Capability(CapTypes.of(capName), capValue));
                return $$;
            });

        PEG.define(CAP_VALUE, PEG.rule$OneOrMore(
            PEG.rule$Choice(
                PEG.rule(ESCAPED),
                PEG.rule(CTRL),
                PEG.rule(PARAM),
                PEG.rule$RegExp("[^,]")
                    .action($$ -> {
                        $$.setValue(
                            new Integer(((String)$$.getValue()).charAt(0)));
                        return $$;
                    })))
            .action($$ -> {
                List<Object> data = new ArrayList<>();
                for (int i = 0; i < $$.length(); i++) {
                    PEGNode $i = $$.get(i).get(0);
                    data.add($i.getValue());
                }
                $$.setValue(data);
                return $$;
            }));

        // \200/\e/\E/\n/\l/\r/\t/\b/\f/\s/\^/\\/\,/\:/\0
        PEG.define(ESCAPED, PEG.rule$RegExp("\\\\([0-7]{3,3}|[eEnlrtbfs^\\,:0])"))
            .action($$ -> {
                switch ($$.get(0).getValue().toString()) {
                case "\\e":
                case "\\E": $$.setValue(0x1b); break;
                case "\\n":
                case "\\l": $$.setValue((int)'\n'); break;
                case "\\r": $$.setValue((int)'\r'); break;
                case "\\t": $$.setValue((int)'\t'); break;
                case "\\b": $$.setValue((int)'\b'); break;
                case "\\f": $$.setValue((int)'\f'); break;
                case "\\s": $$.setValue((int)' '); break;
                case "\\^": $$.setValue((int)'^'); break;
                case "\\\\": $$.setValue((int)'\\'); break;
                case "\\,": $$.setValue((int)','); break;
                case "\\:": $$.setValue((int)':'); break;
                case "\\0": $$.setValue((int)'\200'); break;
                default: $$.setValue(
                    Integer.parseInt($$.getValue().toString().substring(1), 8));
                    break;
                }
                return $$;
            });

        // ^x
        PEG.define(CTRL, PEG.rule$RegExp("\\^[a-zA-Z]"))
            .action($$ -> {
                char c = $$.get(0).getValue().toString().charAt(1);
                $$.setValue(new Integer((char)(c - 64)));
                return $$;
            });

        // FIXME %%/%c/%s/%p[1-9]/%P[a-z]/%g[a-z]/%P[A-Z]/%g[A-Z/%'c'/%{nn}/%l/%+/%-/%*/%//%m/%&/%|%^/%=/%>/%</%A/%O/%!/%~/%i...
        PEG.define(PARAM, PEG.rule$RegExp("%([%cdis]|p[1-9])"))
            .action($$ -> {
                int p = 0;
                switch ((String)$$.get(0).getValue()) {
                case "%i":  p = PARAM_I; break;
                case "%d":  p = PARAM_D; break;
                case "%p1": p = PARAM_P1; break;
                case "%p2": p = PARAM_P2; break;
                case "%p3": p = PARAM_P3; break;
                case "%p4": p = PARAM_P4; break;
                case "%p5": p = PARAM_P5; break;
                case "%p6": p = PARAM_P6; break;
                case "%p7": p = PARAM_P7; break;
                case "%p8": p = PARAM_P8; break;
                case "%p9": p = PARAM_P9; break;
                default:
                    LOG.error("unknown parameter = " + $$.get(0).getValue());
                }
                $$.setValue(p);
                return $$;
            });

        // 123/0177/0x1b
        PEG.define(NUMBER, PEG.rule$Choice(
            // 10進数
            PEG.rule$RegExp("[1-9][0-9]*")
                .action($$ -> {
                    $$.setValue(Integer.parseInt($$.getValue().toString()));
                    return $$;
                }),
            // 8進数
            PEG.rule$RegExp("0[0-7]+")
                .action($$ -> {
                    $$.setValue(Integer.parseInt($$.getValue().toString(), 8));
                    return $$;
                }),
            // 16進数
            PEG.rule$RegExp("0[xX][0-9a-fA-F]+")
                .action($$ -> {
                    $$.setValue(Integer.parseInt($$.getValue().toString(), 16));
                    return $$;
                })))
            .action($$ -> {
                return $$.get(0);
            });

        PEG.define(EOL, PEG.rule$RegExp("\r?\n"));
        PEG.define(EOF, PEG.rule$Not(
            PEG.rule$Any()));
        PEG.define(EOL_OR_EOF, PEG.rule$Choice(
            PEG.rule(EOL),
            PEG.rule(EOF)));
    }

    public static enum InfoTypes implements RuleTypes {
        TERMINFO, LINE, COMMENT, ENTRY, EMPTY,
        VARIABLES, NAME, ALIAS, CAPABILITY, CAP_NAME, BOOL_CAP, NUM_CAP, STR_CAP,
        CAP_VALUE, NUMBER, ESCAPED, CTRL, PARAM,
        DELIM, EOL_OR_EOF, EOL, EOF;
    }

    public static Terminfo compile(String source) throws TerminfoException {
        PEGContext ctx = PEG.createContext(source);
        try {
            return (Terminfo)PEG.rule(TERMINFO).accept(ctx).getValue();
        } catch (PEGException e) {
            LOG.error("failed", e);
        }
        if (ctx.available() > 0) {
            throw new TerminfoException(ctx.subString(ctx.position()));
        }
        return null;
    }

    private String name;
    private List<String> aliases;
    private String description;
    private List<Capability> capabilities;

    private Terminfo(String name) {
        this.name = name;
        this.capabilities = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    private void addCapability(Capability cap) {
        capabilities.add(cap);
    }

    public List<Capability> getCapabilities() {
        return Collections.unmodifiableList(capabilities);
    }

    public static class Capability {
        private CapTypes type;
        private Object data;

        public Capability(CapTypes type, Object data) {
            this.type = type;
            this.data = data;
        }

        public CapTypes getType() {
            return type;
        }

        public Object getData() {
            return data;
        }
    }

    public enum CapTypes {
        // ブール値
        /** auto_left_margin/cub1 は桁 0 から最後の桁に回り込む */
        CAP_bw("bw", "bw"),
        /** auto_right_margin/自動マージン機能を持つ */
        CAP_am("am", "am"),
        /** back_color_erase/画面を背景色で消去する */
        CAP_bce("ut", "bce"),
        /** can_change/既存の色を再定義可能 */
        CAP_ccc("cc", "ccc"),
        /** ceol_standout_glitch/強調出力は上書きで消去されない (hp) */
        CAP_xhp("xs", "xhp"),
        /** col_addr_glitch/hpa/mhpa では正方向のみ移動可能 */
        CAP_xhpa("YA", "xhpa"),
        /** cpi_changes_res/文字ピッチを変えると解像度が変わる */
        CAP_cpix("YF", "cpix"),
        /** cr_cancels_micro_mode/cr を使用するとマイクロモードがオフになる */
        CAP_crxm("YB", "crxm"),
        /** dest_tabs_magic_smso/タブは破壊的、強調文字の動作が奇妙 (t1061) */
        CAP_xt("xt", "xt"),
        /** eat_newline_glitch/80 桁より後の改行は無視される (concept) */
        CAP_xenl("xn", "xenl"),
        /** erase_overstrike/空白の重ね打ちで消去可能 */
        CAP_eo("eo", "eo"),
        /** generic_type/一般的な回線タイプ */
        CAP_gn("gn", "gn"),
        /** hard_copy/ハードコピー端末 */
        CAP_hc("hc", "hc"),
        /** hard_cursor/カーソルが見にくい */
        CAP_chts("HC", "chts"),
        /** has_meta_key/メタキーを持つ (押すと第 8 ビットがセットされる) */
        CAP_km("km", "km"),
        /** has_print_wheel/文字セットを変更するのに人手が必要なプリンタ */
        CAP_daisy("YC", "daisy"),
        /** has_status_line/特別なステータス行を持つ */
        CAP_hs("hs", "hs"),
        /** hue_lightness_saturation/端末は HLS 色表記のみ使用 (Tektronix) */
        CAP_hls("hl", "hls"),
        /** insert_null_glitch/挿入モードでヌル文字を区別する */
        CAP_in("in", "in"),
        /** lpi_changes_res/行ピッチを変えると解像度が変わる */
        CAP_lpix("YG", "lpix"),
        /** memory_above/画面より上の描画が保持される */
        CAP_da("da", "da"),
        /** memory_below/画面より下の描画が保持される */
        CAP_db("db", "db"),
        /** move_insert_mode/挿入モードで安全にカーソル移動可能 */
        CAP_mir("mi", "mir"),
        /** move_standout_mode/強調モードで安全にカーソル移動可能 */
        CAP_msgr("ms", "msgr"),
        /** needs_xon_xoff/パディング機能が働かない。xon/xoff 制御が必要 */
        CAP_nxon("nx", "nxon"),
        /** no_esc_ctlc/beehive 端末 (f1=escape, f2=ctrl C) */
        CAP_xsb("xb", "xsb"),
        /** no_pad_char/パディング文字が存在しない */
        CAP_npc("NP", "npc"),
        /** non_dest_scroll_region/スクロール領域が非破壊的 */
        CAP_ndscr("ND", "ndscr"),
        /** non_rev_rmcup/smcup は rmcup の動作を反転しない */
        CAP_nrrmc("NR", "nrrmc"),
        /** over_strike/端末は重ね打ち可能 */
        CAP_os("os", "os"),
        /** prtr_silent/プリンタ出力は画面にエコーしない */
        CAP_mc5i("5i", "mc5i"),
        /** row_addr_glitch/vpa/mvpa では正方向にのみ移動可能 */
        CAP_xvpa("YD", "xvpa"),
        /** semi_auto_right_margin/最終桁で出力すると cr 動作をする */
        CAP_sam("YE", "sam"),
        /** status_line_esc_ok/ステータス行上でエスケープ可能 */
        CAP_eslok("es", "eslok"),
        /** tilde_glitch/~ 表示不可 (hazeltine 端末) */
        CAP_hz("hz", "hz"),
        /** transparent_underline/下線付文字は上書きする */
        CAP_ul("ul", "ul"),
        /** xon_xoff/端末は xon/xoff ハンドシェークを行う */
        CAP_xon("xo", "xon"),

        // 数値ケーパビリティ
        /** columns/1 行の桁数 */
        CAP_cols("co", "cols"),
        /** init_tabs/タブの初期設定は # 個の空白毎 */
        CAP_it("it", "it"),
        /** label_height/ラベル 1 つの行数 */
        CAP_lh("lh", "lh"),
        /** label_width/ラベル 1 つの桁数 */
        CAP_lw("lw", "lw"),
        /** lines/画面またはページの行数 */
        CAP_lines("li", "lines"),
        /** lines_of_memory/lines の値以上の場合メモリの行数。0 は行数が可変であることを表す */
        CAP_lm("lm", "lm"),
        /** magic_cookie_glitch/smso や rmso で画面に残る空白数 */
        CAP_xmc("sg", "xmc"),
        /** max_attributes/端末が組み合わせて処理できる属性数の最大値 */
        CAP_ma("ma", "ma"),
        /** max_colors/画面上の最大色数 */
        CAP_colors("Co", "colors"),
        /** max_pairs/画面上の色ペアの最大値 */
        CAP_pairs("pa", "pairs"),
        /** maximum_windows/定義可能なウィンドウ数の最大値 */
        CAP_wnum("MW", "wnum"),
        /** no_color_video/色付きでは使用できないビデオ属性 */
        CAP_ncv("NC", "ncv"),
        /** num_labels/画面上のラベル数 */
        CAP_nlab("Nl", "nlab"),
        /** padding_baud_rate/パディングが必要な最低ボーレート */
        CAP_pb("pb", "pb"),
        /** virtual_terminal/仮想画面番号 (CB/unix) */
        CAP_vt("vt", "vt"),
        /** width_status_line/ステータス行の桁数 */
        CAP_wsl("ws", "wsl"),

        // 文字列ケーパビリティ
        /** acs_chars/図形文字セットの組、vt100 に基づく */
        CAP_acsc("ac", "acsc"),
        /** back_tab/後退タブ (P) */
        CAP_cbt("bt", "cbt"),
        /** bell/可聴シグナル (ベル) (P) */
        CAP_bel("bl", "bel"),
        /** carriage_return/復帰文字 (P*) (P*) */
        CAP_cr("cr", "cr"),
        /** change_char_pitch/1 インチあたりの文字数を # 1 に変更 */
        CAP_cpi("ZA", "cpi"),
        /** change_line_pitch/1 インチあたりの行数を #1 に変更 */
        CAP_lpi("ZB", "lpi"),
        /** change_res_horz/水平解像度を #1 に変更 */
        CAP_chr("ZC", "chr"),
        /** change_res_vert/垂直解像度を #1 に変更 */
        CAP_cvr("ZD", "cvr"),
        /** change_scroll_region/領域を行 #1 から行 #2 までに変更 (P) */
        CAP_csr("cs", "csr"),
        /** char_padding/ip と同様だが挿入モード以外で使用 */
        CAP_rmp("rP", "rmp"),
        /** clear_all_tabs/タブ設定をすべてクリア (P) */
        CAP_tbc("ct", "tbc"),
        /** clear_margins/左右のソフトマージンをクリア */
        CAP_mgc("MC", "mgc"),
        /** clear_screen/画面をクリアし、カーソルをホームに移動 (P*) */
        CAP_clear("cl", "clear"),
        /** clr_bol/行先頭までクリア */
        CAP_el1("cb", "el1"),
        /** clr_eol/行末までクリア (P) */
        CAP_el("ce", "el"),
        /** clr_eos/画面末までクリア (P*) */
        CAP_ed("cd", "ed"),
        /** column_address/水平絶対位置 #1 (P) */
        CAP_hpa("ch", "hpa"),
        /** command_character/端末がプロトタイプで設定可能なコマンド文字 !? */
        CAP_cmdch("CC", "cmdch"),
        /** create_window/ウィンドウ #1 を #2,#3 から #4,#5 までと定義 */
        CAP_cwin("CW", "cwin"),
        /** cursor_address/行 #1 桁 #2 に移動 */
        CAP_cup("cm", "cup"),
        /** cursor_down/1 行下に移動 */
        CAP_cud1("do", "cud1"),
        /** cursor_home/カーソルをホームに移動 (cup がない場合) */
        CAP_home("ho", "home"),
        /** cursor_invisible/カーソルを見えなくする */
        CAP_civis("vi", "civis"),
        /** cursor_left/カーソルを 1 空白分左に移動 */
        CAP_cub1("le", "cub1"),
        /** cursor_mem_address/メモリ相対のカーソル位置指定であり、行 #1 列 #2 へ移動 */
        CAP_mrcup("CM", "mrcup"),
        /** cursor_normal/カーソルを通常表示にする (civis/cvvis を元に戻す) */
        CAP_cnorm("ve", "cnorm"),
        /** cursor_right/非破壊空白 (1 空白分右に移動) */
        CAP_cuf1("nd", "cuf1"),
        /** cursor_to_ll/最終行の最初の桁 (cup がない場合) */
        CAP_ll("ll", "ll"),
        /** cursor_up/1 行上へ */
        CAP_cuu1("up", "cuu1"),
        /** cursor_visible/カーソルをより見えるようにする */
        CAP_cvvis("vs", "cvvis"),
        /** define_char/文字 #1 を幅 #2 ドット、ディセンダ #3 で定義する */
        CAP_defc("ZE", "defc"),
        /** delete_character/文字を削除 (P*) */
        CAP_dch1("dc", "dch1"),
        /** delete_line/行を削除 (P*) */
        CAP_dl1("dl", "dl1"),
        /** dial_phone/番号 #1 にダイアルする */
        CAP_dial("DI", "dial"),
        /** dis_status_line/ステータス行を無効にする */
        CAP_dsl("ds", "dsl"),
        /** display_clock/時計を表示する */
        CAP_dclk("DK", "dclk"),
        /** down_half_line/半行下へ */
        CAP_hd("hd", "hd"),
        /** ena_acs/別の文字セットを有効にする */
        CAP_enacs("eA", "enacs"),
        /** enter_alt_charset_mode/別の文字セットを開始 (P) */
        CAP_smacs("as", "smacs"),
        /** enter_am_mode/自動マージンオン */
        CAP_smam("SA", "smam"),
        /** enter_blink_mode/点滅モードオン */
        CAP_blink("mb", "blink"),
        /** enter_bold_mode/太字 (更に明るい) モードオン */
        CAP_bold("md", "bold"),
        /** enter_ca_mode/cup を用いたプログラムを開始する文字列 */
        CAP_smcup("ti", "smcup"),
        /** enter_delete_mode/削除モード開始 */
        CAP_smdc("dm", "smdc"),
        /** enter_dim_mode/半輝度モード開始 */
        CAP_dim("mh", "dim"),
        /** enter_doublewide_mode/倍幅モード開始 */
        CAP_swidm("ZF", "swidm"),
        /** enter_draft_quality/ドラフト印字モード開始 */
        CAP_sdrfq("ZG", "sdrfq"),
        /** enter_insert_mode/挿入モード開始 */
        CAP_smir("im", "smir"),
        /** enter_italics_mode/斜体モード開始 */
        CAP_sitm("ZH", "sitm"),
        /** enter_leftward_mode/左向き移動モード開始 */
        CAP_slm("ZI", "slm"),
        /** enter_micro_mode/マイクロ移動モード開始 */
        CAP_smicm("ZJ", "smicm"),
        /** enter_near_letter_quality/NLQ 印字モード開始 */
        CAP_snlq("ZK", "snlq"),
        /** enter_normal_quality/通常品質印字モード開始 */
        CAP_snrmq("ZL", "snrmq"),
        /** enter_protected_mode/保護モードオン */
        CAP_prot("mp", "prot"),
        /** enter_reverse_mode/反転表示モードオン */
        CAP_rev("mr", "rev"),
        /** enter_secure_mode/ブランクモードオン (文字が見えない) */
        CAP_invis("mk", "invis"),
        /** enter_shadow_mode/シャドウプリントモード開始 */
        CAP_sshm("ZM", "sshm"),
        /** enter_standout_mode/強調モード開始 */
        CAP_smso("so", "smso"),
        /** enter_subscript_mode/下付き文字モード開始 */
        CAP_ssubm("ZN", "ssubm"),
        /** enter_superscript_mode/上付き文字モード開始 */
        CAP_ssupm("ZO", "ssupm"),
        /** enter_underline_mode/下線モード開始 */
        CAP_smul("us", "smul"),
        /** enter_upward_mode/上向き移動モード開始 */
        CAP_sum("ZP", "sum"),
        /** enter_xon_mode/xon/xoff ハンドシェークオン */
        CAP_smxon("SX", "smxon"),
        /** erase_chars/#1 個の文字を消去 (P) */
        CAP_ech("ec", "ech"),
        /** exit_alt_charset_mode/別の文字セット終了 (P) */
        CAP_rmacs("ae", "rmacs"),
        /** exit_am_mode/自動マージンオフ */
        CAP_rmam("RA", "rmam"),
        /** exit_attribute_mode/全属性オフ */
        CAP_sgr0("me", "sgr0"),
        /** exit_ca_mode/cup を用いたプログラムを終了する文字列 */
        CAP_rmcup("te", "rmcup"),
        /** exit_delete_mode/削除モード終了 */
        CAP_rmdc("ed", "rmdc"),
        /** exit_doublewide_mode/倍幅モード終了 */
        CAP_rwidm("ZQ", "rwidm"),
        /** exit_insert_mode/挿入モード終了 */
        CAP_rmir("ei", "rmir"),
        /** exit_italics_mode/斜体モード終了 */
        CAP_ritm("ZR", "ritm"),
        /** exit_leftward_mode/左向き移動モード終了 */
        CAP_rlm("ZS", "rlm"),
        /** exit_micro_mode/マイクロ移動モード終了 */
        CAP_rmicm("ZT", "rmicm"),
        /** exit_shadow_mode/シャドウプリントモード終了 */
        CAP_rshm("ZU", "rshm"),
        /** exit_standout_mode/強調モード終了 */
        CAP_rmso("se", "rmso"),
        /** exit_subscript_mode/下付き文字モード終了 */
        CAP_rsubm("ZV", "rsubm"),
        /** exit_superscript_mode/上付き文字モード終了 */
        CAP_rsupm("ZW", "rsupm"),
        /** exit_underline_mode/下線モード終了 */
        CAP_rmul("ue", "rmul"),
        /** exit_upward_mode/逆向き移動モード終了 */
        CAP_rum("ZX", "rum"),
        /** exit_xon_mode/xon/xoff ハンドシェークオフ */
        CAP_rmxon("RX", "rmxon"),
        /** fixed_pause/2-3 秒待つ */
        CAP_pause("PA", "pause"),
        /** flash_hook/スイッチフックをフラッシュ */
        CAP_hook("fh", "hook"),
        /** flash_screen/可視ベル (カーソルは移動しない) */
        CAP_flash("vb", "flash"),
        /** form_feed/ハードコピー端末でのページ排出 (P*) */
        CAP_ff("ff", "ff"),
        /** from_status_line/ステータス行からの復帰 */
        CAP_fsl("fs", "fsl"),
        /** goto_window/ウィンドウ #1 に移動 */
        CAP_wingo("WG", "wingo"),
        /** hangup/電話を切る */
        CAP_hup("HU", "hup"),
        /** init_1string/初期化文字列 */
        CAP_is1("i1", "is1"),
        /** init_2string/初期化文字列 */
        CAP_is2("is", "is2"),
        /** init_3string/初期化文字列 */
        CAP_is3("i3", "is3"),
        /** init_file/初期化ファイルの名前 */
        CAP_if("if", "if"),
        /** init_prog/初期化プログラムのパス名 */
        CAP_iprog("iP", "iprog"),
        /** initialize_color/色 #1 を (#2,#3,#4) に初期化 */
        CAP_initc("Ic", "initc"),
        /** initialize_pair/色ペア #1 を fg=(#2,#3,#4), bg=(#5,#6,#7) に初期化 */
        CAP_initp("Ip", "initp"),
        /** insert_character/文字の挿入 (P) */
        CAP_ich1("ic", "ich1"),
        /** insert_line/行の挿入 (P*) */
        CAP_il1("al", "il1"),
        /** insert_padding/文字挿入の後にパディングを挿入 */
        CAP_ip("ip", "ip"),
        /** key_a1/キーパッドの左上キー */
        CAP_ka1("K1", "ka1"),
        /** key_a3/キーパッドの右上キー */
        CAP_ka3("K3", "ka3"),
        /** key_b2/キーパッドの中央キー */
        CAP_kb2("K2", "kb2"),
        /** key_backspace/backspace キー */
        CAP_kbs("kb", "kbs"),
        /** key_beg/begin キー */
        CAP_kbeg("@1", "kbeg"),
        /** key_btab/back-tab キー */
        CAP_kcbt("kB", "kcbt"),
        /** key_c1/キーパッドの左下キー */
        CAP_kc1("K4", "kc1"),
        /** key_c3/キーパッドの右下キー */
        CAP_kc3("K5", "kc3"),
        /** key_cancel/cancel キー */
        CAP_kcan("@2", "kcan"),
        /** key_catab/clear-all-tabs キー */
        CAP_ktbc("ka", "ktbc"),
        /** key_clear/clear-screen キー、または erase キー */
        CAP_kclr("kC", "kclr"),
        /** key_close/close キー */
        CAP_kclo("@3", "kclo"),
        /** key_command/command キー */
        CAP_kcmd("@4", "kcmd"),
        /** key_copy/copy キー */
        CAP_kcpy("@5", "kcpy"),
        /** key_create/create キー */
        CAP_kcrt("@6", "kcrt"),
        /** key_ctab/clear-tab キー */
        CAP_kctab("kt", "kctab"),
        /** key_dc/delete-character キー */
        CAP_kdch1("kD", "kdch1"),
        /** key_dl/delete-line キー */
        CAP_kdl1("kL", "kdl1"),
        /** key_down/down-arrow キー */
        CAP_kcud1("kd", "kcud1"),
        /** key_eic/挿入モードで rmir や smir が送出するデータ */
        CAP_krmir("kM", "krmir"),
        /** key_end/end キー */
        CAP_kend("@7", "kend"),
        /** key_enter/enter/send キー */
        CAP_kent("@8", "kent"),
        /** key_eol/clear-to-end-of-line キー */
        CAP_kel("kE", "kel"),
        /** key_eos/clear-to-end-of-screen キー */
        CAP_ked("kS", "ked"),
        /** key_exit/exit キー */
        CAP_kext("@9", "kext"),
        /** key_f0/F0 ファンクションキー */
        CAP_kf0("k0", "kf0"),
        /** key_f1/F1 ファンクションキー */
        CAP_kf1("k1", "kf1"),
        /** key_f10/F10 ファンクションキー */
        CAP_kf10("k;", "kf10"),
        /** key_f11/F11 ファンクションキー */
        CAP_kf11("F1", "kf11"),
        /** key_f12/F12 ファンクションキー */
        CAP_kf12("F2", "kf12"),
        /** key_f13/F13 ファンクションキー */
        CAP_kf13("F3", "kf13"),
        /** key_f14/F14 ファンクションキー */
        CAP_kf14("F4", "kf14"),
        /** key_f15/F15 ファンクションキー */
        CAP_kf15("F5", "kf15"),
        /** key_f16/F16 ファンクションキー */
        CAP_kf16("F6", "kf16"),
        /** key_f17/F17 ファンクションキー */
        CAP_kf17("F7", "kf17"),
        /** key_f18/F18 ファンクションキー */
        CAP_kf18("F8", "kf18"),
        /** key_f19/F19 ファンクションキー */
        CAP_kf19("F9", "kf19"),
        /** key_f2/F2 ファンクションキー */
        CAP_kf2("k2", "kf2"),
        /** key_f20/F20 ファンクションキー */
        CAP_kf20("FA", "kf20"),
        /** key_f21/F21 ファンクションキー */
        CAP_kf21("FB", "kf21"),
        /** key_f22/F22 ファンクションキー */
        CAP_kf22("FC", "kf22"),
        /** key_f23/F23 ファンクションキー */
        CAP_kf23("FD", "kf23"),
        /** key_f24/F24 ファンクションキー */
        CAP_kf24("FE", "kf24"),
        /** key_f25/F25 ファンクションキー */
        CAP_kf25("FF", "kf25"),
        /** key_f26/F26 ファンクションキー */
        CAP_kf26("FG", "kf26"),
        /** key_f27/F27 ファンクションキー */
        CAP_kf27("FH", "kf27"),
        /** key_f28/F28 ファンクションキー */
        CAP_kf28("FI", "kf28"),
        /** key_f29/F29 ファンクションキー */
        CAP_kf29("FJ", "kf29"),
        /** key_f3/F3 ファンクションキー */
        CAP_kf3("k3", "kf3"),
        /** key_f30/F30 ファンクションキー */
        CAP_kf30("FK", "kf30"),
        /** key_f31/F31 ファンクションキー */
        CAP_kf31("FL", "kf31"),
        /** key_f32/F32 ファンクションキー */
        CAP_kf32("FM", "kf32"),
        /** key_f33/F33 ファンクションキー */
        CAP_kf33("FN", "kf33"),
        /** key_f34/F34 ファンクションキー */
        CAP_kf34("FO", "kf34"),
        /** key_f35/F35 ファンクションキー */
        CAP_kf35("FP", "kf35"),
        /** key_f36/F36 ファンクションキー */
        CAP_kf36("FQ", "kf36"),
        /** key_f37/F37 ファンクションキー */
        CAP_kf37("FR", "kf37"),
        /** key_f38/F38 ファンクションキー */
        CAP_kf38("FS", "kf38"),
        /** key_f39/F39 ファンクションキー */
        CAP_kf39("FT", "kf39"),
        /** key_f4/F4 ファンクションキー */
        CAP_kf4("k4", "kf4"),
        /** key_f40/F40 ファンクションキー */
        CAP_kf40("FU", "kf40"),
        /** key_f41/F41 ファンクションキー */
        CAP_kf41("FV", "kf41"),
        /** key_f42/F42 ファンクションキー */
        CAP_kf42("FW", "kf42"),
        /** key_f43/F43 ファンクションキー */
        CAP_kf43("FX", "kf43"),
        /** key_f44/F44 ファンクションキー */
        CAP_kf44("FY", "kf44"),
        /** key_f45/F45 ファンクションキー */
        CAP_kf45("FZ", "kf45"),
        /** key_f46/F46 ファンクションキー */
        CAP_kf46("Fa", "kf46"),
        /** key_f47/F47 ファンクションキー */
        CAP_kf47("Fb", "kf47"),
        /** key_f48/F48 ファンクションキー */
        CAP_kf48("Fc", "kf48"),
        /** key_f49/F49 ファンクションキー */
        CAP_kf49("Fd", "kf49"),
        /** key_f5/F5 ファンクションキー */
        CAP_kf5("k5", "kf5"),
        /** key_f50/F50 ファンクションキー */
        CAP_kf50("Fe", "kf50"),
        /** key_f51/F51 ファンクションキー */
        CAP_kf51("Ff", "kf51"),
        /** key_f52/F52 ファンクションキー */
        CAP_kf52("Fg", "kf52"),
        /** key_f53/F53 ファンクションキー */
        CAP_kf53("Fh", "kf53"),
        /** key_f54/F54 ファンクションキー */
        CAP_kf54("Fi", "kf54"),
        /** key_f55/F55 ファンクションキー */
        CAP_kf55("Fj", "kf55"),
        /** key_f56/F56 ファンクションキー */
        CAP_kf56("Fk", "kf56"),
        /** key_f57/F57 ファンクションキー */
        CAP_kf57("Fl", "kf57"),
        /** key_f58/F58 ファンクションキー */
        CAP_kf58("Fm", "kf58"),
        /** key_f59/F59 ファンクションキー */
        CAP_kf59("Fn", "kf59"),
        /** key_f6/F6 ファンクションキー */
        CAP_kf6("k6", "kf6"),
        /** key_f60/F60 ファンクションキー */
        CAP_kf60("Fo", "kf60"),
        /** key_f61/F61 ファンクションキー */
        CAP_kf61("Fp", "kf61"),
        /** key_f62/F62 ファンクションキー */
        CAP_kf62("Fq", "kf62"),
        /** key_f63/F63 ファンクションキー */
        CAP_kf63("Fr", "kf63"),
        /** key_f7/F7 ファンクションキー */
        CAP_kf7("k7", "kf7"),
        /** key_f8/F8 ファンクションキー */
        CAP_kf8("k8", "kf8"),
        /** key_f9/F9 ファンクションキー */
        CAP_kf9("k9", "kf9"),
        /** key_find/find キー */
        CAP_kfnd("@0", "kfnd"),
        /** key_help/help キー */
        CAP_khlp("%1", "khlp"),
        /** key_home/home キー */
        CAP_khome("kh", "khome"),
        /** key_ic/insert-character キー */
        CAP_kich1("kI", "kich1"),
        /** key_il/insert-line キー */
        CAP_kil1("kA", "kil1"),
        /** key_left/left-arrow キー */
        CAP_kcub1("kl", "kcub1"),
        /** key_ll/lower-left キー (home down) */
        CAP_kll("kH", "kll"),
        /** key_mark/mark キー */
        CAP_kmrk("%2", "kmrk"),
        /** key_message/message キー */
        CAP_kmsg("%3", "kmsg"),
        /** key_move/move キー */
        CAP_kmov("%4", "kmov"),
        /** key_next/next キー */
        CAP_knxt("%5", "knxt"),
        /** key_npage/next-page キー */
        CAP_knp("kN", "knp"),
        /** key_open/open キー */
        CAP_kopn("%6", "kopn"),
        /** key_options/options キー */
        CAP_kopt("%7", "kopt"),
        /** key_ppage/previous-page キー */
        CAP_kpp("kP", "kpp"),
        /** key_previous/previous キー */
        CAP_kprv("%8", "kprv"),
        /** key_print/print キー */
        CAP_kprt("%9", "kprt"),
        /** key_redo/redo キー */
        CAP_krdo("%0", "krdo"),
        /** key_reference/reference キー */
        CAP_kref("&1", "kref"),
        /** key_refresh/refresh キー */
        CAP_krfr("&2", "krfr"),
        /** key_replace/replace キー */
        CAP_krpl("&3", "krpl"),
        /** key_restart/restart キー */
        CAP_krst("&4", "krst"),
        /** key_resume/resume キー */
        CAP_kres("&5", "kres"),
        /** key_right/right-arrow キー */
        CAP_kcuf1("kr", "kcuf1"),
        /** key_save/save キー */
        CAP_ksav("&6", "ksav"),
        /** key_sbeg/シフト状態の begin キー */
        CAP_kBEG("&9", "kBEG"),
        /** key_scancel/シフト状態の cancel キー */
        CAP_kCAN("&0", "kCAN"),
        /** key_scommand/シフト状態の command キー */
        CAP_kCMD("*1", "kCMD"),
        /** key_scopy/シフト状態の copy キー */
        CAP_kCPY("*2", "kCPY"),
        /** key_screate/シフト状態の create キー */
        CAP_kCRT("*3", "kCRT"),
        /** key_sdc/シフト状態の delete-character キー */
        CAP_kDC("*4", "kDC"),
        /** key_sdl/シフト状態の delete-line キー */
        CAP_kDL("*5", "kDL"),
        /** key_select/select キー */
        CAP_kslt("*6", "kslt"),
        /** key_send/シフト状態の end キー */
        CAP_kEND("*7", "kEND"),
        /** key_seol/シフト状態の clear-to-end-of-line キー */
        CAP_kEOL("*8", "kEOL"),
        /** key_sexit/シフト状態の exit キー */
        CAP_kEXT("*9", "kEXT"),
        /** key_sf/scroll-forward キー */
        CAP_kind("kF", "kind"),
        /** key_sfind/シフト状態の find キー */
        CAP_kFND("*0", "kFND"),
        /** key_shelp/シフト状態の help キー */
        CAP_kHLP("#1", "kHLP"),
        /** key_shome/シフト状態の home キー */
        CAP_kHOM("#2", "kHOM"),
        /** key_sic/シフト状態の insert-character キー */
        CAP_kIC("#3", "kIC"),
        /** key_sleft/シフト状態の left-arrow キー */
        CAP_kLFT("#4", "kLFT"),
        /** key_smessage/シフト状態の message キー */
        CAP_kMSG("%a", "kMSG"),
        /** key_smove/シフト状態の move キー */
        CAP_kMOV("%b", "kMOV"),
        /** key_snext/シフト状態の next キー */
        CAP_kNXT("%c", "kNXT"),
        /** key_soptions/シフト状態の options キー */
        CAP_kOPT("%d", "kOPT"),
        /** key_sprevious/シフト状態の previous キー */
        CAP_kPRV("%e", "kPRV"),
        /** key_sprint/シフト状態の print キー */
        CAP_kPRT("%f", "kPRT"),
        /** key_sr/scroll-backward キー */
        CAP_kri("kR", "kri"),
        /** key_sredo/シフト状態の redo キー */
        CAP_kRDO("%g", "kRDO"),
        /** key_sreplace/シフト状態の replace キー */
        CAP_kRPL("%h", "kRPL"),
        /** key_sright/シフト状態の right-arrow キー */
        CAP_kRIT("%i", "kRIT"),
        /** key_srsume/シフト状態の resume キー */
        CAP_kRES("%j", "kRES"),
        /** key_ssave/シフト状態の save キー */
        CAP_kSAV("!1", "kSAV"),
        /** key_ssuspend/シフト状態の suspend キー */
        CAP_kSPD("!2", "kSPD"),
        /** key_stab/set-tab キー */
        CAP_khts("kT", "khts"),
        /** key_sundo/シフト状態の undo キー */
        CAP_kUND("!3", "kUND"),
        /** key_suspend/suspend キー */
        CAP_kspd("&7", "kspd"),
        /** key_undo/undo キー */
        CAP_kund("&8", "kund"),
        /** key_up/up-arrow キー */
        CAP_kcuu1("ku", "kcuu1"),
        /** keypad_local/'keyboard_transmit' モードから抜ける */
        CAP_rmkx("ke", "rmkx"),
        /** keypad_xmit/'keyboard_transmit' モードに入る */
        CAP_smkx("ks", "smkx"),
        /** lab_f0/f0 でない場合、ファンクションキー f0 のラベル */
        CAP_lf0("l0", "lf0"),
        /** lab_f1/f1 でない場合、ファンクションキー f1 のラベル */
        CAP_lf1("l1", "lf1"),
        /** lab_f10/f10 でない場合、ファンクションキー f10 のラベル */
        CAP_lf10("la", "lf10"),
        /** lab_f2/f2 でない場合、ファンクションキー f2 のラベル */
        CAP_lf2("l2", "lf2"),
        /** lab_f3/f3 でない場合、ファンクションキー f3 のラベル */
        CAP_lf3("l3", "lf3"),
        /** lab_f4/f4 でない場合、ファンクションキー f4 のラベル */
        CAP_lf4("l4", "lf4"),
        /** lab_f5/f5 でない場合、ファンクションキー f5 のラベル */
        CAP_lf5("l5", "lf5"),
        /** lab_f6/f6 でない場合、ファンクションキー f6 のラベル */
        CAP_lf6("l6", "lf6"),
        /** lab_f7/f7 でない場合、ファンクションキー f7 のラベル */
        CAP_lf7("l7", "lf7"),
        /** lab_f8/f8 でない場合、ファンクションキー f8 のラベル */
        CAP_lf8("l8", "lf8"),
        /** lab_f9/f9 でない場合、ファンクションキー f9 のラベル */
        CAP_lf9("l9", "lf9"),
        /** label_format/ラベルフォーマット */
        CAP_fln("Lf", "fln"),
        /** label_off/ソフトラベルオフ */
        CAP_rmln("LF", "rmln"),
        /** label_on/ソフトラベルオン */
        CAP_smln("LO", "smln"),
        /** meta_off/メタモードオフ */
        CAP_rmm("mo", "rmm"),
        /** meta_on/メタモードオン (8 番目のビットオン) */
        CAP_smm("mm", "smm"),
        /** micro_column_address/マイクロモードの column_address */
        CAP_mhpa("ZY", "mhpa"),
        /** micro_down/マイクロモードの cursor_down */
        CAP_mcud1("ZZ", "mcud1"),
        /** micro_left/マイクロモードの cursor_left */
        CAP_mcub1("Za", "mcub1"),
        /** micro_right/マイクロモードの cursor_right */
        CAP_mcuf1("Zb", "mcuf1"),
        /** micro_row_address/マイクロモードの row_address #1 */
        CAP_mvpa("Zc", "mvpa"),
        /** micro_up/マイクロモードの cursor_up */
        CAP_mcuu1("Zd", "mcuu1"),
        /** newline/改行 (cr の後に lf が来る) */
        CAP_nel("nw", "nel"),
        /** order_of_pins/ソフトウェアビットを印字ヘッドピンに一致させる */
        CAP_porder("Ze", "porder"),
        /** orig_colors/すべての色ペアを本来のものにする */
        CAP_oc("oc", "oc"),
        /** orig_pair/デフォルトのペアを本来の値にする */
        CAP_op("op", "op"),
        /** pad_char/パディング文字 (ヌル以外) */
        CAP_pad("pc", "pad"),
        /** parm_dch/#1 文字を削除 (P*) */
        CAP_dch("DC", "dch"),
        /** parm_delete_line/#1 行を削除 (P*) */
        CAP_dl("DL", "dl"),
        /** parm_down_cursor/#1 行下へ (P*) */
        CAP_cud("DO", "cud"),
        /** parm_down_micro/マイクロモードの parm_down_cursor */
        CAP_mcud("Zf", "mcud"),
        /** parm_ich/#1 文字を挿入 (P*) */
        CAP_ich("IC", "ich"),
        /** parm_index/#1 行の前進スクロール (P) */
        CAP_indn("SF", "indn"),
        /** parm_insert_line/#1 行を挿入 (P*) */
        CAP_il("AL", "il"),
        /** parm_left_cursor/左へ #1 文字分移動 (P) */
        CAP_cub("LE", "cub"),
        /** parm_left_micro/マイクロモードの parm_left_cursor */
        CAP_mcub("Zg", "mcub"),
        /** parm_right_cursor/右へ #1 文字分移動 (P*) */
        CAP_cuf("RI", "cuf"),
        /** parm_right_micro/マイクロモードの parm_right_cursor */
        CAP_mcuf("Zh", "mcuf"),
        /** parm_rindex/#1 行の後退スクロール (P) */
        CAP_rin("SR", "rin"),
        /** parm_up_cursor/#1 行上へ (P*) */
        CAP_cuu("UP", "cuu"),
        /** parm_up_micro/マイクロモードの parm_up_cursor */
        CAP_mcuu("Zi", "mcuu"),
        /** pkey_key/ファンクションキー #1 の打鍵文字列を #2 にする */
        CAP_pfkey("pk", "pfkey"),
        /** pkey_local/ファンクションキー #1 の実行文字列を #2 にする */
        CAP_pfloc("pl", "pfloc"),
        /** pkey_xmit/ファンクションキー #1 の送信文字列を #2 にする */
        CAP_pfx("px", "pfx"),
        /** plab_norm/ラベル #1 に文字列 #2 を表示 */
        CAP_pln("pn", "pln"),
        /** print_screen/画面の内容を印字する */
        CAP_mc0("ps", "mc0"),
        /** prtr_non/#1 バイトだけプリンタをオンにする */
        CAP_mc5p("pO", "mc5p"),
        /** prtr_off/プリンタをオフにする */
        CAP_mc4("pf", "mc4"),
        /** prtr_on/プリンタをオンにする */
        CAP_mc5("po", "mc5"),
        /** pulse/パルスダイアルを選択 */
        CAP_pulse("PU", "pulse"),
        /** quick_dial/確認なしで電話番号 #1 にダイアルする */
        CAP_qdial("QD", "qdial"),
        /** remove_clock/時計を削除 */
        CAP_rmclk("RC", "rmclk"),
        /** repeat_char/文字 #1 を #2 回繰り返す (P*) */
        CAP_rep("rp", "rep"),
        /** req_for_input/(pty 用に) 次の入力文字を送る */
        CAP_rfi("RF", "rfi"),
        /** reset_1string/リセット文字列 */
        CAP_rs1("r1", "rs1"),
        /** reset_2string/リセット文字列 */
        CAP_rs2("r2", "rs2"),
        /** reset_3string/リセット文字列 */
        CAP_rs3("r3", "rs3"),
        /** reset_file/リセットファイルの名前 */
        CAP_rf("rf", "rf"),
        /** restore_cursor/最後の save_cursor の位置にカーソルを戻す */
        CAP_rc("rc", "rc"),
        /** row_address/垂直絶対位置 #1 (P) */
        CAP_vpa("cv", "vpa"),
        /** save_cursor/現在のカーソル位置を保存 (P) */
        CAP_sc("sc", "sc"),
        /** scroll_forward/テキストを上にスクロール (P) */
        CAP_ind("sf", "ind"),
        /** scroll_reverse/テキストを下にスクロール (P) */
        CAP_ri("sr", "ri"),
        /** select_char_set/文字セット #1 の選択 */
        CAP_scs("Zj", "scs"),
        /** set_attributes/ビデオ属性を #1-#9 に定義 (PG9) */
        CAP_sgr("sa", "sgr"),
        /** set_background/背景色を #1 に設定 */
        CAP_setb("Sb", "setb"),
        /** set_bottom_margin/下マージンを現在行に設定 */
        CAP_smgb("Zk", "smgb"),
        /** set_bottom_margin_parm/下マージンを #1 行目か (smgtp が与えられていなければ) 下から #2 行目にする */
        CAP_smgbp("Zl", "smgbp"),
        /** set_clock/時計を #1 時 #2 分 #3 秒に設定 */
        CAP_sclk("SC", "sclk"),
        /** set_color_pair/現在の色ペアを #1 に設定 */
        CAP_scp("sp", "scp"),
        /** set_foreground/前景色を #1 に設定 */
        CAP_setf("Sf", "setf"),
        /** set_left_margin/左ソフトマージンを現在桁に設定 smgl を参照 (ML は BSD の termcap とは違います) */
        CAP_smgl("ML", "smgl"),
        /** set_left_margin_parm/左 (右) マージンを桁 #1 に設定 */
        CAP_smglp("Zm", "smglp"),
        /** set_right_margin/右ソフトマージンを現在桁に設定 */
        CAP_smgr("MR", "smgr"),
        /** set_right_margin_parm/右マージンを桁 #1 に設定 */
        CAP_smgrp("Zn", "smgrp"),
        /** set_tab/全行のタブを現在の桁に設定 */
        CAP_hts("st", "hts"),
        /** set_top_margin/上マージンを現在行に設定 */
        CAP_smgt("Zo", "smgt"),
        /** set_top_margin_parm/上 (下) マージンを行 #1 に設定 */
        CAP_smgtp("Zp", "smgtp"),
        /** set_window/現在のウィンドウを行 #1-#2、桁 #3-#4 とする */
        CAP_wind("wi", "wind"),
        /** start_bit_image/ビットイメージグラフィック印字の開始 */
        CAP_sbim("Zq", "sbim"),
        /** start_char_set_def/#2 個の文字からなる文字セット #1 の定義の開始 */
        CAP_scsd("Zr", "scsd"),
        /** stop_bit_image/ビットイメージグラフィック印字の終了 */
        CAP_rbim("Zs", "rbim"),
        /** stop_char_set_def/文字セット #1 の定義の終了 */
        CAP_rcsd("Zt", "rcsd"),
        /** subscript_characters/下付き文字となりうる文字のリスト */
        CAP_subcs("Zu", "subcs"),
        /** superscript_characters/上付き文字となりうる文字のリスト */
        CAP_supcs("Zv", "supcs"),
        /** tab/次の 8 文字分のハードウェアタブストップへのタブ文字 */
        CAP_ht("ta", "ht"),
        /** these_cause_cr/これらの文字のうちのいずれかの印字は CR を引き起こす */
        CAP_docr("Zw", "docr"),
        /** to_status_line/ステータス行の列 #1 に移動 */
        CAP_tsl("ts", "tsl"),
        /** tone/タッチトーンダイアルを選択 */
        CAP_tone("TO", "tone"),
        /** underline_char/文字 1 つに下線を付け、次の文字に移動 */
        CAP_uc("uc", "uc"),
        /** up_half_line/半行上へ */
        CAP_hu("hu", "hu"),
        /** user0/ユーザ文字列 #0 */
        CAP_u0("u0", "u0"),
        /** user1/ユーザ文字列 #1 */
        CAP_u1("u1", "u1"),
        /** user2/ユーザ文字列 #2 */
        CAP_u2("u2", "u2"),
        /** user3/ユーザ文字列 #3 */
        CAP_u3("u3", "u3"),
        /** user4/ユーザ文字列 #4 */
        CAP_u4("u4", "u4"),
        /** user5/ユーザ文字列 #5 */
        CAP_u5("u5", "u5"),
        /** user6/ユーザ文字列 #6 */
        CAP_u6("u6", "u6"),
        /** user7/ユーザ文字列 #7 */
        CAP_u7("u7", "u7"),
        /** user8/ユーザ文字列 #8 */
        CAP_u8("u8", "u8"),
        /** user9/ユーザ文字列 #9 */
        CAP_u9("u9", "u9"),
        /** wait_tone/ダイアルトーンを待つ */
        CAP_wait("WA", "wait"),
        /** xoff_character/XOFF 文字 */
        CAP_xoffc("XF", "xoffc"),
        /** xon_character/XON 文字 */
        CAP_xonc("XN", "xonc"),
        /** zero_motion/次の文字表示を移動無しで行う */
        CAP_zerom("Zx", "zerom")
        ;

        private String capCode;
        private String capName;

        private CapTypes(String capCode, String capName) {
            this.capCode = capCode;
            this.capName = capName;
        }
        
        public String getCapCode() {
            return capCode;
        }

        public static CapTypes of(String capName) {
            for (CapTypes type : values()) {
                if (type.capName.equals(capName)) {
                    return type;
                }
            }
            return null;
        }
    }
    
    public static class TerminfoException extends Exception {
        private static final long serialVersionUID = 1L;

        public TerminfoException(String message) {
            super(message);
        }
    }
}
