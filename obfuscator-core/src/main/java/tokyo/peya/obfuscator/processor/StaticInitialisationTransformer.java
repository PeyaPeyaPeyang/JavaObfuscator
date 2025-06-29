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

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import tokyo.peya.obfuscator.IClassTransformer;
import tokyo.peya.obfuscator.Obfuscator;
import tokyo.peya.obfuscator.ProcessorCallback;
import tokyo.peya.obfuscator.annotations.ObfuscationTransformer;
import tokyo.peya.obfuscator.configuration.DeprecationLevel;
import tokyo.peya.obfuscator.configuration.ValueManager;
import tokyo.peya.obfuscator.configuration.values.EnabledValue;
import tokyo.peya.obfuscator.utils.NodeUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class StaticInitialisationTransformer implements IClassTransformer
{
    private static final String PROCESSOR_NAME = "static_initialisation";

    private static final Random random = new Random();
    private static final EnabledValue V_ENABLED = new EnabledValue(
            PROCESSOR_NAME,
            "ui.transformers.flow_obfuscator.static_init.description",
            DeprecationLevel.AVAILABLE,
            true
    );
    private final Obfuscator inst;

    static
    {
        ValueManager.registerOwner(PROCESSOR_NAME, "ui.transformers.flow_obfuscator.static_init");
        ValueManager.registerClass(StaticInitialisationTransformer.class);
    }

    public StaticInitialisationTransformer(Obfuscator inst)
    {
        this.inst = inst;
    }

    @Override
    public void process(ProcessorCallback callback, ClassNode node)
    {
        if (!V_ENABLED.get())
            return;

        HashMap<FieldNode, Object> objs = new HashMap<>();
        for (FieldNode field : node.fields)
        {
            if (field.value != null)
            {
                if ((field.access & Opcodes.ACC_STATIC) != 0 && (field.value instanceof String || field.value instanceof Integer))
                {
                    objs.put(field, field.value);
                    field.value = null;
                }
            }
        }
        InsnList toAdd = new InsnList();
        for (Map.Entry<FieldNode, Object> fieldNodeObjectEntry : objs.entrySet())
        {
            if (fieldNodeObjectEntry.getValue() instanceof String)
                toAdd.add(new LdcInsnNode(fieldNodeObjectEntry.getValue()));
            if (fieldNodeObjectEntry.getValue() instanceof Integer)
                toAdd.add(NodeUtils.generateIntPush((Integer) fieldNodeObjectEntry.getValue()));
            toAdd.add(new FieldInsnNode(
                    Opcodes.PUTSTATIC,
                    node.name,
                    fieldNodeObjectEntry.getKey().name,
                    fieldNodeObjectEntry.getKey().desc
            ));
        }
        MethodNode clInit = NodeUtils.getMethod(node, "<clinit>");
        if (clInit == null)
        {
            clInit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, new String[0]);
            node.methods.add(clInit);
        }

        if (clInit.instructions == null || clInit.instructions.getFirst() == null)
        {
            clInit.instructions = toAdd;
            clInit.instructions.add(new InsnNode(Opcodes.RETURN));
        }
        else
            clInit.instructions.insertBefore(clInit.instructions.getFirst(), toAdd);
    }

    @Override
    public ObfuscationTransformer getType()
    {
        return null;
    }

}
