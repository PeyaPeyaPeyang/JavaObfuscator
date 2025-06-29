/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 * Copyright (c) 2023-2025 Peyang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package tokyo.peya.obfuscator.processor;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import tokyo.peya.obfuscator.IClassTransformer;
import tokyo.peya.obfuscator.ProcessorCallback;
import tokyo.peya.obfuscator.annotations.ObfuscationTransformer;
import tokyo.peya.obfuscator.configuration.DeprecationLevel;
import tokyo.peya.obfuscator.configuration.ValueManager;
import tokyo.peya.obfuscator.configuration.values.BooleanValue;
import tokyo.peya.obfuscator.configuration.values.EnabledValue;
import tokyo.peya.obfuscator.utils.NameUtils;

import java.lang.reflect.Modifier;
import java.util.ArrayList;

public class DecompilerCrasher implements IClassTransformer
{
    private static final String PROCESSOR_NAME = "decompiler_crasher";

    private static final String EMPTY_STRINGS;
    private static final EnabledValue V_ENABLED = new EnabledValue(
            PROCESSOR_NAME,
            "ui.transformers.decompiler_crasher.description",
            DeprecationLevel.AVAILABLE,
            false
    );
    private static final BooleanValue V_INVALID_SIGNATURES = new BooleanValue(
            PROCESSOR_NAME,
            "invalid_signatures",
            "ui.transformers.decompiler_crasher.invalid_signatures",
            DeprecationLevel.AVAILABLE,
            true
    );
    private static final BooleanValue V_EMPTY_ANNOTATION = new BooleanValue(
            PROCESSOR_NAME,
            "empty_annotation_spam",
            "ui.transformers.decompiler_crasher.empty_annotation_spam",
            DeprecationLevel.AVAILABLE,
            true
    );

    static
    {
        EMPTY_STRINGS = NameUtils.crazyString(16378);
    }

    static
    {
        ValueManager.registerOwner(PROCESSOR_NAME, "ui.transformers.decompiler_crasher");
        ValueManager.registerClass(DecompilerCrasher.class);
    }

    @Override
    public void process(ProcessorCallback callback, ClassNode node)
    {
        if (Modifier.isInterface(node.access))
            return;
        if (!V_ENABLED.get())
            return;

        if (V_INVALID_SIGNATURES.get())
            if (node.signature == null)  // By ItzSomebody
                node.signature = NameUtils.crazyString(10);

        if (V_EMPTY_ANNOTATION.get())
        {
            node.methods.forEach(method -> {
                if (method.invisibleAnnotations == null)
                    method.invisibleAnnotations = new ArrayList<>();

                for (int i = 0; i < 50; i++)
                    method.invisibleAnnotations.add(new AnnotationNode(EMPTY_STRINGS));
            });
        }

    }

    @Override
    public ObfuscationTransformer getType()
    {
        return ObfuscationTransformer.CRASHER;
    }


}
