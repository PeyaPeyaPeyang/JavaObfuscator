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
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;
import tokyo.peya.obfuscator.ProcessorCallback;
import tokyo.peya.obfuscator.utils.NodeUtils;
import tokyo.peya.obfuscator.utils.VariableProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class LocalVariableMangler
{
    static void mangleLocalVariables(ProcessorCallback callback, ClassNode node, MethodNode method)
    {
        InsnList instructions = method.instructions;

        // 一時的にスタックサイズとローカル変数の最大値を変更し, ASM のフレーム解析を行う
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

        VariableProvider provider = new VariableProvider(method);

        // KEY: Type of array
        // VALUE: Array local variable index
        HashMap<Type, Integer> typeArrayMap = new HashMap<>();

        // KEY: Original Local variable
        // VALUE: [0]: Local variable index of the array, [1]: Array index
        HashMap<Integer, int[]> slotMap = new HashMap<>();

        // KEY: Array local variable index
        // VALUE: Current highest array index
        HashMap<Integer, Integer> arrayIndices = new HashMap<>();

        // Map of local variables and their types.
        // They are added if the type of the variable is double, float, int or long
        HashMap<Integer, Type> localVarMap = scanLoads(instructions, provider, frames);

        removeVoidInsn(localVarMap);

        for (Map.Entry<Integer, Type> integerTypeEntry : localVarMap.entrySet())
        {
            if (!typeArrayMap.containsKey(integerTypeEntry.getValue()))
                typeArrayMap.put(integerTypeEntry.getValue(), provider.allocateVar());

            int index = typeArrayMap.get(integerTypeEntry.getValue());
            int arrayIndex;

            if (!arrayIndices.containsKey(index))
                arrayIndices.put(index, 0);

            arrayIndex = arrayIndices.get(index);

            arrayIndices.put(index, arrayIndex + 1);
            slotMap.put(integerTypeEntry.getKey(), new int[]{index, arrayIndex});
        }

        InsnList initialize = buildInstructions(typeArrayMap, arrayIndices);
        for (AbstractInsnNode abstractInsnNode : method.instructions.toArray())
        {
            if (abstractInsnNode instanceof VarInsnNode varInsnNode)
            {
                if (!slotMap.containsKey(varInsnNode.var))
                    continue;

                // Check if it is a load instruction
                if (varInsnNode.getOpcode() == Opcodes.ILOAD
                        || varInsnNode.getOpcode() == Opcodes.LLOAD
                        || varInsnNode.getOpcode() == Opcodes.FLOAD
                        || varInsnNode.getOpcode() == Opcodes.DLOAD)
                    replaceLoadInstructions(instructions, varInsnNode, slotMap, localVarMap);

                // Check if it is a store instruction
                if (abstractInsnNode.getOpcode() == Opcodes.ISTORE
                        || abstractInsnNode.getOpcode() == Opcodes.LSTORE
                        || abstractInsnNode.getOpcode() == Opcodes.FSTORE
                        || abstractInsnNode.getOpcode() == Opcodes.DSTORE)
                    replaceStoreInstructions(instructions, varInsnNode, slotMap, localVarMap);
            }

            if (abstractInsnNode instanceof IincInsnNode iincInsnNode)
            {
                if (!slotMap.containsKey(iincInsnNode.var))
                    continue;

                replaceIncrements(instructions, iincInsnNode, slotMap, localVarMap);
            }
        }

        if (!localVarMap.isEmpty())
            instructions.insertBefore(instructions.getFirst(), initialize);

        callback.setForceComputeFrames();
    }

    private static void replaceIncrements(InsnList methodInstructions, IincInsnNode iincInsnNode,
                                          HashMap<Integer, int[]> slotMap,
                                          HashMap<Integer, Type> localVarMap)
    {
        int[] value = slotMap.get(iincInsnNode.var);

        InsnList replace = new InsnList();

        replace.add(new VarInsnNode(Opcodes.ALOAD, value[0]));
        replace.add(NodeUtils.generateIntPush(value[1]));

        replace.add(new VarInsnNode(Opcodes.ALOAD, value[0]));
        replace.add(NodeUtils.generateIntPush(value[1]));
        replace.add(new InsnNode(localVarMap.get(iincInsnNode.var).getOpcode(Opcodes.IALOAD)));

        replace.add(NodeUtils.generateIntPush(iincInsnNode.incr));
        replace.add(new InsnNode(Opcodes.IADD));

        replace.add(new InsnNode(localVarMap.get(iincInsnNode.var).getOpcode(Opcodes.IASTORE)));

        methodInstructions.insert(iincInsnNode, replace);
        methodInstructions.remove(iincInsnNode);
    }

    private static void replaceStoreInstructions(InsnList methodInstructions, VarInsnNode varInsnNode,
                                                 HashMap<Integer, int[]> slotMap, HashMap<Integer, Type> localVarMap)
    {

        int[] value = slotMap.get(varInsnNode.var);
        Type type = localVarMap.get(varInsnNode.var);

        InsnList replace = new InsnList();

        // long または double の値を正しく格納する: [value] -> [arrayREF, index, value]
        if (type.getSize() == 2)
        {
            // long/double（2 スタックスロット分を占有）の場合
            replace.add(new InsnNode(Opcodes.DUP2)); // long/double の値を複製
            replace.add(new VarInsnNode(Opcodes.ALOAD, value[0])); // 配列参照を読み込む
            replace.add(NodeUtils.generateIntPush(value[1])); // インデックスをプッシュ
            replace.add(new InsnNode(Opcodes.DUP2_X2)); // スタックの順序を調整
            replace.add(new InsnNode(Opcodes.POP2));    // 不要な値を除去
        }
        else
        {
            // int/float の場合
            replace.add(new VarInsnNode(Opcodes.ALOAD, value[0])); // 配列参照を読み込む
            replace.add(NodeUtils.generateIntPush(value[1]));      // インデックスをプッシュ
            replace.add(new InsnNode(Opcodes.SWAP));               // 値とインデックスを入れ替える
        }

        replace.add(new InsnNode(type.getOpcode(Opcodes.IASTORE))); // Store

        methodInstructions.insert(varInsnNode, replace);
        methodInstructions.remove(varInsnNode);
    }

    private static void replaceLoadInstructions(InsnList methodInstructions, VarInsnNode varInsnNode,
                                                HashMap<Integer, int[]> slotMap, HashMap<Integer, Type> localVarMap)
    {
        int[] value = slotMap.get(varInsnNode.var);

        InsnList replace = new InsnList();
        replace.add(new VarInsnNode(Opcodes.ALOAD, value[0]));
        replace.add(NodeUtils.generateIntPush(value[1]));
        replace.add(new InsnNode(localVarMap.get(varInsnNode.var).getOpcode(Opcodes.IALOAD)));

        methodInstructions.insert(varInsnNode, replace);
        methodInstructions.remove(varInsnNode);
    }

    private static InsnList buildInstructions(HashMap<Type, Integer> typeArrays, HashMap<Integer, Integer> arrayIndices)
    {
        InsnList initialize = new InsnList();
        for (Map.Entry<Type, Integer> integerTypeEntry : typeArrays.entrySet())
        {
            int arrayType = integerTypeEntry.getKey().getSort();

            arrayType = switch (arrayType)
            {
                case Type.INT -> Opcodes.T_INT;
                case Type.LONG -> Opcodes.T_LONG;
                case Type.DOUBLE -> Opcodes.T_DOUBLE;
                case Type.FLOAT -> Opcodes.T_FLOAT;
                default -> throw new IllegalArgumentException();
            };

            initialize.add(NodeUtils.generateIntPush(arrayIndices.get(integerTypeEntry.getValue())));
            initialize.add(new IntInsnNode(Opcodes.NEWARRAY, arrayType));
            initialize.add(new VarInsnNode(Opcodes.ASTORE, integerTypeEntry.getValue()));
        }

        return initialize;
    }

    private static void removeVoidInsn(HashMap<Integer, Type> localVarMap)
    {
        List<Integer> remove = new ArrayList<>();

        for (Map.Entry<Integer, Type> integerTypeEntry : localVarMap.entrySet())
            if (integerTypeEntry.getValue().getSort() == Type.VOID)
                remove.add(integerTypeEntry.getKey());

        for (Integer integer : remove)
            localVarMap.remove(integer);
    }

    private static HashMap<Integer, Type> scanLoads(InsnList methodInstructions, VariableProvider provider,
                                                    Frame<SourceValue>[] frames)
    {
        HashMap<Integer, Type> localVarMapToStore = new HashMap<>();

        for (AbstractInsnNode abstractInsnNode : methodInstructions.toArray())
        {
            if (!(abstractInsnNode instanceof VarInsnNode insnNode))
                continue;

            if (provider.isArgument(insnNode.var))
                continue;

            if (!localVarMapToStore.containsKey(insnNode.var))
            {
                Type t = switch (insnNode.getOpcode())  // ロード系
                {
                    case Opcodes.ILOAD -> Type.INT_TYPE;
                    case Opcodes.LLOAD -> Type.LONG_TYPE;
                    case Opcodes.FLOAD -> Type.FLOAT_TYPE;
                    case Opcodes.DLOAD -> Type.DOUBLE_TYPE;
                    default -> null;
                };

                if (t != null)
                    localVarMapToStore.put(insnNode.var, t);
            }

            if (insnNode.getOpcode() >= Opcodes.ISTORE && insnNode.getOpcode() <= Opcodes.ASTORE)
            {
                Frame<SourceValue> currentFrame = frames[methodInstructions.indexOf(insnNode)];
                SourceValue stack = currentFrame.getStack(currentFrame.getStackSize() - 1);

                if (stack.getSize() > 1) // LONG と DOUBLE の場合
                {
                    Type type = guessTypeFromFrame(stack);
                    if (type != null && !localVarMapToStore.containsKey(insnNode.var))
                        localVarMapToStore.put(insnNode.var, type);
                }
            }
        }

        return localVarMapToStore;
    }

    private static Type guessTypeFromFrame(SourceValue value)
    {
        for (AbstractInsnNode insn : value.insns)
            switch (insn.getOpcode())
            {
                case Opcodes.LSTORE, Opcodes.LLOAD, Opcodes.LADD, Opcodes.LSUB ->
                {
                    return Type.LONG_TYPE;
                }
                case Opcodes.DSTORE, Opcodes.DLOAD, Opcodes.DADD, Opcodes.DSUB ->
                {
                    return Type.DOUBLE_TYPE;
                }
            }

        return null;
    }
}
