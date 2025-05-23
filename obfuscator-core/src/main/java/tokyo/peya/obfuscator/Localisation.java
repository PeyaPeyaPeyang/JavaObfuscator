package tokyo.peya.obfuscator;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public class Localisation
{
    private static ResourceBundle bundle;

    public static void setLocale(Locale locale)
    {
        bundle = ResourceBundle.getBundle("langs/messages", locale);
    }

    static {
        System.out.println(Locale.getDefault());
        setLocale(Locale.getDefault());
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
