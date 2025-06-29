package tokyo.peya.obfuscator.processor.naming.entrypoint;

import tokyo.peya.obfuscator.clazz.ClassReference;

public class DelegateBukkitPluginDescription extends AbstractYamlLikeTextDelegate
{
    public DelegateBukkitPluginDescription()
    {
        super("main");
    }

    @Override
    public boolean canProvideMainClass(String entryName)
    {
        return entryName.equals("plugin.yml") || entryName.equals("plugin.yaml");
    }
}
