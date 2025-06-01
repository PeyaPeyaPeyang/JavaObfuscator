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

package tokyo.peya.obfuscator.processor;

import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import tokyo.peya.obfuscator.IClassTransformer;
import tokyo.peya.obfuscator.Obfuscator;
import tokyo.peya.obfuscator.ProcessorCallback;
import tokyo.peya.obfuscator.annotations.ObfuscationTransformer;
import tokyo.peya.obfuscator.configuration.DeprecationLevel;
import tokyo.peya.obfuscator.configuration.ValueManager;
import tokyo.peya.obfuscator.configuration.values.EnabledValue;
import tokyo.peya.obfuscator.utils.NameUtils;
import tokyo.peya.obfuscator.utils.NodeUtils;
import tokyo.peya.obfuscator.utils.Utils;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.ICONST_2;
import static org.objectweb.asm.Opcodes.ICONST_3;
import static org.objectweb.asm.Opcodes.ICONST_4;
import static org.objectweb.asm.Opcodes.ICONST_5;
import static org.objectweb.asm.Opcodes.IF_ICMPGT;
import static org.objectweb.asm.Opcodes.IF_ICMPNE;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.PUTSTATIC;

@Slf4j(topic = "Processor/InvokeDynamic")
public class InvokeDynamic implements IClassTransformer
{
    private static final String PROCESSOR_NAME = "invoke_dynamic";
    private static final EnabledValue V_ENABLED = new EnabledValue(PROCESSOR_NAME, "ui.transformers.invoke_dynamic.description", DeprecationLevel.SOME_DEPRECATION, false);

    static
    {
        ValueManager.registerOwner(PROCESSOR_NAME, "ui.transformers.invoke_dynamic");
        ValueManager.registerClass(InvokeDynamic.class);
    }

    private final Obfuscator instance;

    public InvokeDynamic(Obfuscator instance)
    {
        this.instance = instance;
    }

    @Override
    public void process(ProcessorCallback callback, ClassNode classNode)
    {
        if (!(V_ENABLED.get() && NodeUtils.isNormalClass(classNode)))
            return;

        if (classNode.version == Opcodes.V1_1 || classNode.version < Opcodes.V1_4)
        {
            log.warn("!!! WARNING !!! " + classNode.name + "'s lang level is too low (VERSION < V1_4)");
            return;
        }

        // <invokedynamic> は 1.7 以降でサポートされているため, クラスバージョンを合わせる
        if (classNode.version < Opcodes.V1_7)
        {
            classNode.version = Opcodes.V1_7;
            callback.setForceComputeFrames();
        }

        // メソッドとフィールドの呼び出し: インデックスを格納するフィールド
        FieldNode methodInvocationsField = new FieldNode(
                ACC_PRIVATE | ACC_STATIC,
                this.instance.getNameProvider().toUniqueFieldName(classNode, "METHOD_TARGETS"),
                "[Ljava/lang/String;",
                null,
                null
        );
        FieldNode arrayInvocationsField = new FieldNode(
                ACC_PRIVATE | ACC_STATIC,
                this.instance.getNameProvider().toUniqueFieldName(classNode, "FIELD_TARGETS"),
                "[Ljava/lang/Class;",
                null,
                null
        );

        MethodNode bootstrap = this.generateBootstrapMethod(methodInvocationsField, arrayInvocationsField, classNode);
        String bootstrapDescriptor = MethodType.methodType(
                /* return: */ CallSite.class,
                /* param: */ MethodHandles.Lookup.class, String.class, MethodType.class
        ).toMethodDescriptorString();
        Handle bootstrapHandle = new Handle(H_INVOKESTATIC, classNode.name, bootstrap.name, bootstrapDescriptor, false);

        // メソッドとフィールドの呼び出し: インデックス
        HashMap<String, Integer> invocations = new HashMap<>();
        HashMap<Type, Integer> fieldTypes = new HashMap<>();

        // メソッドとフィールドの呼び出しを置換
        long count = replaceMethodInstructions(classNode, bootstrapHandle, invocations, fieldTypes);
        if (count <= 0)
            return;  // 何も置換されなかった場合は処理を終了

        // 呼び出し定義の生成メソッドを作成
        MethodNode generatorMethod = this.createInvocationsGenerator(classNode, methodInvocationsField, arrayInvocationsField, invocations, fieldTypes);

        // クラスにフィールドとメソッドを追加
        classNode.methods.add(bootstrap);
        classNode.methods.add(generatorMethod);
        classNode.fields.add(methodInvocationsField);
        classNode.fields.add(arrayInvocationsField);
    }

