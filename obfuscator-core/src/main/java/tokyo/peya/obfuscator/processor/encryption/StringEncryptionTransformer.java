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

package tokyo.peya.obfuscator.processor.encryption;

import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import tokyo.peya.obfuscator.IClassTransformer;
import tokyo.peya.obfuscator.JarObfuscator;
import tokyo.peya.obfuscator.ProcessorCallback;
import tokyo.peya.obfuscator.annotations.ObfuscationTransformer;
import tokyo.peya.obfuscator.configuration.DeprecationLevel;
import tokyo.peya.obfuscator.configuration.ValueManager;
import tokyo.peya.obfuscator.configuration.values.BooleanValue;
import tokyo.peya.obfuscator.configuration.values.EnabledValue;
import tokyo.peya.obfuscator.processor.encryption.algorithms.AESEncryptionAlgorithm;
import tokyo.peya.obfuscator.processor.encryption.algorithms.BlowfishEncryptionAlgorithm;
import tokyo.peya.obfuscator.processor.encryption.algorithms.DESEncryptionAlgorithm;
import tokyo.peya.obfuscator.processor.encryption.algorithms.XOREncryptionAlgorithm;
import tokyo.peya.obfuscator.utils.NameUtils;
import tokyo.peya.obfuscator.utils.NodeUtils;
import tokyo.peya.obfuscator.utils.StringManipulationUtils;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j(topic = "Processor/StringEncryption")
public class StringEncryptionTransformer implements IClassTransformer
{
    public static final String MAGICNUMBER_START = "\u00e4";
    private static final String MAGICNUMBER_SPLIT = "\u00f6";
    private static final String MAGICNUMBER_END = "\u00fc";
    private static final String PROCESSOR_NAME = "StringEncryption";
    private static final Random random = new Random();
    private static final EnabledValue V_ENABLED = new EnabledValue(PROCESSOR_NAME, DeprecationLevel.AVAILABLE, true);
    private static final BooleanValue V_HIDE_STRINGS = new BooleanValue(PROCESSOR_NAME, "HideStrings", "Hide strings in SourceFile. Might break after editing the SourceFile", DeprecationLevel.SOME_DEPRECATION, false);
    private static final BooleanValue V_AES = new BooleanValue(PROCESSOR_NAME, "AES", DeprecationLevel.SOME_DEPRECATION, false);

    static
    {
        ValueManager.registerClass(StringEncryptionTransformer.class);
    }

    private final JarObfuscator inst;
    private final List<? extends IStringEncryptionAlgorithm> algorithms;

    public StringEncryptionTransformer(JarObfuscator inst)
    {
        this.inst = inst;
        this.algorithms = getAlgorithms();
    }

