package tokyo.peya.obfuscator.processor.naming.entrypoint;

public class DelegateBukkitPluginDescription extends AbstractYamlFileDelegate
{
    public DelegateBukkitPluginDescription()
    {
        super("main");
    }

    @Override
    public boolean canProvideMainClass(String entryName, String content)
    {
        return (entryName.equals("plugin.yml") || entryName.equals("plugin.yaml"))
                && super.canProvideMainClass(entryName, content);
    }
}
