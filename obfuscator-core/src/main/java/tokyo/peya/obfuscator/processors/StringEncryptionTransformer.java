/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
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
import tokyo.peya.obfuscator.processors.encryption.string.AESEncryptionAlgorithm;
import tokyo.peya.obfuscator.processors.encryption.string.BlowfishEncryptionAlgorithm;
import tokyo.peya.obfuscator.processors.encryption.string.DESEncryptionAlgorithm;
import tokyo.peya.obfuscator.processors.encryption.string.IStringEncryptionAlgorithm;
import tokyo.peya.obfuscator.processors.encryption.string.XOREncryptionAlgorithm;
import tokyo.peya.obfuscator.utils.NameUtils;
import tokyo.peya.obfuscator.utils.NodeUtils;
import tokyo.peya.obfuscator.utils.StringManipulationUtils;
import tokyo.peya.obfuscator.utils.values.BooleanValue;
import tokyo.peya.obfuscator.utils.values.DeprecationLevel;
import tokyo.peya.obfuscator.utils.values.EnabledValue;
import tokyo.peya.obfuscator.utils.values.ValueManager;
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

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class StringEncryptionTransformer implements IClassTransformer
{
    public static final String MAGICNUMBER_START = "\u00e4";
    private static final String MAGICNUMBER_SPLIT = "\u00f6";
    private static final String MAGICNUMBER_END = "\u00fc";
    private static final String PROCESSOR_NAME = "StringEncryption";
    private static final Random random = new Random();
    private static final EnabledValue V_ENABLED = new EnabledValue(PROCESSOR_NAME, DeprecationLevel.GOOD, true);
    private static final BooleanValue V_HIDE_STRINGS = new BooleanValue(PROCESSOR_NAME, "HideStrings", "Hide strings in SourceFile. Might break after editing the SourceFile", DeprecationLevel.OK, false);
    private static final BooleanValue V_AES = new BooleanValue(PROCESSOR_NAME, "AES", DeprecationLevel.OK, false);

    static
    {
        ValueManager.registerClass(StringEncryptionTransformer.class);
    }

    private final JarObfuscator inst;

    public StringEncryptionTransformer(JarObfuscator inst)
    {
        this.inst = inst;
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
                        {
                            break;
                        }

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

//            System.out.println("Win in " + cn.name);
        }

        StringBuilder sb = new StringBuilder(MAGICNUMBER_START);
        for (String s : hiddenStrings.values())
        {
            sb.append(s);
            sb.append(MAGICNUMBER_SPLIT);
        }
        sb.append(MAGICNUMBER_END);

//        if (cn.sourceFile == null) {
        cn.sourceFile = sb.toString();
//        } else {
//            cn.sourceFile += sb.toString();
//        }

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
//            generateStrings.localVariables.add(new LocalVariableNode(NameUtils.generateLocalVariableName(), "Ljava/lang/String;", null, start, end, 1));
            cn.methods.add(generateStrings);
//            System.out.println("Added shit in " + cn.name);

            clInit.instructions.insertBefore(clInit.instructions.getFirst(), NodeUtils.methodCall(cn, generateStrings));
//            System.out.println(generateStrings.name);
        }
    }

    @Override
    public void process(ProcessorCallback callback, ClassNode node)
    {
        if (!V_ENABLED.get()) return;

//        if (AnnotationUtils.isExcluded(node, this.getType())) return;

        List<IStringEncryptionAlgorithm> algorithmList = new ArrayList<>();

        initAlgorithms(algorithmList);

        boolean hideStrings = V_HIDE_STRINGS.get();

        if (Modifier.isInterface(node.access)) return;

        String stringArrayName = NameUtils.generateFieldName(node);

        HashMap<Integer, String> arrayMap = new HashMap<>();

        int slot = 0;


        for (MethodNode method : node.methods)
        {
            for (AbstractInsnNode abstractInsnNode : method.instructions.toArray())
            {
                if (abstractInsnNode instanceof LdcInsnNode)
                {
                    LdcInsnNode insnNode = (LdcInsnNode) abstractInsnNode;
                    if (insnNode.cst instanceof String && ((String) insnNode.cst).length() < 500)
                    {
                        InsnList insnList = new InsnList();
                        insnList.add(new FieldInsnNode(Opcodes.GETSTATIC, node.name, stringArrayName, "[Ljava/lang/String;"));
                        insnList.add(NodeUtils.generateIntPush(slot));
                        insnList.add(new InsnNode(Opcodes.AALOAD));
//                        String key = StringUtils.generateString(5);
//                        method.instructions.insertBefore(abstractInsnNode, new LdcInsnNode(decrypt(insnNode.cst.toString(), key)));
//                        method.instructions.insertBefore(abstractInsnNode, new LdcInsnNode(key));
//                        method.instructions.insertBefore(abstractInsnNode, new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, decryptMethodName, "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false));
                        method.instructions.insert(abstractInsnNode, insnList);
                        method.instructions.remove(abstractInsnNode);
                        arrayMap.put(slot, (String) insnNode.cst);
                        slot++;
                    }
                }
            }
        }


        HashMap<IStringEncryptionAlgorithm, String> encryptionMethodMap = new HashMap<>();

        if (slot > 0)
        {
            if (arrayMap.size() > 0)
            {
                node.fields.add(new FieldNode(((node.access & Opcodes.ACC_INTERFACE) != 0 ? Opcodes.ACC_PUBLIC: Opcodes.ACC_PRIVATE) | (node.version > Opcodes.V1_8 ? 0: Opcodes.ACC_FINAL) | Opcodes.ACC_STATIC, stringArrayName, "[Ljava/lang/String;", null, null));
                MethodNode clInit = NodeUtils.getMethod(node, "<clinit>");
                if (clInit == null)
                {
                    clInit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, new String[0]);
                    node.methods.add(clInit);
                }
                if (clInit.instructions == null)
                    clInit.instructions = new InsnList();


                InsnList toAdd = new InsnList();

//            if (clInit.instructions.getFirst() == null)
//                clInit.instructions.insert(NodeUtils.generateIntPush(i));
//            else
                toAdd.add(NodeUtils.generateIntPush(slot));

                toAdd.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"));
//            toAdd.insert(new IntInsnNode(Opcodes.NEWARRAY, 0));
                toAdd.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, stringArrayName, "[Ljava/lang/String;"));

                for (int j = 0; j < slot; j++)
                {
                    IStringEncryptionAlgorithm processor = algorithmList.get(random.nextInt(algorithmList.size()));

                    String name;

                    if (!encryptionMethodMap.containsKey(processor))
                    {
                        encryptionMethodMap.put(processor, name = NameUtils.generateMethodName(node, "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
                    }
                    else
                    {
                        name = encryptionMethodMap.get(processor);
                    }

                    LabelNode label = new LabelNode(new Label());
                    toAdd.add(label);
                    toAdd.add(new LineNumberNode(j, label));
                    toAdd.add(new FieldInsnNode(Opcodes.GETSTATIC, node.name, stringArrayName, "[Ljava/lang/String;"));
                    toAdd.add(NodeUtils.generateIntPush(j));
//                toAdd.add(getInstructions(integerList.get(j)));
                    String key = StringManipulationUtils.generateString(5);
                    toAdd.add(new LdcInsnNode(processor.encrypt(arrayMap.get(j), key)));
                    toAdd.add(new LdcInsnNode(key));
//                    System.out.println(name);
                    toAdd.add(new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, name, "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false));

                    toAdd.add(new InsnNode(Opcodes.AASTORE));
                }

                MethodNode generateStrings = new MethodNode(((node.access & Opcodes.ACC_INTERFACE) != 0 ? Opcodes.ACC_PUBLIC: Opcodes.ACC_PRIVATE) | Opcodes.ACC_STATIC, NameUtils.generateMethodName(node, "()V"), "()V", null, new String[0]);
                generateStrings.instructions = toAdd;
                generateStrings.instructions.add(new InsnNode(Opcodes.RETURN));
                generateStrings.maxStack = 6;
                node.methods.add(generateStrings);

                if (clInit.instructions == null || clInit.instructions.getFirst() == null)
                {
                    clInit.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, generateStrings.name, generateStrings.desc, false));
                    clInit.instructions.add(new InsnNode(Opcodes.RETURN));
                }
                else
                {
                    clInit.instructions.insertBefore(clInit.instructions.getFirst(), new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, generateStrings.name, generateStrings.desc, false));
                }

                if (hideStrings)
                    hideStrings(node, generateStrings);

            }
        }

        for (Map.Entry<IStringEncryptionAlgorithm, String> iStringEncryptionAlgorithmStringEntry : encryptionMethodMap.entrySet())
        {
            try
            {
                MethodNode method = NodeUtils.getMethod(NodeUtils.toNode(iStringEncryptionAlgorithmStringEntry.getKey().getClass().getName()), "decrypt");

                if (method != null)
                {
                    method.access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
                    method.name = iStringEncryptionAlgorithmStringEntry.getValue();
                    node.methods.add(method);
                }
                else
                {
                    throw new Error("Decryption method of " + iStringEncryptionAlgorithmStringEntry.getKey().getClass().getSimpleName() + " wasn't found");
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

    }

    @Override
    public ObfuscationTransformer getType()
    {
        return ObfuscationTransformer.STRING_ENCRYPTION;
    }

    private void initAlgorithms(List<IStringEncryptionAlgorithm> algorithmList)
    {
        algorithmList.clear();

        algorithmList.add(new XOREncryptionAlgorithm());
        algorithmList.add(new DESEncryptionAlgorithm());
        algorithmList.add(new BlowfishEncryptionAlgorithm());

        if (V_AES.get()) algorithmList.add(new AESEncryptionAlgorithm());
    }

}
