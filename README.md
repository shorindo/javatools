VirtualTerminal

termcap
https://manpages.debian.org/stretch/manpages-ja/termcap.5.ja.html
```
*6=\EOF     select キー
@7=\EOF     end キー
F1=\E[23~   ファンクションキー f11 が送出する文字列
F2=\E[24~   ファンクションキー f12 が送出する文字列
K2=\EOE     キーパッドの中央キー
Km=\E[M
k1=\EOP     ファンクションキー 1
k2=\EOQ     ファンクションキー 2
k3=\EOR     ファンクションキー 3
k4=\EOS     ファンクションキー 4
k5=\E[15~   ファンクションキー 5
k6=\E[17~   ファンクションキー 6
k7=\E[18~   ファンクションキー 7
k8=\E[19~   ファンクションキー 8
k9=\E[20~   ファンクションキー 9
k;=\E[21~   ファンクションキー 10
kB=\E[Z     back tab キー
kH=\EOF     cursor hown down キー
kI=\E[2~    文字挿入キー/insert モードキー
kN=\E[6~    次のページへ移動するキー
kP=\E[5~    前のページへ移動するキー
kd=\EOB     下カーソルキー
kh=\EOH     home キー
kl=\EOD     左カーソルキー
kr=\EOC     右カーソルキー
ku=\EOA     上カーソルキー
am          自動マージン。自動的に行を折り返す
bs          コントロール H (キーコード 8) をバックスペースとして扱う
km          端末にはメタキーがある
mi          挿入モードでもカーソル移動ができる
ms          強調/下線モードでもカーソル移動ができる
ut
xn          改行/折り返しに不具合がある
AX
Co#8
co#80       端末の行数=80
kn#12
li#24       行数=24
pa#64
AB=\E[4%dm
AF=\E[3%dm
AL=\E[%dL       %1 行挿入する
DC=\E[%dP       %1 文字削除する
DL=\E[%dM       %1 行削除する
DO=\E[%dB       カーソルを #1 行下げる
LE=\E[%dD       カーソルを左 %1 文字分移動する
RI=\E[%dC       カーソルを右へ %1 文字分移動する
UP=\E[%dA       カーソルを %1 行分上に移動
ae=\E(B         代替文字セットの終り
al=\E[L         1 行挿入する
as=\E(0         図形文字集合に対する、代替文字セットの開始
bl=^G           (音声の) ベルを鳴らす
cd=\E[J         画面の最後までをクリア
ce=\E[K         行の最後までをクリア
cl=\E[H\E[2J    画面を消去し、カーソルをホームポジションへ
cm=\E[%i%d;%dH  画面上の %1 行、 %2 桁へカーソルを移動
cs=\E[%i%d;%dr  %1 行目から %2 行目までの範囲をスクロールする
ct=\E[3g        タブの消去
dc=\E[P         一文字削除する
dl=\E[M         一行削除する
ei=\E[4l        intert モード終了
ho=\E[H         カーソルをホームポジションに移動
im=\E[4h        insert モード開始
is=\E[!p\E[?3;4l\E[4l\E>\E]104^G
kD=\E[3~        カーソル位置の文字を消すキー
ke=\E[?1l\E>    キーパッドをオフにする
ks=\E[?1h\E=
le=^H           カーソルを左へ一文字分移動する
md=\E[1m        bold モード開始
me=\E[m         so, us, mb, md, mr などのモード全てを終了する
ml=\El
mr=\E[7m        反転モード開始
mu=\Em      
nd=\E[C         カーソルを右に一文字分移動
op=\E[39;49m
rc=\E8          保存しておいたカーソル位置に復帰する
rs=\E[!p\E[?3;4l\E[4l\E>\E]104^G
sc=\E7          カーソル位置を保存する
se=\E[27m       強調モード終了
sf=^J           順方向の 1 行スクロール
so=\E[7m        強調モード開始
sr=\EM          逆スクロール
st=\EH          全ての行において、現在の桁位置をタブストップに設定する
te=\E[?1049l    カーソル移動を用いるプログラムの終了
ti=\E[?1049h    カーソル移動を用いるプログラムの開始
ue=\E[24m       下線モード終了
up=\E[A         カーソルを 1 行分上に移動
us=\E[4m        下線モード開始
ve=\E[?12l\E[?25h   カーソルを通常の明るさにする
vi=\E[?25l      カーソルを見えなくする
vs=\E[?12;25h   強調カーソル
kb=^H           バックスペースキー

```
