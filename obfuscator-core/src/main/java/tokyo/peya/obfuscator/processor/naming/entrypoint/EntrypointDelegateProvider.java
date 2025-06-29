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

    public EntrypointDelegate getOptimalDelegateFor(String entryName, byte[] entryData)
    {
        for (EntrypointDelegate delegate : this.delegates)
            if (delegate.canProvideMainClass(entryName, entryData))
                return delegate;

        return null;
    }

    public void enableDelegateAuto(@NotNull String entryName, byte[] entryData)
    {
        // Delegate が検出できた場合は，enabledDelegates に追加する
        EntrypointDelegate delegate = this.getOptimalDelegateFor(entryName, entryData);
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
