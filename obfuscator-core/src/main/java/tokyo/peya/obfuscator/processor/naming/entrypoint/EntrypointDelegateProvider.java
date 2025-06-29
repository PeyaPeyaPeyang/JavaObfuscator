package tokyo.peya.obfuscator.processor.naming.entrypoint;

import org.codehaus.commons.nullanalysis.NotNull;
import tokyo.peya.obfuscator.clazz.ClassReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntrypointDelegateProvider
{
    private final List<EntrypointDelegate> delegates;
    private final Map<String, EntrypointDelegate> enabledDelegates;

    public EntrypointDelegateProvider()
    {
        this.delegates = new ArrayList<>();
        this.enabledDelegates = new HashMap<>();
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

        return null;
    }

    public void enableDelegateAuto(@NotNull String entryName)
    {
        // Delegate が検出できた場合は，enabledDelegates に追加する
        EntrypointDelegate delegate = this.getOptimalDelegateFor(entryName);
        if (delegate != null)
            this.enabledDelegates.put(entryName, delegate);
    }

    public boolean isDelegateEnabled(@NotNull String entryName)
    {
        return this.enabledDelegates.containsKey(entryName);
    }

    public byte[] renameMainClass(@NotNull String entryName, @NotNull ClassReference renamedClassReference, @NotNull byte[] data)
    {
        EntrypointDelegate delegate = this.enabledDelegates.get(entryName);
        if (delegate == null)
            throw new IllegalStateException("No enabled EntrypointDelegate found for entry: " + entryName);

        return delegate.renameMainClass(entryName, renamedClassReference, data);
    }
}