    private static void hideStrings(ClassNode cn, MethodNode... methods)
    {
        cn.sourceFile = null;
        cn.sourceDebug = null;
        String fieldName = NameUtils.generateFieldName(cn);
        HashMap<Integer, String> hiddenStrings = new HashMap<>();
        int slot = 0;

        int stringLength = 0;

        int methodCount = 0;
        MethodNode methodNode = null;

        for (MethodNode method : methods)
        {
            boolean hide = false;
            for (AbstractInsnNode abstractInsnNode : method.instructions.toArray())
            {
                if (abstractInsnNode instanceof LdcInsnNode)
                {
                    LdcInsnNode ldc = (LdcInsnNode) abstractInsnNode;
                    if (ldc.cst instanceof String && ((String) ldc.cst).length() < 500)
                    {
                        if (stringLength + ((String) (ldc).cst).length() > 498)
                            break;

                        InsnList insnList = new InsnList();
                        insnList.add(new FieldInsnNode(Opcodes.GETSTATIC, cn.name, fieldName, "[Ljava/lang/String;"));
                        insnList.add(NodeUtils.generateIntPush(slot));
                        insnList.add(new InsnNode(Opcodes.AALOAD));
                        method.instructions.insert(abstractInsnNode, insnList);
                        method.instructions.remove(abstractInsnNode);
                        slot++;
                        stringLength += ((String) ldc.cst).length() + 1;
                        hiddenStrings.put(slot, (String) ldc.cst);
                        hide = true;
                    }
                }
            }
            if (hide)
            {
                methodCount++;
                methodNode = method;
            }
        }

        if (methodCount == 1)
        {
            InsnList toAdd = new InsnList();
            toAdd.add(new InsnNode(Opcodes.ACONST_NULL));
            toAdd.add(new FieldInsnNode(Opcodes.PUTSTATIC, cn.name, fieldName, "[Ljava/lang/String;"));

            NodeUtils.insertOn(methodNode.instructions, insnNode -> insnNode.getOpcode() >= Opcodes.IRETURN && insnNode.getOpcode() <= Opcodes.RETURN, toAdd);
        }

        StringBuilder sb = new StringBuilder(MAGICNUMBER_START);
        for (String s : hiddenStrings.values())
        {
            sb.append(s);
            sb.append(MAGICNUMBER_SPLIT);
        }
        sb.append(MAGICNUMBER_END);

        cn.sourceFile = sb.toString();

        if (slot > 0)
        {
            cn.fields.add(new FieldNode(((cn.access & Opcodes.ACC_INTERFACE) != 0 ? Opcodes.ACC_PUBLIC: Opcodes.ACC_PRIVATE) | Opcodes.ACC_STATIC, fieldName, "[Ljava/lang/String;", null, null));
            MethodNode clInit = NodeUtils.getMethod(cn, "<clinit>");
            if (clInit == null)
            {
                clInit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, new String[0]);
                clInit.instructions.add(new InsnNode(Opcodes.RETURN));
                cn.methods.add(clInit);
            }

            LabelNode start = new LabelNode(new Label());
            LabelNode end = new LabelNode(new Label());
            InsnList toAdd = new InsnList();
            toAdd.add(start);
            toAdd.add(new TypeInsnNode(Opcodes.NEW, "java/lang/Exception"));
            toAdd.add(new InsnNode(Opcodes.DUP));
            toAdd.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Exception", "<init>", "()V", false));
            toAdd.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Exception", "getStackTrace", "()[Ljava/lang/StackTraceElement;", false));
            toAdd.add(new InsnNode(Opcodes.ICONST_0));
            toAdd.add(new InsnNode(Opcodes.AALOAD));
            toAdd.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StackTraceElement", "getFileName", "()Ljava/lang/String;", false));
            toAdd.add(new VarInsnNode(Opcodes.ASTORE, 0));
            toAdd.add(new VarInsnNode(Opcodes.ALOAD, 0));
            toAdd.add(new VarInsnNode(Opcodes.ALOAD, 0));
            toAdd.add(new LdcInsnNode(MAGICNUMBER_START));
            toAdd.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "indexOf", "(Ljava/lang/String;)I", false));
            toAdd.add(new InsnNode(Opcodes.ICONST_1));
            toAdd.add(new InsnNode(Opcodes.IADD));
            toAdd.add(new VarInsnNode(Opcodes.ALOAD, 0));
            toAdd.add(new LdcInsnNode(MAGICNUMBER_END));
            toAdd.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "lastIndexOf", "(Ljava/lang/String;)I", false));
            toAdd.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false));
            toAdd.add(new LdcInsnNode(MAGICNUMBER_SPLIT));
            toAdd.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false));
            toAdd.add(new FieldInsnNode(Opcodes.PUTSTATIC, cn.name, fieldName, "[Ljava/lang/String;"));
            toAdd.add(end);

            MethodNode generateStrings = new MethodNode(((cn.access & Opcodes.ACC_INTERFACE) != 0 ? Opcodes.ACC_PUBLIC: Opcodes.ACC_PRIVATE) | Opcodes.ACC_STATIC, NameUtils.generateMethodName(cn, "()V"), "()V", null, new String[0]);
            generateStrings.instructions = toAdd;
            generateStrings.instructions.add(new InsnNode(Opcodes.RETURN));
            generateStrings.maxStack = 4;
            generateStrings.maxLocals = 4;
            cn.methods.add(generateStrings);

            clInit.instructions.insertBefore(clInit.instructions.getFirst(), NodeUtils.methodCall(cn, generateStrings));
        }
    }

    @Override
    public void process(ProcessorCallback callback, ClassNode node)
    {
        if (!V_ENABLED.get())
            return;
        else if (Modifier.isInterface(node.access))
            return;

        boolean hideStrings = V_HIDE_STRINGS.get();

        String encryptedStringsFieldName = NameUtils.generateFieldName(node);
        String[] constantReferences = createStringConstantReferences(node, encryptedStringsFieldName);

        int constants = constantReferences.length;
        if (constants == 0)
            return;

        boolean isInterface = (node.access & Opcodes.ACC_INTERFACE) != 0;
        node.fields.add(new FieldNode(
                        (isInterface ? Opcodes.ACC_PUBLIC: Opcodes.ACC_PRIVATE)  // インターフェースの場合はプライベートにできない
                                | (node.version > Opcodes.V1_8 ? 0: Opcodes.ACC_FINAL)
                                | Opcodes.ACC_STATIC, encryptedStringsFieldName,
                        "[Ljava/lang/String;",
                        null,
                        null
                )
        );

        HashMap<IStringEncryptionAlgorithm, String> encryptionMethodMap = new HashMap<>();
        for (IStringEncryptionAlgorithm algorithm : this.algorithms)
            encryptionMethodMap.put(
                    algorithm,
                    NameUtils.generateMethodName(node, "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
            );

        InsnList encryptedStringConstants = this.createEncryptedStringConstants(
                node,
                constants,
                encryptedStringsFieldName,
                encryptionMethodMap,
                constantReferences
        );

        MethodNode generateStrings = createInitStringsMethod(node, encryptedStringConstants);
        node.methods.add(generateStrings);
        NodeUtils.addInvokeOnClassInitMethod(node, generateStrings);

        if (hideStrings)
            hideStrings(node, generateStrings);

        // 実際に復号化するメソッドを追加
        deployDecryptionMethods(node, encryptionMethodMap);
    }

    private static MethodNode createInitStringsMethod(ClassNode node, InsnList stringArrayInstructions)
    {
        MethodNode generateStrings = new MethodNode(
                ((node.access & Opcodes.ACC_INTERFACE) != 0 ? Opcodes.ACC_PUBLIC: Opcodes.ACC_PRIVATE)
                        | Opcodes.ACC_STATIC,
                NameUtils.generateMethodName(node, "()V"),
                "()V",
                null,
                new String[0]
        );

        generateStrings.instructions = stringArrayInstructions;
        generateStrings.instructions.add(new InsnNode(Opcodes.RETURN));
        generateStrings.maxStack = 6;

        return generateStrings;
    }

    private InsnList createEncryptedStringConstants(
            ClassNode node,
            int constants,
            String encryptedStringsFieldName,
            Map<IStringEncryptionAlgorithm, String> encryptionMethodMap,
            String[] constantReferences)
    {
        InsnList instructions = new InsnList();

        // 空の配列 (constants 個 ) を生成
        instructions.add(NodeUtils.generateIntPush(constants));
        instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"));
        instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, encryptedStringsFieldName, "[Ljava/lang/String;"));

        for (int j = 0; j < constants; j++)
        {
            IStringEncryptionAlgorithm processor = this.algorithms.get(random.nextInt(this.algorithms.size()));
            String decryptionKey = StringManipulationUtils.generateString(5);

            String decrypterName = encryptionMethodMap.get(processor);

            instructions.add(generateDecrypterInvocation(
                    node,
                    j,
                    encryptedStringsFieldName,
                    decryptionKey,
                    decrypterName,
                    processor.encrypt(constantReferences[j], decryptionKey)
            ));
        }

        return instructions;
    }

    private static InsnList generateDecrypterInvocation(ClassNode node,
                                                        int constantNumber,
                                                        String encryptedStringsField,
                                                        String decryptionKey,
                                                        String processorMethodName,
                                                        String encryptedString)
    {
        InsnList toAdd = new InsnList();

        LabelNode label = new LabelNode(new Label());
        toAdd.add(label);
        toAdd.add(new LineNumberNode(constantNumber, label));
        toAdd.add(new FieldInsnNode(Opcodes.GETSTATIC,
                node.name,
                encryptedStringsField,
                "[Ljava/lang/String;")

        );

        toAdd.add(NodeUtils.generateIntPush(constantNumber));
        toAdd.add(new LdcInsnNode(encryptedString));
        toAdd.add(new LdcInsnNode(decryptionKey));
        toAdd.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                node.name,
                processorMethodName,
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                false)
        );
        toAdd.add(new InsnNode(Opcodes.AASTORE));

        return toAdd;
    }

    private static void deployDecryptionMethods(ClassNode node, HashMap<IStringEncryptionAlgorithm, String> methods)
    {
        for (Map.Entry<IStringEncryptionAlgorithm, String> entry : methods.entrySet())
        {
            try
            {
                MethodNode method = NodeUtils.getMethod(NodeUtils.toNode(entry.getKey().getClass()), "decrypt");

                if (method != null)
                {
                    method.access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
                    method.name = entry.getValue();
                    node.methods.add(method);
                }
                else
                    throw new IllegalStateException("Could not find decryption method for " + entry.getKey().getClass().getName());
            }
            catch (IOException e)
            {
                throw new IllegalStateException("Could not find decryption method for " + entry.getKey().getClass().getName(), e);
            }
        }
    }

    private static String[] createStringConstantReferences(ClassNode node, String referenceName)
    {
        LinkedList<String> strings = new LinkedList<>();

        int index = 0;
        for (MethodNode method : node.methods)
            for (AbstractInsnNode abstractInsnNode : method.instructions.toArray())
            {
                if (!(abstractInsnNode instanceof LdcInsnNode))
                    continue;

                LdcInsnNode insnNode = (LdcInsnNode) abstractInsnNode;
                if (!(insnNode.cst instanceof String))
                    continue;

                String string = (String) insnNode.cst;
                if (string.length() >= 500)
                {
                    log.warn("A constant string value in class " + node.name +
                            " is too long (\"" + string.substring(0, 10) +
                            "...\", length: " + string.length() +
                            "), skipping");
                    continue;
                }

                InsnList insnList = new InsnList();

                insnList.add(new FieldInsnNode(Opcodes.GETSTATIC, node.name, referenceName, "[Ljava/lang/String;"));
                insnList.add(NodeUtils.generateIntPush(index));
                insnList.add(new InsnNode(Opcodes.AALOAD));

                method.instructions.insert(abstractInsnNode, insnList);
                method.instructions.remove(abstractInsnNode);
                strings.add(string);
                index++;
            }

        return strings.toArray(new String[0]);
    }

    @Override
    public ObfuscationTransformer getType()
    {
        return ObfuscationTransformer.STRING_ENCRYPTION;
    }

    private static List<? extends IStringEncryptionAlgorithm> getAlgorithms()
    {
        List<IStringEncryptionAlgorithm> algorithms = new ArrayList<>();

        algorithms.add(new XOREncryptionAlgorithm());
        algorithms.add(new DESEncryptionAlgorithm());
        algorithms.add(new BlowfishEncryptionAlgorithm());

        if (V_AES.get())
            algorithms.add(new AESEncryptionAlgorithm());

        return algorithms;
    }

}
