/*
 * Copyright (c) 2023      Peyang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package tokyo.peya.obfuscator.utils;

import java.util.Random;

public class NameUtils
{
    /**
     * By ItzSomebody
     */
    private static final char[] DICT_SPACES = {
            '\u2000', '\u2001', '\u2002', '\u2003', '\u2004', '\u2005', '\u2006', '\u2007', '\u2008', '\u2009', '\u200A', '\u200B', '\u200C', '\u200D', '\u200E', '\u200F'
    };
    private static final Random RANDOM = new Random();

    @SuppressWarnings("SameParameterValue")
    private static int randInt(int min, int max)
    {
        return RANDOM.nextInt(max - min) + min;
    }

    public static String generateSpaceString(int length)
    {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < length; i++)
            stringBuilder.append(" ");
        return stringBuilder.toString();
    }

    /**
     * @param len Length of the string to generate.
     * @return a built {@link String} consisting of DICT_SPACES.
     * @author ItzSomebody
     * Generates a {@link String} consisting only of DICT_SPACES.
     * Stole this idea from NeonObf and Smoke.
     */
    public static String crazyString(int len)
    {
        char[] buildString = new char[len];
        for (int i = 0; i < len; i++)
            buildString[i] = DICT_SPACES[RANDOM.nextInt(DICT_SPACES.length)];
        return new String(buildString);
    }

    public static String unicodeString(int length)
    {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < length; i++)
            stringBuilder.append((char) randInt(128, 250));
        return stringBuilder.toString();
    }

    public static String trimPackageName(String in)
    {
        int lin = in.lastIndexOf('/');

        if (lin == 0)
            throw new IllegalArgumentException("Illegal class name");

        return lin == -1 ? "": in.substring(0, lin);
    }
}
