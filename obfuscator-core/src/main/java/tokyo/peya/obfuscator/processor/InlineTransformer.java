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

import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;
import tokyo.peya.obfuscator.IClassTransformer;
import tokyo.peya.obfuscator.Obfuscator;
import tokyo.peya.obfuscator.ProcessorCallback;
import tokyo.peya.obfuscator.annotations.ObfuscationTransformer;
import tokyo.peya.obfuscator.configuration.DeprecationLevel;
import tokyo.peya.obfuscator.configuration.values.EnabledValue;
import tokyo.peya.obfuscator.utils.InliningUtils;
import tokyo.peya.obfuscator.utils.NodeUtils;
import tokyo.peya.obfuscator.utils.Utils;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j(topic = "Processor/InlineTransformer")
public class InlineTransformer implements IClassTransformer
{
    private static final Random random = new Random();

    private static final EnabledValue V_ENABLED = new EnabledValue(
            "Inlining",
            "Doesn't work, please don't use this",
            DeprecationLevel.DEPRECATED,
            false
    );

    private static final List<String> exceptions = new ArrayList<>();
    private final Obfuscator inst;

    static
    {
        // ValueManager.registerClass(InlineTransformer.class);
    }

    public InlineTransformer(Obfuscator inst)
    {
        this.inst = inst;
    }

    @Override
    public void process(ProcessorCallback callback, ClassNode node)
    {
        if (!V_ENABLED.get()) return;

        int maxPasses = 3;

        boolean ok;
        int index = 0;

        boolean found = false;

        do
        {
            ok = false;
            for (MethodNode method : node.methods)
            {
                int maxStackSize = method.maxStack;
                int maxLocals = method.maxLocals;
                method.maxStack = 1337;
                method.maxLocals = 1337;

                Frame<SourceValue>[] frames;
                try
                {
                    frames = new Analyzer<>(new SourceInterpreter()).analyze(node.name, method);
                }
                catch (AnalyzerException e)
                {
                    throw new RuntimeException(e);
                }

                method.maxStack = maxStackSize;
                method.maxLocals = maxLocals;

                HashMap<AbstractInsnNode, InsnList> replacements = new HashMap<>();

                for (AbstractInsnNode abstractInsnNode : method.instructions.toArray())
                {
                    if (abstractInsnNode instanceof MethodInsnNode insnNode)
                    {

                        ClassNode lookupClass = Utils.lookupClass(insnNode.owner);

                        if (lookupClass == null) continue;

                        MethodNode lookupMethod = Utils.getMethod(lookupClass, insnNode.name, insnNode.desc, false);

                        if (lookupMethod == null
//                                || (lookupMethod.instructions.size() > 100)
                                || (Modifier.isFinal(lookupMethod.access) || insnNode.getOpcode() != Opcodes.INVOKESPECIAL) && insnNode.getOpcode() != Opcodes.INVOKESTATIC
                                || !InliningUtils.canInlineMethod(node, lookupClass, lookupMethod))
                            continue;

                        InsnList inline = InliningUtils.inline(
                                lookupMethod,
                                lookupClass,
                                method,
                                frames[method.instructions.indexOf(abstractInsnNode)]
                        );
                        inline.insertBefore(
                                inline.getFirst(),
                                NodeUtils.debugString("--- INLINE (" + lookupClass.name + "." + lookupMethod.name + lookupMethod.desc + ") ---")
                        );
                        inline.add(NodeUtils.debugString("--- END ---"));

//                    System.out.println(NodeUtils.prettyprint(inline));

                        replacements.put(abstractInsnNode, inline);

                        log.info("Inlined method in " + node.name + "." + method.name + method.desc + "(" + lookupClass.name + "." + lookupMethod.name + lookupMethod.desc + ")");

                        ok = true;
                        found = true;
                    }
                }
                for (Map.Entry<AbstractInsnNode, InsnList> abstractInsnNodeInsnListEntry : replacements.entrySet())
                {
                    method.instructions.insert(
                            abstractInsnNodeInsnListEntry.getKey(),
                            abstractInsnNodeInsnListEntry.getValue()
                    );
                    method.instructions.remove(abstractInsnNodeInsnListEntry.getKey());
                }
            }
            index++;
        }
        while (ok && index <= maxPasses);

        if (found) callback.setForceComputeFrames();

//        System.out.println("Inlined " + inlined + " methods.");

    }

    @Override
    public ObfuscationTransformer getType()
    {
        return ObfuscationTransformer.INLINING;
    }


}
