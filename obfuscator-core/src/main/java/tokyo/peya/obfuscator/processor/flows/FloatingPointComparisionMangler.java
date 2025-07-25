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

package tokyo.peya.obfuscator.processor.flows;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import tokyo.peya.obfuscator.UniqueNameProvider;

import java.util.Collection;
import java.util.HashMap;

class FloatingPointComparisionMangler
{
    static Collection<MethodNode> mangleComparisions(UniqueNameProvider nameProvider, ClassNode cn, MethodNode node)
    {
        HashMap<Integer, MethodNode> comparisionMethodMap = new HashMap<>();

        for (AbstractInsnNode insnNode : node.instructions.toArray())
        {
            if (insnNode.getOpcode() >= Opcodes.LCMP && insnNode.getOpcode() <= Opcodes.DCMPG)
            {
                if (!comparisionMethodMap.containsKey(insnNode.getOpcode()))
                    comparisionMethodMap.put(
                            insnNode.getOpcode(),
                            generateComparisionMethod(nameProvider, cn, insnNode.getOpcode())
                    );

                MethodNode comparisionMethod = comparisionMethodMap.get(insnNode.getOpcode());

                // Invokes the comparision method instead of the comparision opcode
                // e.g: invokestatic    Test.compare:(DD)I
                node.instructions.insert(insnNode, new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        cn.name,
                        comparisionMethod.name,
                        comparisionMethod.desc,
                        false
                ));
                node.instructions.remove(insnNode);
            }
        }

        return comparisionMethodMap.values();

    }

    /**
     * Generates a method that looks like this:
     * <p>
     * private static int compare(double, double);
     * Flags: PRIVATE, STATIC
     * Code:
     * 0: dload_0
     * 1: dload_2
     * 2: dcmpl (<--- The opcode)
     * 3: ireturn
     *
     * @param cn     The ClassNode the method is supposed to be
     * @param opcode the comparision opcode. Allowed opcodes: LCMP, FCMPL, FCMPG, DCMPL, DCMPG
     * @return The method node
     */
    private static MethodNode generateComparisionMethod(UniqueNameProvider nameProvider, ClassNode cn, int opcode)
    {
        if (!(opcode >= Opcodes.LCMP && opcode <= Opcodes.DCMPG))
            throw new IllegalArgumentException("The opcode must be LCMP, FCMPL, FCMPG, DCMPL or DCMPG");

        // The type of numbers which are compared
        Type type = opcode == Opcodes.LCMP ? Type.LONG_TYPE: (opcode == Opcodes.FCMPG || opcode == Opcodes.FCMPL) ? Type.FLOAT_TYPE: Type.DOUBLE_TYPE;
        String desc = "(" + type + type + ")I";

        MethodNode methodNode = new MethodNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                nameProvider.toUniqueMethodName(
                        cn,
                        "compare" + opCodeToName(opcode), desc
                ),
                desc, null, new String[0]
        );

        methodNode.instructions = new InsnList();

        methodNode.instructions.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD), 0));
        methodNode.instructions.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD), type.getSize()));
        methodNode.instructions.add(new InsnNode(opcode));
        methodNode.instructions.add(new InsnNode(Opcodes.IRETURN));

        return methodNode;
    }

    private static String opCodeToName(int code)
    {
        return switch (code)
        {
            case Opcodes.LCMP -> "LCMP";
            case Opcodes.FCMPL -> "FCMPL";
            case Opcodes.FCMPG -> "FCMPG";
            case Opcodes.DCMPL -> "DCMPL";
            case Opcodes.DCMPG -> "DCMPG";
            default -> "UNKNOWN";
        };
    }

}
