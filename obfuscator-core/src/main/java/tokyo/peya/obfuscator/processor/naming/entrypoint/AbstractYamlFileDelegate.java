package tokyo.peya.obfuscator.processor.naming.entrypoint;

import java.util.regex.Pattern;

public abstract class AbstractYamlFileDelegate extends AbstractRegexBasedTextDelegate
{
    public AbstractYamlFileDelegate(String keyName)
    {
        super(Pattern.compile("(?m)^" + Pattern.quote(keyName) + "\\s*:\\s*(.+)$"), 1);
    }
}
