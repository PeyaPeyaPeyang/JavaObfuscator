/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 * Copyright (c) 2023      Peyang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package tokyo.peya.obfuscator.processor.optimiser;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import tokyo.peya.obfuscator.IClassTransformer;
import tokyo.peya.obfuscator.ProcessorCallback;
import tokyo.peya.obfuscator.annotations.ObfuscationTransformer;
import tokyo.peya.obfuscator.configuration.DeprecationLevel;
import tokyo.peya.obfuscator.configuration.ValueManager;
import tokyo.peya.obfuscator.configuration.values.BooleanValue;
import tokyo.peya.obfuscator.configuration.values.EnabledValue;

public class Optimiser implements IClassTransformer
{
    private static final String PROCESSOR_NAME = "optimiser";

    private static final EnabledValue V_ENABLED = new EnabledValue(PROCESSOR_NAME,  "ui.transformers.optimiser", DeprecationLevel.AVAILABLE, false);

    private static final BooleanValue V_REPLACE_EQUALS = new BooleanValue(PROCESSOR_NAME, "replace_string_equals", "ui.transformers.optimiser.replace_equals", DeprecationLevel.SOME_DEPRECATION, false);
    private static final BooleanValue V_EQUALS_IGNORE_CASE = new BooleanValue(PROCESSOR_NAME, "replace_string_equals_ignore_case", "ui.transformers.optimiser.replace_equals_ic", DeprecationLevel.SOME_DEPRECATION, false);
    private static final BooleanValue V_OPTIMIZE_STATIC_STRING_CALLS = new BooleanValue(PROCESSOR_NAME, "optimise_static_string_calls",  "ui.transformers.optimiser.replace_static_calls", DeprecationLevel.AVAILABLE, false);

    static
    {
        ValueManager.registerOwner(PROCESSOR_NAME, "ui.transformers.optimiser");
        ValueManager.registerClass(Optimiser.class);
    }

    @Override
    public void process(ProcessorCallback callback, ClassNode node)
    {
        if (!V_ENABLED.get())
            return;

        for (MethodNode method : node.methods)
        {
            if (V_REPLACE_EQUALS.get() || V_EQUALS_IGNORE_CASE.get())
                ComparisionReplacer.replaceComparisons(method, V_REPLACE_EQUALS.get(), V_EQUALS_IGNORE_CASE.get());
            if (V_OPTIMIZE_STATIC_STRING_CALLS.get())
                StaticStringCallOptimiser.optimise(method);
        }
    }

    @Override
    public ObfuscationTransformer getType()
    {
        return ObfuscationTransformer.PEEPHOLE_OPTIMIZER;
    }

}