    private static boolean replaceMethodInvocation(MethodNode method, MethodInsnNode invocation, Handle bootstrap,
                                                   Map<? super String, Integer> map)
    {
        int opcode = invocation.getOpcode();
        boolean isVirtualOrInterface = opcode == INVOKEVIRTUAL || opcode == INVOKEINTERFACE;
        boolean isStatic = opcode == INVOKESTATIC;

        boolean isMethodCall = isVirtualOrInterface || isStatic;
        if (!isMethodCall)
            return false;

        // path/to/MyClass/doSomething(Ljava/lang/String;)V -> path.to.MyClass:doSomething(Ljava/lang/String;)V:<SIG>
        int methodTypeSign = getInvocationTypeByOpcode(opcode);
        String name = invocation.owner.replace('/', '.') + ":"
                + invocation.name + ":"
                + invocation.desc + ":"
                + NameUtils.generateSpaceString(methodTypeSign);


        int index;
        if (map.containsKey(name))
            index = map.get(name);
        else
        {
            index = map.size();  // 現在の要素数 = 次のインデックス
            map.put(name, index);
        }

        String invocationDescriptor;
        if (isVirtualOrInterface)
        {
            StringBuilder sb = new StringBuilder();
            if (invocation.owner.startsWith("["))
                sb.append("(");
            else
                sb.append("(L");
            sb.append(invocation.owner);
            if (invocation.owner.endsWith(";"))
                sb.append(")");
            else
                sb.append(";");

            sb.append(invocation.desc.substring(1));  // メソッドの引数部分を取得
            invocationDescriptor = sb.toString();
        }
        else /* if (isStatic) */
            invocationDescriptor = invocation.desc;  // 静的メソッドの場合はそのまま


        String invocationName = Integer.toString(index);
        method.instructions.insert(
                invocation,
                new InvokeDynamicInsnNode(invocationName, invocationDescriptor, bootstrap)
        );
        method.instructions.remove(invocation);

        return true;
    }

