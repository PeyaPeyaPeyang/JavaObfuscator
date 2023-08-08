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

package tokyo.peya.obfuscator.processor.number;

import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodNode;
import tokyo.peya.obfuscator.IClassTransformer;
import tokyo.peya.obfuscator.ProcessorCallback;
import tokyo.peya.obfuscator.annotations.ObfuscationTransformer;
import tokyo.peya.obfuscator.configuration.DeprecationLevel;
import tokyo.peya.obfuscator.configuration.ValueManager;
import tokyo.peya.obfuscator.configuration.values.BooleanValue;
import tokyo.peya.obfuscator.configuration.values.EnabledValue;
import tokyo.peya.obfuscator.utils.NameUtils;
import tokyo.peya.obfuscator.utils.NodeUtils;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j(topic = "Processor/NumberObfuscationTransformer")
public class NumberObfuscationTransformer implements IClassTransformer
{
    private static final String PROCESSOR_NAME = "NumberObfuscation";
    private static final Random random = new Random();
    private static final EnabledValue V_ENABLED = new EnabledValue(PROCESSOR_NAME, DeprecationLevel.AVAILABLE, true);
    private static final BooleanValue V_EXTRACT_TO_ARRAY = new BooleanValue(PROCESSOR_NAME, "Extract to Array", "Calculates the integers once and store them in an array", DeprecationLevel.AVAILABLE, true);
    private static final BooleanValue V_SPECIAL_OBFUSCATE_ZERO = new BooleanValue(PROCESSOR_NAME, "Obfuscate Zero", "Enables special obfuscation of the number 0", DeprecationLevel.AVAILABLE, true);
    private static final BooleanValue V_SHIFT = new BooleanValue(PROCESSOR_NAME, "Shift", "Uses \"<<\" to obfuscate numbers", DeprecationLevel.AVAILABLE, false);
    private static final BooleanValue V_MULTIPLE_INSTRUCTIONS = new BooleanValue(PROCESSOR_NAME, "Multiple Instructions", "Repeats the obfuscation process", DeprecationLevel.AVAILABLE, true);

    private static final BooleanValue V_METHOD_AND = new BooleanValue(PROCESSOR_NAME, "And", "Uses \"&\" to obfuscate numbers", DeprecationLevel.AVAILABLE, true);
    private static final BooleanValue V_METHOD_XOR = new BooleanValue(PROCESSOR_NAME, "XOR", "Uses \"^\" to obfuscate numbers", DeprecationLevel.AVAILABLE, true);
    private static final BooleanValue V_METHOD_STRING_LENGTH = new BooleanValue(PROCESSOR_NAME, "String Length", "Uses the length of a random string(fixed length) to obfuscate numbers", DeprecationLevel.AVAILABLE, true);
    private static final BooleanValue V_METHOD_SIMPLE_MATH = new BooleanValue(PROCESSOR_NAME, "Simple Math", "Uses simple math(add, sub, ) to obfuscate numbers", DeprecationLevel.AVAILABLE, true);

    static
    {
        ValueManager.registerClass(NumberObfuscationTransformer.class);
    }

    private static InsnList getInstructionsMultipleTimes(int value, int iterations)
    {
        InsnList list = new InsnList();
        list.add(NodeUtils.generateIntPush(value));

        for (int i = 0; i < (V_MULTIPLE_INSTRUCTIONS.get() ? iterations: 1); i++)
            list = obfuscateInsnList(list);

        return list;
    }

    public static InsnList obfuscateInsnList(InsnList list)
    {
        if (!V_ENABLED.get())
            return list;  // 他のプロセッサから呼び出される可能性がある

        for (AbstractInsnNode abstractInsnNode : list.toArray())
        {
            if (NodeUtils.isIntegerNumber(abstractInsnNode))
            {
                int number = NodeUtils.getIntValue(abstractInsnNode);

                if (number == Integer.MIN_VALUE)
                    continue;

                list.insert(abstractInsnNode, obfuscateIntInsn(number));
                list.remove(abstractInsnNode);
            }
        }
        return list;
    }

    public static InsnList obfuscateIntInsn(int value)
    {
        InsnList methodInstructions = new InsnList();
        final boolean negative = value < 0;

        if (!V_ENABLED.get())
        {
            methodInstructions.add(NodeUtils.generateIntPush(value));
            return methodInstructions;  // 他のプロセッサから呼び出される可能性がある
        }

        NumberObfuscationMethod method = NumberObfuscationMethod.pickup(value, getAvailableMethods());

        if (method == null)
        {
            log.warn("Number obfuscation is enabled but no method was selected/matched for value " + value);
            methodInstructions.add(NodeUtils.generateIntPush(value));
            return methodInstructions;
        }

        if (negative)
            value = -value;

        methodInstructions.add(method.obfuscate(value));

        if (negative)
            methodInstructions.add(new InsnNode(Opcodes.INEG));

        return methodInstructions;
    }

