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

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;
import org.objectweb.asm.tree.VarInsnNode;
import tokyo.peya.obfuscator.IClassTransformer;
import tokyo.peya.obfuscator.Obfuscator;
import tokyo.peya.obfuscator.ProcessorCallback;
import tokyo.peya.obfuscator.annotations.ObfuscationTransformer;
import tokyo.peya.obfuscator.configuration.DeprecationLevel;
import tokyo.peya.obfuscator.configuration.ValueManager;
import tokyo.peya.obfuscator.configuration.values.BooleanValue;
import tokyo.peya.obfuscator.configuration.values.EnabledValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class LineNumberRemover implements IClassTransformer
{
    private static final String PROCESSOR_NAME = "line_number_remover";
    private static final Random random = new Random();
    private static final ArrayList<String> TYPES = new ArrayList<>();
    private static final EnabledValue V_ENABLED = new EnabledValue(
            PROCESSOR_NAME,
            "ui.transformers.line_number_remover.description",
            DeprecationLevel.AVAILABLE,
            true
    );
    private static final BooleanValue V_RENAME_VALUES = new BooleanValue(
            PROCESSOR_NAME,
            "rename_local_variables",
            "ui.transformers.line_number_remover.rename_local_vars",
            DeprecationLevel.AVAILABLE,
            true
    );
    private static final BooleanValue V_REMOVE_LINE_NUMBERS = new BooleanValue(
            PROCESSOR_NAME,
            "remove_line_numbers",
            "ui.transformers.line_number_remover.remove_line_nums",
            DeprecationLevel.AVAILABLE,
            true
    );
    private static final BooleanValue V_ADD_LOCAL_VARIABLES = new BooleanValue(
            PROCESSOR_NAME,
            "add_confusing_local_variables",
            "ui.transformers.line_number_remover.add_local_vars",
            DeprecationLevel.AVAILABLE,
            true
    );
    private final Obfuscator instance;

    static
    {
        TYPES.add("Z");  // Boolean
        TYPES.add("C");  // Character
        TYPES.add("B");  // Byte
        TYPES.add("S");  // Short
        TYPES.add("I");  // Integer
        TYPES.add("F");  // Float
        TYPES.add("J");  // Long
        TYPES.add("D");  // Double
        TYPES.add("Ljava/lang/Exception;");
        TYPES.add("Ljava/lang/String;");
    }

    static
    {
        ValueManager.registerOwner(PROCESSOR_NAME, "ui.transformers.line_number_remover");
        ValueManager.registerClass(LineNumberRemover.class);
    }

    public LineNumberRemover(Obfuscator instance)
    {
        this.instance = instance;
    }

    @Override
    public void process(ProcessorCallback callback, ClassNode node)
    {
        if (!V_ENABLED.get())
            return;

        for (MethodNode method : node.methods)
        {
            LabelNode firstLabel = null;
            LabelNode lastLabel = null;
            HashMap<Integer, String> varMap = new HashMap<>();

            for (AbstractInsnNode abstractInsnNode : method.instructions.toArray())
            {
                if (abstractInsnNode instanceof LineNumberNode insnNode && V_REMOVE_LINE_NUMBERS.get())
                    method.instructions.remove(insnNode);

                if (abstractInsnNode instanceof VarInsnNode insnNode && !varMap.containsKey(insnNode.var))
                    varMap.put(insnNode.var, TYPES.get(random.nextInt(TYPES.size())));
                if (abstractInsnNode instanceof LabelNode insnNode)
                {
                    if (firstLabel == null)
                        firstLabel = insnNode;

                    lastLabel = insnNode;
                }
            }

            if (firstLabel != null && V_ADD_LOCAL_VARIABLES.get())
            {
                if (method.localVariables == null)
                    method.localVariables = new ArrayList<>();

                for (Map.Entry<Integer, String> integerStringEntry : varMap.entrySet())
                    method.localVariables.add(new LocalVariableNode(
                            this.instance.getNameProvider()
                                         .generateLocalVariableName(method),
                            integerStringEntry.getValue(),
                            null,
                            firstLabel,
                            lastLabel,
                            integerStringEntry.getKey()
                    ));
            }

            if (V_RENAME_VALUES.get())
            {
                this.procRenameMethods(method);
                if (method.localVariables != null)
                    for (LocalVariableNode parameter : method.localVariables)
                        parameter.name = this.instance.getNameProvider().generateLocalVariableName(method);
            }
        }
    }

    private void procRenameMethods(MethodNode method)
    {
        if (method.parameters == null)
        {
            method.parameters = new ArrayList<>();
            Type[] argumentTypes = Type.getArgumentTypes(method.desc);
            for (int i = 0; i < argumentTypes.length; i++)
            {
                String name = this.instance.getNameProvider().generateLocalVariableName(method);
                method.parameters.add(new ParameterNode(name, 0));
            }
        }
        else
            for (ParameterNode parameter : method.parameters)
                parameter.name = this.instance.getNameProvider().generateLocalVariableName(method);

    }

    @Override
    public ObfuscationTransformer getType()
    {
        return ObfuscationTransformer.LINE_NUMBER_REMOVER;
    }

}