    private static boolean replaceFieldReference(MethodNode method, FieldInsnNode field, Handle bootstrap,
                                                 Map<? super String, Integer> map, Map<? super Type, Integer> typeMap)
    {
        int opcode = field.getOpcode();
        boolean isGet = opcode == GETFIELD || opcode == GETSTATIC;
        boolean isPut = opcode == PUTFIELD || opcode == PUTSTATIC;
        if (!(isGet || isPut))
            return false;

        if (isPut && !isFieldWritable(field))
            return false; // フィールドが書き込み可能でない場合は何もしない

        Type fieldType = Type.getType(field.desc);
        int typeIndex;

        if (typeMap.containsKey(fieldType))
            typeIndex = typeMap.get(fieldType);
        else
        {
            typeIndex = typeMap.size();  // 現在の要素数 = 次のインデックス
            typeMap.put(fieldType, typeIndex);
        }

        int methodTypeSign = getInvocationTypeByOpcode(opcode);
        String name = field.owner.replace('/', '.') + ":" +
                field.name + ":" +
                typeIndex + ":" +
                NameUtils.generateSpaceString(methodTypeSign);

        int index;
        if (map.containsKey(name))
            index = map.get(name);
        else
        {
            index = map.size();  // 現在の要素数 = 次のインデックス
            map.put(name, index);
        }

        String invocationName = Integer.toString(index);
        String invocationDescriptor = switch (opcode) {
            // 値の取得
            case GETSTATIC -> "()" + field.desc; // 静的フィールドの取
            case GETFIELD -> "(L" + field.owner + ";)" + field.desc; // インスタンスフィールド

            // 値の設定
            case PUTSTATIC -> "(" + field.desc + ")V"; // 静的フィールド
            case PUTFIELD -> "(L" + field.owner + ";" + field.desc + ")V"; // インスタンスフィールド
            default -> throw new IllegalStateException("Unexpected value: " + opcode);
        };

        method.instructions.insert(
                field,
                new InvokeDynamicInsnNode(invocationName, invocationDescriptor, bootstrap)
        );
        method.instructions.remove(field);

        return true;
    }
    private static boolean isFieldWritable(FieldInsnNode fieldInsnNode)
    {
        ClassNode owner = Utils.lookupClass(fieldInsnNode.owner);
        FieldNode field = null;

        if (owner != null)
            field = Utils.getField(owner, fieldInsnNode.name);

        if (field == null)
            log.warn("Field {}.{} wasn't found. Please add it as library", fieldInsnNode.owner, fieldInsnNode.name);
        else if (Modifier.isFinal(field.access))
            log.warn("Field {}.{} is final. It cannot be modified.", fieldInsnNode.owner, fieldInsnNode.name);
        else
            return true; // フィールドが存在し、finalでない場合は書き込み可能

        return false;
    }

    private static int getInvocationTypeByOpcode(int opcode)
    {
        return switch (opcode)
        {
            case INVOKESTATIC -> 1; // 静的メソッドの呼び出し
            case INVOKEVIRTUAL, INVOKEINTERFACE -> 2; // インスタンスメソッド/インタフェースの呼び出し
            case GETFIELD -> 3; // フィールド値の取得
            case GETSTATIC -> 4; // 静的フィールド値の取得
            case PUTFIELD -> 5; // フィールド値の設定
            case PUTSTATIC -> 6; // 静的フィールド値の設定
            default -> throw new IllegalArgumentException("Unsupported opcode: " + opcode);
        };
    }

    private static int replaceMethodInstructions(MethodNode method,
                                                 Handle bootstrap,
                                                 HashMap<? super String, Integer> methodMap,
                                                 HashMap<? super Type, Integer> fieldTypeMap)
    {
        int count = 0;
        for (AbstractInsnNode abstractInsnNode : method.instructions.toArray())
        {
            boolean isReplaced = false;
            if (abstractInsnNode instanceof MethodInsnNode methodInsnNode)
                isReplaced = replaceMethodInvocation(method, methodInsnNode, bootstrap, methodMap);
            else if (abstractInsnNode instanceof FieldInsnNode fieldInsnNode)
                isReplaced = replaceFieldReference(method, fieldInsnNode, bootstrap, methodMap, fieldTypeMap);

            if (isReplaced)
                count++;
        }
        return count;
    }

    private static long replaceMethodInstructions(ClassNode clazz,
                                                  Handle bootstrap,
                                                  HashMap<? super String, Integer> methodMap,
                                                  HashMap<? super Type, Integer> fieldTypeMap)
    {
        long count = 0;
        for (MethodNode method : clazz.methods)
        {
            if (method.instructions == null || method.instructions.size() == 0)
                continue;

            count += replaceMethodInstructions(method, bootstrap, methodMap, fieldTypeMap);
        }
        return count;
    }

