/*
 * Copyright 2019 Shorindo, Inc.
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

import java.util.Arrays;

/**
 * 
 */
public class VirtualTerminal {
    private static final Logger LOG = Logger.getLogger(VirtualTerminal.class);
    private VirtualCharacter[] vchars;
    private int rowSize = 0;
    private int colSize = 0;
    private int curRow = 0;
    private int curCol = 0;

    /**
     * 
     * @param rows
     * @param cols
     */
    public VirtualTerminal(int rows, int cols) {
        resize(rows, cols);
    }

    /**
     * TODO
     * @param rows
     * @param cols
     */
    public void resize(int rows, int cols) {
        rowSize = rows;
        colSize = cols;
        vchars = new VirtualCharacter[rows * cols];
    }

    /**
     * 画面を消去し、カーソルをホームポジションに移動する。
     */
    public void clear() {
        Arrays.fill(vchars, null);
        curRow = 0;
        curCol = 0;
    }

    /**
     * 
     * @param amount
     */
    public void scroll(int amount) {
        VirtualCharacter[] dest = new VirtualCharacter[rowSize * colSize];
        if (amount > 0) {
            System.arraycopy(vchars, amount * colSize, dest, 0, (rowSize - amount) * colSize);
            vchars = dest;
        } else if (amount < 0) {
            System.arraycopy(vchars, 0, dest, -amount * colSize, (rowSize + amount) * colSize);
        }
        vchars = dest;
    }

    /**
     * 
     * @param row
     * @param col
     * @return
     */
    public VirtualCharacter moveTo(int row, int col) {
        if (col >= colSize) {
            col = colSize - 1;
        }
        if (row >= rowSize) {
            row = rowSize - 1;
        }
        this.curRow = row;
        this.curCol = col;
        return vchars[row + colSize + col];
    }

    /**
     * カーソル位置に文字をセットし、カーソル位置を１文字進める。
     * カーソル位置が行の最後になったときは折り返して次の先頭となる。
     * カーソル位置が最終行になったときは画面を１行スクロールアップする。
     * 
     * @param ch 挿入文字
     */
    public void addChar(Character ch) {
        vchars[curRow * colSize + curCol] = new VirtualCharacter(ch);
        curCol = curCol + 1;
        if (curCol >= colSize) {
            curCol = 0;
            curRow = curRow + 1;
        }
        if (curRow >= rowSize) {
            scroll(1);
            curRow = rowSize - 1;
        }
        //LOG.debug("[" + row + "," + col + "]");
    }

    /**
     * 指定したカーソル位置の文字を取得する。
     * 
     * @param row
     * @param col
     * @return
     */
    public VirtualCharacter getChar(int row, int col) {
        if (0 <= row && row < rowSize && 0 <= col && col < colSize) {
            return vchars[row * colSize + col];
        } else {
            LOG.warn("Invalid position : getChar(" + row + "," + col + ")");
            return null;
        }
    }
}
