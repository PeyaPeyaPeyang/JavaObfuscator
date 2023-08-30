/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 * Copyright (c) 2023      Peyang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package tokyo.peya.obfuscator.utils;

import java.util.regex.Pattern;

public class ExcludePattern
{
    public static Pattern compileExcludePattern(String s)
    {
        StringBuilder sb = new StringBuilder();
        // s.replace('.', '/').replace("**", ".*").replace("*", "[^/]*")

        char[] chars = s.toCharArray();

        for (int i = 0; i < chars.length; i++)
        {
            char c = chars[i];

            if (c == '*')
            {
                if (chars.length - 1 != i && chars[i + 1] == '*')
                {
                    sb.append(".*");  // Linux風の Glob に対応
                    i++;
                }
                else
                {
                    sb.append("[^/]*");
                }
            }
            else if (c == '.')
                sb.append('/');  // パッケージ名は . ではなく / で区切る
            else
                sb.append(c);
        }

        return Pattern.compile(sb.toString());
    }
}
