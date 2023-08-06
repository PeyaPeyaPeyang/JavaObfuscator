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

package tokyo.peya.obfuscator.processors;

import tokyo.peya.obfuscator.annotations.ObfuscationTransformer;
import tokyo.peya.obfuscator.IClassTransformer;
import tokyo.peya.obfuscator.JarObfuscator;
import tokyo.peya.obfuscator.ProcessorCallback;
import tokyo.peya.obfuscator.utils.NameUtils;
import tokyo.peya.obfuscator.utils.NodeUtils;
import tokyo.peya.obfuscator.utils.values.BooleanValue;
import tokyo.peya.obfuscator.utils.values.DeprecationLevel;
import tokyo.peya.obfuscator.utils.values.EnabledValue;
import tokyo.peya.obfuscator.utils.values.ValueManager;
import org.apache.commons.lang3.RandomUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NumberObfuscationTransformer implements IClassTransformer
{
    private static final String PROCESSOR_NAME = "NumberObfuscation";
    private static final Random random = new Random();
    private static final EnabledValue V_ENABLED = new EnabledValue(PROCESSOR_NAME, DeprecationLevel.GOOD, true);
    private static final BooleanValue V_EXTRACT_TO_ARRAY = new BooleanValue(PROCESSOR_NAME, "Extract to Array", "Calculates the integers once and store them in an array", DeprecationLevel.GOOD, true);
    private static final BooleanValue V_OBFUSCATE_ZERO = new BooleanValue(PROCESSOR_NAME, "Obfuscate Zero", "Enables special obfuscation of the number 0", DeprecationLevel.GOOD, true);
    private static final BooleanValue V_SHIFT = new BooleanValue(PROCESSOR_NAME, "Shift", "Uses \"<<\" to obfuscate numbers", DeprecationLevel.GOOD, false);
    private static final BooleanValue V_AND = new BooleanValue(PROCESSOR_NAME, "And", "Uses \"&\" to obfuscate numbers", DeprecationLevel.GOOD, false);
    private static final BooleanValue V_MULTIPLE_INSTRUCTIONS = new BooleanValue(PROCESSOR_NAME, "Multiple Instructions", "Repeats the obfuscation process", DeprecationLevel.GOOD, true);

    static
    {
        ValueManager.registerClass(NumberObfuscationTransformer.class);
    }

    private final JarObfuscator inst;

    public NumberObfuscationTransformer(JarObfuscator inst)
    {
        this.inst = inst;
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

        if (value == 0 && V_OBFUSCATE_ZERO.get())
        {
            int randomInt = random.nextInt(100);
            methodInstructions.add(obfuscateIntInsn(randomInt));
            methodInstructions.add(obfuscateIntInsn(randomInt));
            methodInstructions.add(new InsnNode(Opcodes.ICONST_M1));
            methodInstructions.add(new InsnNode(Opcodes.IXOR));
            methodInstructions.add(new InsnNode(Opcodes.IAND));

            return methodInstructions;
        }
        int[] shiftOutput = splitToLShift(value);

        if (shiftOutput[1] > 0 && V_SHIFT.get())
        {
            methodInstructions.add(obfuscateIntInsn(shiftOutput[0]));
            methodInstructions.add(obfuscateIntInsn(shiftOutput[1]));
            methodInstructions.add(new InsnNode(Opcodes.ISHL));
            return methodInstructions;
        }

        int method = getMethod(value);
        final boolean negative = value < 0;

        if (negative)
            value = -value;

        switch (method)
        {
            case 0:
                /*
                 * Generates a string.length() statement (e. 4 will be "kfjr".length())
                 */
                methodInstructions.add(new LdcInsnNode(NameUtils.generateSpaceString(value)));
                methodInstructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false));
                break;
            case 1:
                /*
                 * Generates a XOR statement 20 will be 29 ^ 9 <--- It's random that there a two 9s
                 */
                int A = value;
                int B = random.nextInt(200);
                A = A ^ B;
                methodInstructions.add(NodeUtils.generateIntPush(A));
                methodInstructions.add(NodeUtils.generateIntPush(B));
                methodInstructions.add(new InsnNode(Opcodes.IXOR));
                break;
            case 2:
                /*
                 * Generates a simple calculation eg.
                 */
                int addTimes = random.nextInt(10) + 3;
                int[] values = new int[addTimes];
                int sum = 0;
                for (int i = 0; i < addTimes; i++)
                {
                    int v = random.nextInt(10);
                    values[i] = v;
                    sum += v;
                }

                int toSubtract = sum - value;

                boolean subtracted = false;
                methodInstructions.add(NodeUtils.generateIntPush(values[0]));
                for (int i = 1; i < addTimes; i++)
                {
                    methodInstructions.add(NodeUtils.generateIntPush(values[i]));
                    methodInstructions.add(new InsnNode(Opcodes.IADD));
                    if (!subtracted && toSubtract > 0 && random.nextInt(10) == 0)
                    {
                        methodInstructions.add(NodeUtils.generateIntPush(toSubtract));
                        methodInstructions.add(new InsnNode(Opcodes.ISUB));
                        subtracted = true;
                    }
                }

                if (toSubtract > 0 && !subtracted)
                {
                    methodInstructions.add(NodeUtils.generateIntPush(toSubtract));
                    methodInstructions.add(new InsnNode(Opcodes.ISUB));
                }

                break;
            case 3:
                int[] and = splitToAnd(value);
                methodInstructions.add(NodeUtils.generateIntPush(and[0]));
                methodInstructions.add(NodeUtils.generateIntPush(and[1]));
                methodInstructions.add(new InsnNode(Opcodes.IAND));
                break;
        }
        if (negative)
            methodInstructions.add(new InsnNode(Opcodes.INEG));

        return methodInstructions;
    }

    private static int getMethod(int value)
    {
        int method;

        boolean lengthMode = RandomUtils.nextBoolean();
        boolean xorMode = RandomUtils.nextBoolean();
        boolean simpleMathMode = RandomUtils.nextBoolean();

        if (lengthMode && (Math.abs(value) < 4 || (!xorMode && !simpleMathMode)))
            method = 0;
        else if (xorMode && (Math.abs(value) < Byte.MAX_VALUE || (!lengthMode && !simpleMathMode)))
            method = 1;
        else if (!V_AND.get() && Math.abs(value) > 0xFF)
            method = 3;
        else
            method = 2;

        return method;
    }

    private static int[] splitToAnd(int number)
    {
        int number2 = random.nextInt(Short.MAX_VALUE) & ~number;

        return new int[]{~number2, number2 | number};
    }

    private static int[] splitToLShift(int number)
    {
        int shift = 0;

        while ((number & ~0x7ffffffffffffffEL) == 0 && number != 0)
        {
            number = number >> 1;
            shift++;
        }
        return new int[]{number, shift};
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

                int number = NodeUtils.getIntValue(abstractInsnNode);

                if (number == Integer.MIN_VALUE)
                    continue;

                boolean isExcluded = Modifier.isInterface(node.access);

                if (!isExcluded && V_EXTRACT_TO_ARRAY.get())
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
                    method.instructions.insertBefore(abstractInsnNode, new FieldInsnNode(Opcodes.GETSTATIC, node.name, fieldName, "[I"));
                    method.instructions.insertBefore(abstractInsnNode, NodeUtils.generateIntPush(containedSlot == -1 ? proceed: containedSlot));
                    method.instructions.insertBefore(abstractInsnNode, new InsnNode(Opcodes.IALOAD));
                    method.instructions.remove(abstractInsnNode);
                    if (containedSlot == -1)
                        proceed++;

                    method.maxStack += 2;
                }
                else
                {
                    method.maxStack += 4;

                    method.instructions.insertBefore(abstractInsnNode, getInstructionsMultipleTimes(number, random.nextInt(2) + 1));
                    method.instructions.remove(abstractInsnNode);
                }
            }

        if (proceed != 0)
        {
            node.fields.add(new FieldNode(((node.access & Opcodes.ACC_INTERFACE) != 0 ? Opcodes.ACC_PUBLIC: Opcodes.ACC_PRIVATE) | (node.version > Opcodes.V1_8 ? 0: Opcodes.ACC_FINAL) | Opcodes.ACC_STATIC, fieldName, "[I", null, null));
            MethodNode clInit = NodeUtils.getMethod(node, "<clinit>");
            if (clInit == null)
            {
                clInit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, new String[0]);
                node.methods.add(clInit);
            }
            if (clInit.instructions == null)
                clInit.instructions = new InsnList();

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

            if (clInit.instructions == null)
                clInit.instructions = new InsnList();

            if (clInit.instructions.getFirst() == null)
            {
                clInit.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, generateIntegers.name, generateIntegers.desc, false));
                clInit.instructions.add(new InsnNode(Opcodes.RETURN));
            }
            else
                clInit.instructions.insertBefore(clInit.instructions.getFirst(), new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, generateIntegers.name, generateIntegers.desc, false));
        }
    }

    @Override
    public ObfuscationTransformer getType()
    {
        return ObfuscationTransformer.INLINING;
    }
}
