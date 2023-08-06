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

package tokyo.peya.obfuscator.processors.optimizer;

import tokyo.peya.obfuscator.IClassTransformer;
import tokyo.peya.obfuscator.ProcessorCallback;
import tokyo.peya.obfuscator.annotations.ObfuscationTransformer;
import tokyo.peya.obfuscator.utils.values.BooleanValue;
import tokyo.peya.obfuscator.utils.values.DeprecationLevel;
import tokyo.peya.obfuscator.utils.values.EnabledValue;
import tokyo.peya.obfuscator.utils.values.ValueManager;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class Optimizer implements IClassTransformer
{
    private static final String PROCESSOR_NAME = "Optimizer";

    private static final EnabledValue V_ENABLED = new EnabledValue(PROCESSOR_NAME, DeprecationLevel.OK, false);

    private static final BooleanValue V_REPLACE_EQUALS = new BooleanValue(PROCESSOR_NAME, "Replace String.equals()", "NOT TESTED", DeprecationLevel.OK, false);
    private static final BooleanValue V_EQUALS_IGNORE_CASE = new BooleanValue(PROCESSOR_NAME, "Replace String.equalsIgnoreCase()", "Might break some comparisons with strings that contains unicode chars", DeprecationLevel.OK, false);
    private static final BooleanValue V_OPTIMIZE_STATIC_STRING_CALLS = new BooleanValue(PROCESSOR_NAME, "Optimize static string calls", null, DeprecationLevel.GOOD, false);

    static
    {
        ValueManager.registerClass(Optimizer.class);
    }

    @Override
    public void process(ProcessorCallback callback, ClassNode node)
    {
        if (!V_ENABLED.get()) return;

        for (MethodNode method : node.methods)
        {
            if (V_REPLACE_EQUALS.get() || V_EQUALS_IGNORE_CASE.get())
                ComparisionReplacer.replaceComparisons(method, V_REPLACE_EQUALS.get(), V_EQUALS_IGNORE_CASE.get());
            if (V_OPTIMIZE_STATIC_STRING_CALLS.get())
                StaticStringCallOptimizer.optimize(method);
        }
    }

    @Override
    public ObfuscationTransformer getType()
    {
        return ObfuscationTransformer.PEEPHOLE_OPTIMIZER;
    }

}