    private static NumberObfuscationMethod[] getAvailableMethods()
    {
        List<NumberObfuscationMethod> methods = new ArrayList<>();

        if (V_SPECIAL_OBFUSCATE_ZERO.get())
            methods.add(NumberObfuscationMethod.SPECIAL_ZERO);
        if (V_SHIFT.get())
            methods.add(NumberObfuscationMethod.SHIFT);
        if (V_METHOD_AND.get())
            methods.add(NumberObfuscationMethod.AND);
        if (V_METHOD_XOR.get())
            methods.add(NumberObfuscationMethod.XOR);
        if (V_METHOD_STRING_LENGTH.get())
            methods.add(NumberObfuscationMethod.STRING_LENGTH);
        if (V_METHOD_SIMPLE_MATH.get())
            methods.add(NumberObfuscationMethod.SIMPLE_MATH);

        return methods.toArray(new NumberObfuscationMethod[0]);
    }

    private static boolean extractToArrayOne(ClassNode clazz, MethodNode method, AbstractInsnNode abstractInsnNode, String fieldName, int proceed, List<Integer> integerList, int number)
    {
        int containedSlot = -1;
        int j = 0;
        for (Integer integer : integerList)
        {
            if (integer == number)
                containedSlot = j;
            j++;
        }
        if (containedSlot == -1)
            integerList.add(number);
        method.instructions.insertBefore(abstractInsnNode, new FieldInsnNode(Opcodes.GETSTATIC, clazz.name, fieldName, "[I"));
        method.instructions.insertBefore(abstractInsnNode, NodeUtils.generateIntPush(containedSlot == -1 ? proceed: containedSlot));
        method.instructions.insertBefore(abstractInsnNode, new InsnNode(Opcodes.IALOAD));
        method.instructions.remove(abstractInsnNode);
        if (containedSlot == -1)
            return true;

        method.maxStack += 2;

        return false;
    }

    @Override
    public void process(ProcessorCallback callback, ClassNode node)
    {
        if (!V_ENABLED.get())
            return;

        int proceed = 0;
        String fieldName = NameUtils.generateFieldName(node.name);
        List<Integer> integerList = new ArrayList<>();
        for (MethodNode method : node.methods)
            for (AbstractInsnNode abstractInsnNode : method.instructions.toArray())
            {
                if (!NodeUtils.isIntegerNumber(abstractInsnNode))
                    continue;
                else if (NodeUtils.isBeforeThanInitializer(abstractInsnNode, method, node.name))
                    continue;  // InvokeSpecial が呼ばれる前には実行できない
                /*
                例：
                <init>(String)V {
                    this.<init>(var a = new String[0], a[0] = "a");  // JVM による自動生成
                }
                <init>(String[]) {
                    // ...
                }
                 */

                int number = NodeUtils.getIntValue(abstractInsnNode);

                if (number == Integer.MIN_VALUE)
                    continue;

                if (!Modifier.isInterface(node.access) && V_EXTRACT_TO_ARRAY.get())
                {
                    boolean isExtracted = extractToArrayOne(
                            node,
                            method,
                            abstractInsnNode,
                            fieldName,
                            proceed,
                            integerList,
                            number
                    );
                    if (isExtracted)
                        proceed++;
                }
                else
                {
                    method.maxStack += 4;

                    method.instructions.insertBefore(abstractInsnNode, getInstructionsMultipleTimes(number, random.nextInt(2) + 1));
                    method.instructions.remove(abstractInsnNode);
                }
            }

        if (proceed == 0)
            return;

        boolean isInterface = (node.access & Opcodes.ACC_INTERFACE) != 0;
        node.fields.add(new FieldNode(
                        (isInterface ? Opcodes.ACC_PUBLIC: Opcodes.ACC_PRIVATE)  // インターフェースの場合はプライベートにできない
                                | (node.version > Opcodes.V1_8 ? 0: Opcodes.ACC_FINAL)
                                | Opcodes.ACC_STATIC, fieldName,
                        "[I",
                        null,
                        null
                )
        );

        InsnList toAdd = new InsnList();

        toAdd.add(NodeUtils.generateIntPush(proceed));

        toAdd.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT));
        toAdd.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, fieldName, "[I"));

        for (int j = 0; j < proceed; j++)
        {
            toAdd.add(new FieldInsnNode(Opcodes.GETSTATIC, node.name, fieldName, "[I"));
            toAdd.add(NodeUtils.generateIntPush(j));
            toAdd.add(getInstructionsMultipleTimes(integerList.get(j), random.nextInt(2) + 1));
            toAdd.add(new InsnNode(Opcodes.IASTORE));
        }

        MethodNode generateIntegers = new MethodNode(((node.access & Opcodes.ACC_INTERFACE) != 0 ? Opcodes.ACC_PUBLIC: Opcodes.ACC_PRIVATE) | Opcodes.ACC_STATIC, NameUtils.generateMethodName(node, "()V"), "()V", null, new String[0]);
        generateIntegers.instructions = toAdd;
        generateIntegers.instructions.add(new InsnNode(Opcodes.RETURN));
        generateIntegers.maxStack = 6;
        node.methods.add(generateIntegers);

        NodeUtils.addInvokeOnClassInitMethod(node, generateIntegers);
    }

    @Override
    public ObfuscationTransformer getType()
    {
        return ObfuscationTransformer.NUMBER_OBFUSCATION;
    }
}
