/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 * Copyright (c) 2025 Peyang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package tokyo.peya.obfuscator;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public class Localisation
{
    private static ResourceBundle bundle;

    static
    {
        setLocale(Locale.getDefault());
    }

    public static void setLocale(Locale locale)
    {
        bundle = ResourceBundle.getBundle("langs/messages", locale);
    }

    public static String get(String key)
    {
        if (bundle == null)
            return key;
        return bundle.getString(key);
    }

    public static boolean has(String key)
    {
        if (bundle == null)
            return false;
        return bundle.containsKey(key);
    }

    public static LanguageAccessor access(String key)
    {
        return new LanguageAccessor(key);
    }

    public static class LanguageAccessor
    {
        private final String key;
        private final Map<String, Object> arguments;

        public LanguageAccessor(String key)
        {
            this.key = key;
            this.arguments = new HashMap<>();
        }

        public LanguageAccessor set(String key, Object value)
        {
            this.arguments.put(key, value);
            return this;
        }

        public String get()
        {
            if (bundle == null)
                return this.key;

            String value = bundle.getString(this.key);
            for (Map.Entry<String, Object> entry : this.arguments.entrySet())
                value = StringUtils.replace(value, "%%" + entry.getKey() + "%%", String.valueOf(entry.getValue()));

            return value;
        }
    }
}