    private MethodNode createInvocationsGenerator(ClassNode classNode, FieldNode arrayField, FieldNode typeArrayField,
                                                  Map<String, Integer> map, Map<Type, Integer> typeMap)
    {
        MethodNode generatorMethod = new MethodNode(
                ACC_PRIVATE | ACC_STATIC,
                this.instance.getNameProvider().toUniqueFieldName(classNode, "generateInvocations"),
                "()V",
                null,
                new String[0]
        );

        InsnList generatorMethodNodes = generatorMethod.instructions = new InsnList();
        {
            InsnList methodInvocations = generateMethodInvocationsList(classNode, arrayField, map);
            InsnList fieldInvocations = generateFieldInvocationsList(classNode, typeArrayField, typeMap);

            NodeUtils.combineInstructions(generatorMethodNodes, methodInvocations);
            NodeUtils.combineInstructions(generatorMethodNodes, fieldInvocations);

            generatorMethodNodes.add(new InsnNode(Opcodes.RETURN));
        }

        NodeUtils.addInvokeOnClassInitialisation(classNode, generatorMethod);

        return generatorMethod;
    }

    private static InsnList generateMethodInvocationsList(ClassNode classNode, FieldNode methodsField, Map<String, Integer> map)
    {
        InsnList instructions = new InsnList();

        List<Map.Entry<String, Integer>> list = new ArrayList<>(map.entrySet());
        Collections.shuffle(list);

        // サイズ list.size() の String 配列を生成し, フィールドに格納する
        instructions.add(NodeUtils.generateIntPush(list.size()));
        instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"));
        instructions.add(new FieldInsnNode(PUTSTATIC, classNode.name, methodsField.name, methodsField.desc));

        // 以下, 配列に要素を格納する処理
        for (Map.Entry<String, Integer> integerStringEntry : list)
        {
            // フィールドを取得し, インデックスを指定して値を格納する
            instructions.add(new FieldInsnNode(GETSTATIC, classNode.name, methodsField.name, methodsField.desc));

            instructions.add(NodeUtils.generateIntPush(integerStringEntry.getValue()));
            instructions.add(new LdcInsnNode(integerStringEntry.getKey()));

            instructions.add(new InsnNode(Opcodes.AASTORE));
        }
        return instructions;
    }

    private static InsnList generateFieldInvocationsList(ClassNode classNode, FieldNode fieldField, Map<Type, Integer> map)
    {
        InsnList instructions = new InsnList();

        List<Map.Entry<Type, Integer>> list = new ArrayList<>(map.entrySet());
        Collections.shuffle(list);

        // サイズ list.size() の配列を生成し, フィールドに格納する
        instructions.add(NodeUtils.generateIntPush(list.size()));
        instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Class"));
        instructions.add(new FieldInsnNode(PUTSTATIC, classNode.name, fieldField.name, fieldField.desc));

        // 以下, 配列に要素を格納する処理
        for (Map.Entry<Type, Integer> integerStringEntry : list)
        {
            // フィールドを取得し, インデックスを指定して値を格納する
            instructions.add(new FieldInsnNode(GETSTATIC, classNode.name, fieldField.name, fieldField.desc));

            instructions.add(NodeUtils.generateIntPush(integerStringEntry.getValue()));
            {
                // 型の情報を LdcInsnNode で格納する
                Type type = integerStringEntry.getKey();

                // プリミティブ型の場合はボクシングしないと, スタックに積めない。
                // 以下, プリミティブ型 -> ボクシング型の変換。
                int sort = type.getSort();
                if (sort == Type.ARRAY || sort == Type.OBJECT)  // プリミティブではない場合
                    instructions.add(new LdcInsnNode(type));
                else
                    instructions.add(NodeUtils.getTypeNode(type));
            }

            instructions.add(new InsnNode(Opcodes.AASTORE));
        }
        return instructions;
    }

