/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package me.superblaubeere27.jobf.utils;

/**
 * @author Markus Jarderot (https://stackoverflow.com/questions/198431/how-do-you-compare-two-version-strings-in-java)
 */
public class VersionTokenizer
{
    private final String _versionString;
    private final int _length;

    private int _position;
    private int _number;
    private String _suffix;
    private boolean _hasValue;

    public VersionTokenizer(String versionString)
    {
        if (versionString == null)
            throw new IllegalArgumentException("versionString is null");

        this._versionString = versionString;
        this._length = versionString.length();
    }

    public int getNumber()
    {
        return this._number;
    }

    public String getSuffix()
    {
        return this._suffix;
    }

    public boolean hasValue()
    {
        return this._hasValue;
    }

    public boolean MoveNext()
    {
        this._number = 0;
        this._suffix = "";
        this._hasValue = false;

        // No more characters
        if (this._position >= this._length)
            return false;

        this._hasValue = true;

        while (this._position < this._length)
        {
            char c = this._versionString.charAt(this._position);
            if (c < '0' || c > '9') break;
            this._number = this._number * 10 + (c - '0');
            this._position++;
        }

        int suffixStart = this._position;

        while (this._position < this._length)
        {
            char c = this._versionString.charAt(this._position);
            if (c == '.') break;
            this._position++;
        }

        this._suffix = this._versionString.substring(suffixStart, this._position);

        if (this._position < this._length) this._position++;

        return true;
    }
}
