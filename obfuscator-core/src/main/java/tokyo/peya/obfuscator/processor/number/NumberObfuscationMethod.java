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

package tokyo.peya.obfuscator.processor.number;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import tokyo.peya.obfuscator.utils.NameUtils;
import tokyo.peya.obfuscator.utils.NodeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public enum NumberObfuscationMethod implements INumberObfuscator
{

    /**
     * Generates a string.length() statement (e. 4 will be "kfjr".length())
     */
    STRING_LENGTH
            {
                @Override
                public void obfuscate(int value, InsnList insns)
                {
                    insns.add(new LdcInsnNode(NameUtils.generateSpaceString(value)));
                    insns.add(new MethodInsnNode(
                                      Opcodes.INVOKEVIRTUAL,
                                      "java/lang/String",
                                      "length",
                                      "()I",
                                      false
                              )
                    );
                }

                @Override
                public boolean canApply(int value)
                {
                    return Math.abs(value) <= 65535;  // 100 こえたらやばいっす
                }
            },
    /**
     * Generates a XOR statement 20 will be 29 ^ 9 <--- It's random that there a two 9s
     */
    XOR
            {
                @Override
                public void obfuscate(int value, InsnList insns)
                {
                    Random random = new Random();

                    int A = value;
                    int B = random.nextInt(200);
                    A = A ^ B;
                    insns.add(NodeUtils.generateIntPush(A));
                    insns.add(NodeUtils.generateIntPush(B));
                    insns.add(new InsnNode(Opcodes.IXOR));
                }

                @Override
                public boolean canApply(int value)
                {
                    return Math.abs(value) <= Byte.MAX_VALUE;
                }
            },
    /**
     * Generates a simple math statement 20 will be (1 + 2 + 3) - (1 + 2 + 3 + 20)
     */
    SIMPLE_MATH
            {
                @Override
                public void obfuscate(int value, InsnList insns)
                {
                    Random random = new Random();

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
                    boolean subtractionNeeded = toSubtract != 0;

                    boolean subtracted = false;
                    insns.add(NodeUtils.generateIntPush(values[0]));
                    for (int i = 1; i < addTimes; i++)
                    {
                        insns.add(NodeUtils.generateIntPush(values[i]));
                        insns.add(new InsnNode(Opcodes.IADD));
                        if (subtractionNeeded && !subtracted && random.nextInt(10) == 0)
                        {
                            insns.add(NodeUtils.generateIntPush(toSubtract));
                            insns.add(new InsnNode(Opcodes.ISUB));
                            subtracted = true;
                        }
                    }

                    if (subtractionNeeded && !subtracted)
                    {
                        insns.add(NodeUtils.generateIntPush(toSubtract));
                        insns.add(new InsnNode(Opcodes.ISUB));
                    }
                }

                @Override
                public boolean canApply(int value)
                {
                    return true;
                }
            },
    /**
     * Generates a AND statement 20 will be 20 & 20
     */
    AND
            {
                @Override
                public void obfuscate(int value, InsnList insns)
                {

                    int[] and = splitToAnd(value);
                    insns.add(NodeUtils.generateIntPush(and[0]));
                    insns.add(NodeUtils.generateIntPush(and[1]));
                    insns.add(new InsnNode(Opcodes.IAND));
                }

                private int[] splitToAnd(int number)
                {
                    Random random = new Random();
                    int number2 = random.nextInt(Short.MAX_VALUE) & ~number;

                    return new int[]{~number2, number2 | number};
                }

                @Override
                public boolean canApply(int value)
                {
                    return Math.abs(value) > 0xFF;
                }
            },
    SHIFT
            {
                @Override
                public void obfuscate(int value, InsnList insns)
                {
                    int[] shift = splitToLShift(value);
                    insns.add(NodeUtils.generateIntPush(shift[0]));
                    insns.add(NodeUtils.generateIntPush(shift[1]));
                    insns.add(new InsnNode(Opcodes.ISHL));
                }

                @Override
                public boolean canApply(int value)
                {
                    return splitToLShift(value)[1] > 0;
                }

                private int[] splitToLShift(int number)
                {
                    int shift = 0;

                    while ((number & ~0x7ffffffffffffffEL) == 0 && number != 0)
                    {
                        number = number >> 1;
                        shift++;
                    }
                    return new int[]{number, shift};
                }
            },
    SPECIAL_ZERO
            {
                @Override
                public void obfuscate(int value, InsnList insns)
                {
                    Random random = new Random();
                    int randomInt = random.nextInt(100);
                    insns.add(NodeUtils.generateIntPush(randomInt));
                    insns.add(NodeUtils.generateIntPush(randomInt));
                    insns.add(new InsnNode(Opcodes.ICONST_M1));
                    insns.add(new InsnNode(Opcodes.IXOR));
                    insns.add(new InsnNode(Opcodes.IAND));
                }

                @Override
                public boolean canApply(int value)
                {
                    return value == 0;
                }
            };

    public static NumberObfuscationMethod pickup(int value,
                                                 NumberObfuscationMethod[] methods)
    {
        if (methods.length == 0)
            return null;

        Random random = new Random();
        List<NumberObfuscationMethod> methodList = new ArrayList<>(Arrays.asList(methods));

        while (true)
        {
            NumberObfuscationMethod method = methodList.get(random.nextInt(methodList.size()));
            if (method.canApply(value))
                return method;
            methodList.remove(method);
            if (methodList.isEmpty())
                return null;
        }
    }
}
