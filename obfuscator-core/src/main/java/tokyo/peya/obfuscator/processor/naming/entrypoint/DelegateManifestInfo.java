package tokyo.peya.obfuscator.processor.naming.entrypoint;

import tokyo.peya.obfuscator.clazz.ClassReference;

public class DelegateManifestInfo extends AbstractYamlLikeTextDelegate
{
    public DelegateManifestInfo()
    {
        super("Main-Class");
    }

    @Override
    public boolean canProvideMainClass(String entry)
    {
        return entry.equals("META-INF/MANIFEST.MF") || entry.equals("META-INF/MANIFEST.mf");
    }
}