    private MethodNode generateBootstrapMethod(FieldNode arrayField, FieldNode typeField, ClassNode node)
    {
        String className = node.name;

        String referenceFieldName = arrayField.name;
        String referenceFieldType = arrayField.desc;

        String typeFieldName = typeField.name;
        String typeFieldType = typeField.desc;

        MethodNode mv;
        {
            mv = new MethodNode(ACC_PRIVATE + ACC_STATIC,
                                this.instance.getNameProvider().toUniqueMethodName(
                                        node,
                                        "invokedynamic",
                                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;"
                                ),
                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                                null,
                                new String[]{"java/lang/NoSuchMethodException", "java/lang/IllegalAccessException"}
            );
            mv.visitCode();
            Label l0 = new Label();
            Label l1 = new Label();
            Label l2 = new Label();
            // try-catch ブロックの開始
            mv.visitTryCatchBlock(l0, l1, l2, "java/lang/Exception");
            // try { のぶぶん

            mv.visitLabel(l0);
            mv.visitLineNumber(16, l0);

            // String[] split = METHOD_TARGETS[Integer.parseInt(s)].split(":");
            mv.visitFieldInsn(GETSTATIC, className, referenceFieldName, referenceFieldType);
            mv.visitVarInsn(ALOAD, 1);

            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false);
            mv.visitInsn(AALOAD);

            mv.visitLdcInsn(":"); // デリミタ, #split() の引数
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false);
            mv.visitVarInsn(ASTORE, 3);  // 3: split = ...

            Label l3 = new Label();
            mv.visitLabel(l3);
            mv.visitLineNumber(17, l3);

