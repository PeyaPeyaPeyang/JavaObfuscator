/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package me.superblaubeere27.jobf.processors;

import me.superblaubeere27.annotations.ObfuscationTransformer;
import me.superblaubeere27.jobf.IClassTransformer;
import me.superblaubeere27.jobf.JarObfuscator;
import me.superblaubeere27.jobf.ProcessorCallback;
import me.superblaubeere27.jobf.utils.NameUtils;
import me.superblaubeere27.jobf.utils.values.BooleanValue;
import me.superblaubeere27.jobf.utils.values.DeprecationLevel;
import me.superblaubeere27.jobf.utils.values.EnabledValue;
import me.superblaubeere27.jobf.utils.values.StringValue;
import me.superblaubeere27.jobf.utils.values.ValueManager;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class LineNumberRemover implements IClassTransformer
{
    private static final String PROCESSOR_NAME = "LineNumberRemover";
    private static final Random random = new Random();
    private static final ArrayList<String> TYPES = new ArrayList<>();
    private static final EnabledValue V_ENABLED = new EnabledValue(PROCESSOR_NAME, DeprecationLevel.GOOD, true);
    private static final BooleanValue V_RENAME_VALUES = new BooleanValue(PROCESSOR_NAME, "Rename local variables", DeprecationLevel.GOOD, true);
    private static final BooleanValue V_REMOVE_LINE_NUMBERS = new BooleanValue(PROCESSOR_NAME, "Remove Line Numbers", DeprecationLevel.GOOD, true);
    private static final BooleanValue V_REMOVE_DEBUG_NAMES = new BooleanValue(PROCESSOR_NAME, "Remove Debug Names", DeprecationLevel.GOOD, true);
    private static final BooleanValue V_ADD_LOCAL_VARIABLES = new BooleanValue(PROCESSOR_NAME, "Add Local Variables", "Adds random local variables with wrong types. Might break some decompilers", DeprecationLevel.GOOD, true);
    private static final StringValue V_NEW_SOURCE_FILE_NAME = new StringValue(PROCESSOR_NAME, "New SourceFile Name", DeprecationLevel.GOOD, "");

    static
    {
        TYPES.add("Z");
        TYPES.add("C");
        TYPES.add("B");
        TYPES.add("S");
        TYPES.add("I");
        TYPES.add("F");
        TYPES.add("J");
        TYPES.add("D");
        TYPES.add("Ljava/lang/Exception;");
        TYPES.add("Ljava/lang/String;");
    }

    static
    {
        ValueManager.registerClass(LineNumberRemover.class);
    }

    private final JarObfuscator inst;

    public LineNumberRemover(JarObfuscator inst)
    {
        this.inst = inst;
    }

    @Override
    public void process(ProcessorCallback callback, ClassNode node)
    {
        if (!V_ENABLED.get()) return;

        for (MethodNode method : node.methods)
        {
            LabelNode firstLabel = null;
            LabelNode lastLabel = null;
            HashMap<Integer, String> varMap = new HashMap<>();

            for (AbstractInsnNode abstractInsnNode : method.instructions.toArray())
            {
                if (abstractInsnNode instanceof LineNumberNode && V_REMOVE_LINE_NUMBERS.get())
                {
                    LineNumberNode insnNode = (LineNumberNode) abstractInsnNode;
                    method.instructions.remove(insnNode);
                }

                if (abstractInsnNode instanceof VarInsnNode)
                {
                    VarInsnNode insnNode = (VarInsnNode) abstractInsnNode;

                    if (!varMap.containsKey(insnNode.var))
                    {
                        varMap.put(insnNode.var, TYPES.get(random.nextInt(TYPES.size())));
                    }
                }
                if (abstractInsnNode instanceof LabelNode)
                {
                    LabelNode insnNode = (LabelNode) abstractInsnNode;

                    if (firstLabel == null)
                    {
                        firstLabel = insnNode;
                    }

                    lastLabel = insnNode;
                }
            }

            if (firstLabel != null && this.V_ADD_LOCAL_VARIABLES.get())
            {
                if (method.localVariables == null) method.localVariables = new ArrayList<>();

                for (Map.Entry<Integer, String> integerStringEntry : varMap.entrySet())
                {
                    method.localVariables.add(new LocalVariableNode(NameUtils.generateLocalVariableName(), integerStringEntry.getValue(), null, firstLabel, lastLabel, integerStringEntry.getKey()));
                }
            }

            if (method.parameters != null && V_RENAME_VALUES.get())
            {
                for (ParameterNode parameter : method.parameters)
                {
                    parameter.name = NameUtils.generateLocalVariableName();
                }
            }
            if (method.localVariables != null && V_RENAME_VALUES.get())
            {
                for (LocalVariableNode parameter : method.localVariables)
                {
                    parameter.name = NameUtils.generateLocalVariableName();
                }
            }
        }
        if ((node.sourceFile == null || !node.sourceFile.contains(StringEncryptionTransformer.MAGICNUMBER_START)) && this.V_REMOVE_DEBUG_NAMES.get())
        {
            node.sourceFile = V_NEW_SOURCE_FILE_NAME.get().isEmpty() ? null: V_NEW_SOURCE_FILE_NAME.get();
        }

    }

    @Override
    public ObfuscationTransformer getType()
    {
        return ObfuscationTransformer.LINE_NUMBER_REMOVER;
    }

}
