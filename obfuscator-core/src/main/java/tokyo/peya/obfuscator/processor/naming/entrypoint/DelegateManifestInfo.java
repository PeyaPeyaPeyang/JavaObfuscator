package tokyo.peya.obfuscator.processor.naming.entrypoint;

public class DelegateManifestInfo extends AbstractYamlFileDelegate
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
