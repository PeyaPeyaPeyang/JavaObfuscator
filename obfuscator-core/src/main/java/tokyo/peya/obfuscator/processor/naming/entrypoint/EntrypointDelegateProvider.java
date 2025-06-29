package tokyo.peya.obfuscator.processor.naming.entrypoint;

import java.util.ArrayList;
import java.util.List;

public class EntrypointDelegateProvider
{
    private final List<EntrypointDelegate> delegates;

    public EntrypointDelegateProvider()
    {
        this.delegates = new ArrayList<>();
        this.initDefaultDelegates();
    }

    private void initDefaultDelegates()
    {
        this.addDelegate(new DelegateManifestInfo());
        this.addDelegate(new DelegateBukkitPluginDescription());
    }

    public void addDelegate(EntrypointDelegate delegate)
    {
        this.delegates.add(delegate);
    }

    public EntrypointDelegate getOptimalDelegateFor(String entryName)
    {
        for (EntrypointDelegate delegate : this.delegates)
            if (delegate.canProvideMainClass(entryName))
                return delegate;

        throw new IllegalStateException("No suitable EntrypointDelegate found for entry: " + entryName);
    }
}