            // Class classIn = Class.forName(split[0])
            mv.visitVarInsn(ALOAD, 3);
            mv.visitInsn(ICONST_0);
            mv.visitInsn(AALOAD);
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);
            mv.visitVarInsn(ASTORE, 4);  // 4: classIn = ...

            Label l4 = new Label();
            mv.visitLabel(l4);
            mv.visitLineNumber(18, l4);

            // String name = split[1]
            mv.visitVarInsn(ALOAD, 3);  // 3: split
            mv.visitInsn(ICONST_1);
            mv.visitInsn(AALOAD);
            mv.visitVarInsn(ASTORE, 5);  // 5: name = ...

            Label l5 = new Label();
            mv.visitLabel(l5);
            mv.visitLineNumber(19, l5);

            // MethodHandle methodHandle = null
            mv.visitInsn(ACONST_NULL);
            mv.visitVarInsn(ASTORE, 6);  // 6: methodHandle = null

            Label l6 = new Label();
            mv.visitLabel(l6);
            mv.visitLineNumber(21, l6);

            // int length = split[3].length()
            mv.visitVarInsn(ALOAD, 3);  // 3: split
            mv.visitInsn(ICONST_3);
            mv.visitInsn(AALOAD);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
            mv.visitVarInsn(ISTORE, 7);  // 7: length = ...

            Label l7 = new Label();
            mv.visitLabel(l7);
            mv.visitLineNumber(23, l7);

            // if (length <= 2) {
            mv.visitVarInsn(ILOAD, 7);  // 7: length
            mv.visitInsn(ICONST_2);  // 比較する値 = 2
            Label l8 = new Label();
            mv.visitJumpInsn(IF_ICMPGT, l8);  // length <= 2 の場合は l8 へジャンプ

            // --- 分岐: 仮想/静的メソッド呼び出し ---
            Label l9 = new Label();
            mv.visitLabel(l9);
            mv.visitLineNumber(24, l9);

            // MethodType methodType = MethodType.fromMethodDescriptorString(split[2], classIn.getClassLoader());
            mv.visitVarInsn(ALOAD, 3);  // 3: split
            mv.visitInsn(ICONST_2);  // 比較する値 = 2
            mv.visitInsn(AALOAD);  // split[2] の値を取得

            mv.visitLdcInsn(Type.getType("L" + className + ";"));
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false);
            mv.visitMethodInsn(
                    INVOKESTATIC,
                    "java/lang/invoke/MethodType",
                    "fromMethodDescriptorString",
                    "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;",
                    false
            );
            mv.visitVarInsn(ASTORE, 8);  // 8: methodType = ...

            Label l10 = new Label();
            mv.visitLabel(l10);
            mv.visitLineNumber(26, l10);

            mv.visitVarInsn(ILOAD, 7);  // 7: length
            mv.visitInsn(ICONST_2);
            Label l11 = new Label();
            mv.visitJumpInsn(IF_ICMPNE, l11);  // length == 2 の場合は l11 へジャンプ

            Label l12 = new Label();
            mv.visitLabel(l12);
            mv.visitLineNumber(27, l12);

            // MethodHandle methodHandle = lookup.findVirtual(classIn, name, methodType);
            mv.visitVarInsn(ALOAD, 0);  // 0: lookup
            mv.visitVarInsn(ALOAD, 4);  // 4: classIn
            mv.visitVarInsn(ALOAD, 5);  // 5: name
            mv.visitVarInsn(ALOAD, 8);  // 8: methodType
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findVirtual", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
            mv.visitVarInsn(ASTORE, 6);  // 6: methodHandle = ...
            Label l13 = new Label();
            mv.visitJumpInsn(GOTO, l13);  // if 本文終わり, (else 後の) l13 へジャンプ

            // else はじまり
            mv.visitLabel(l11);
            mv.visitLineNumber(29, l11);

            // スタックに残っている値を確認, VerifyError を防ぐためのフレームを設定
            mv.visitFrame(Opcodes.F_FULL, 9, new Object[]{
                                  "java/lang/invoke/MethodHandles$Lookup",
                                  "java/lang/String",
                                  "java/lang/invoke/MethodType",
                                  "[Ljava/lang/String;",
                                  "java/lang/Class",
                                  "java/lang/String",
                                  "java/lang/invoke/MethodHandle",
                                  Opcodes.INTEGER,
                                  "java/lang/invoke/MethodType"
                          }, 0, new Object[]{}
            );

            // methodHandle = lookup.findStatic(classIn, name, methodType);
            mv.visitVarInsn(ALOAD, 0);  // 0: lookup
            mv.visitVarInsn(ALOAD, 4); // 4: classIn
            mv.visitVarInsn(ALOAD, 5);  // 5: name
            mv.visitVarInsn(ALOAD, 8);  // 8: methodType
            mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                               "java/lang/invoke/MethodHandles$Lookup",
                               "findStatic",
                               "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                               false
            );
            mv.visitVarInsn(ASTORE, 6); // 6: methodHandle = ...

            mv.visitLabel(l13);
            mv.visitLineNumber(31, l13);
            // F_CHOP: スタックから 1 つのフレームを削除
            mv.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
            Label l14 = new Label();
            mv.visitJumpInsn(GOTO, l14);
            // else 終わり, l14 へジャンプ

            // --- 分岐: Getter/Setter パターン ---
            mv.visitLabel(l8);
            mv.visitLineNumber(32, l8);

            // スタックに残っている値を確認, VerifyError を防ぐためのフレームを設定
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            // typeLookup = FIELD_TARGETS[Integer.parseInt(split[2])];
            mv.visitFieldInsn(GETSTATIC, className, typeFieldName, typeFieldType);
            mv.visitVarInsn(ALOAD, 3); // 3: split
            mv.visitInsn(ICONST_2); // 比較する値 = 2
            mv.visitInsn(AALOAD); // split[2] の値を取得
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false);
            mv.visitInsn(AALOAD); // TODO: これなに?形がおかしい
            mv.visitVarInsn(ASTORE, 8); // 8: typeLookup = ...

            Label l15 = new Label();
            mv.visitLabel(l15);
            mv.visitLineNumber(34, l15);

            // if (length == 3) {
            mv.visitVarInsn(ILOAD, 7);  // 7: length
            mv.visitInsn(ICONST_3);  // 比較する値 = 3
            Label l16 = new Label();
            mv.visitJumpInsn(IF_ICMPNE, l16);  // length == 3 の場合は l16 へジャンプ

            Label l17 = new Label();
            mv.visitLabel(l17);
            mv.visitLineNumber(35, l17);

            // methodHandle = lookup.findGetter(classIn, name, typeLookup);
            mv.visitVarInsn(ALOAD, 0);  // 0: lookup
            mv.visitVarInsn(ALOAD, 4);  // 4: classIn
            mv.visitVarInsn(ALOAD, 5);  // 5: name
            mv.visitVarInsn(ALOAD, 8);  // 8: typeLookup
            mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "java/lang/invoke/MethodHandles$Lookup",
                    "findGetter",
                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;",
                    false
            );
            mv.visitVarInsn(ASTORE, 6);  // 6: methodHandle = ...

            mv.visitJumpInsn(GOTO, l14);  // if 本文終わり, (else 後の) l14 へジャンプ
            mv.visitLabel(l16);
            mv.visitLineNumber(36, l16);

            // スタックに残っている値を確認, VerifyError を防ぐためのフレームを設定
            mv.visitFrame(Opcodes.F_APPEND, 1, new Object[]{"java/lang/Class"}, 0, null);

            // else if (length == 4) {
            mv.visitVarInsn(ILOAD, 7);  // 7: length
            mv.visitInsn(ICONST_4);  // 比較する値 = 4
            Label l18 = new Label();
            mv.visitJumpInsn(IF_ICMPNE, l18);  // length == 4 の場合は l18 へジャンプ

            Label l19 = new Label();
            mv.visitLabel(l19);
            mv.visitLineNumber(37, l19);

            // methodHandle = lookup.findStaticGetter(classIn, name, typeLookup);
            mv.visitVarInsn(ALOAD, 0);  // 0: lookup
            mv.visitVarInsn(ALOAD, 4);  // 4: classIn
            mv.visitVarInsn(ALOAD, 5);  // 5: name
            mv.visitVarInsn(ALOAD, 8);  // 8: typeLookup
            mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "java/lang/invoke/MethodHandles$Lookup",
                    "findStaticGetter",
                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;",
                    false
            );
            mv.visitVarInsn(ASTORE, 6); // 6: methodHandle = ...

            mv.visitJumpInsn(GOTO, l14);  // if 本文終わり, (else 後の) l14 へジャンプ

            mv.visitLabel(l18);
            mv.visitLineNumber(38, l18);

            // スタックに残っている値を確認, VerifyError を防ぐためのフレームを設定
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);

            // else if (length == 5) {
            mv.visitVarInsn(ILOAD, 7);  // 7: length
            mv.visitInsn(ICONST_5); // 比較する値 = 5
            Label l20 = new Label();
            mv.visitJumpInsn(IF_ICMPNE, l20);  // length == 5 の場合は l20 へジャンプ

            Label l21 = new Label();
            mv.visitLabel(l21);
            mv.visitLineNumber(39, l21);

            // methodHandle = lookup.findSetter(classIn, name, typeLookup);
            mv.visitVarInsn(ALOAD, 0);  // 0: lookup
            mv.visitVarInsn(ALOAD, 4);  // 4: classIn
            mv.visitVarInsn(ALOAD, 5);  // 5: name
            mv.visitVarInsn(ALOAD, 8);  // 8: typeLookup
            mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "java/lang/invoke/MethodHandles$Lookup",
                    "findSetter",
                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;",
                    false
            );
            mv.visitVarInsn(ASTORE, 6);  // 6: methodHandle = ...

            mv.visitJumpInsn(GOTO, l14);  // if 本文終わり, (else 後の) l14 へジャンプ

            mv.visitLabel(l20);
            mv.visitLineNumber(41, l20);

            // スタックに残っている値を確認, VerifyError を防ぐためのフレームを設定
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            // else {
            // methodHandle = lookup.findStaticSetter(classIn, name, typeLookup);
            mv.visitVarInsn(ALOAD, 0);  // 0: lookup
            mv.visitVarInsn(ALOAD, 4);  // 4: classIn
            mv.visitVarInsn(ALOAD, 5);  // 5: name
            mv.visitVarInsn(ALOAD, 8);  // 8: typeLookup
            mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                               "java/lang/invoke/MethodHandles$Lookup",
                    "findStaticSetter",
                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;",
                    false
            );
            mv.visitVarInsn(ASTORE, 6);  // 6: methodHandle = ...


            mv.visitLabel(l14);
            mv.visitLineNumber(45, l14);

            // スタックに残っている値を確認, VerifyError を防ぐためのフレームを設定
            mv.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);

            // methodHandle を ConstantCallSite として返す。
            // new ConstantCallSite(methodHandle);
            mv.visitTypeInsn(NEW, "java/lang/invoke/ConstantCallSite");
            mv.visitInsn(DUP);  // スタックに ConstantCallSite のインスタンスを生成するための空きスペースを確保
            mv.visitVarInsn(ALOAD, 6); // 6: methodHandle
            mv.visitMethodInsn(
                    INVOKESPECIAL,
                               "java/lang/invoke/ConstantCallSite",
                    "<init>",
                    "(Ljava/lang/invoke/MethodHandle;)V",
                    false
            );


            mv.visitLabel(l1);
            mv.visitInsn(ARETURN);

            // catch ブロックここから

            mv.visitLabel(l2);
            mv.visitLineNumber(46, l2);

            // スタックに残っている値を確認, VerifyError を防ぐためのフレームを設定
            mv.visitFrame(Opcodes.F_FULL, 3, new Object[]{
                    "java/lang/invoke/MethodHandles$Lookup",
                    "java/lang/String",
                    "java/lang/invoke/MethodType"
            }, 1, new Object[]{"java/lang/Exception"});
            mv.visitVarInsn(ASTORE, 3);  // 3: ex = Exception

            Label l22 = new Label();
            mv.visitLabel(l22);
            mv.visitLineNumber(47, l22);

            // ex.printStackTrace();
            mv.visitVarInsn(ALOAD, 3);  // 3: ex
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Exception", "printStackTrace", "()V", false);

            Label l23 = new Label();
            mv.visitLabel(l23);
            mv.visitLineNumber(48, l23);

            // return null;
            mv.visitInsn(ACONST_NULL);  // スタックに null を積む, 例外が発生した場合は null を返すため
            mv.visitInsn(ARETURN);  // null を返す

            // ↓catch ブロックの終了
            Label l24 = new Label();
            mv.visitLabel(l24);

            // ローカル変数の定義, catch ブロックのみに行きてるやつら


            mv.visitLocalVariable("methodDesc", "Ljava/lang/invoke/MethodType;", null, l10, l13, 8);
            mv.visitLocalVariable("typeLookup", "Ljava/lang/Class;", "Ljava/lang/Class<*>;", l15, l14, 8);
            mv.visitLocalVariable("split", "[Ljava/lang/String;", null, l3, l2, 3);

            // catch ブロックが始まる前にスコープ・アウトする奴ら
            mv.visitLocalVariable("classIn", "Ljava/lang/Class;", "Ljava/lang/Class<*>;", l4, l2, 4);
            mv.visitLocalVariable("name", "Ljava/lang/String;", null, l5, l2, 5);
            mv.visitLocalVariable("methodHandle", "Ljava/lang/invoke/MethodHandle;", null, l6, l2, 6);
            mv.visitLocalVariable("length", "I", null, l7, l2, 7);
            mv.visitLocalVariable("ex", "Ljava/lang/Exception;", null, l22, l24, 3);
            mv.visitLocalVariable("lookup", "Ljava/lang/invoke/MethodHandles$Lookup;", null, l0, l24, 0);
            mv.visitLocalVariable("s", "Ljava/lang/String;", null, l0, l24, 1);
            mv.visitLocalVariable("methodType", "Ljava/lang/invoke/MethodType;", null, l0, l24, 2);

            mv.visitMaxs(4, 9);
            mv.visitEnd();
        }

        return mv;
    }

    @Override
    public ObfuscationTransformer getType()
    {
        return ObfuscationTransformer.INVOKE_DYNAMIC;
    }

}
