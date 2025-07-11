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

package tokyo.peya.obfuscator.processor.strings;

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
import tokyo.peya.obfuscator.IClassTransformer;
import tokyo.peya.obfuscator.Obfuscator;
import tokyo.peya.obfuscator.ProcessorCallback;
import tokyo.peya.obfuscator.annotations.ObfuscationTransformer;
import tokyo.peya.obfuscator.configuration.DeprecationLevel;
import tokyo.peya.obfuscator.configuration.ValueManager;
import tokyo.peya.obfuscator.configuration.values.BooleanValue;
import tokyo.peya.obfuscator.configuration.values.EnabledValue;
import tokyo.peya.obfuscator.processor.strings.algorithms.AESEncryptionAlgorithm;
import tokyo.peya.obfuscator.processor.strings.algorithms.BlowfishEncryptionAlgorithm;
import tokyo.peya.obfuscator.processor.strings.algorithms.DESEncryptionAlgorithm;
import tokyo.peya.obfuscator.processor.strings.algorithms.XOREncryptionAlgorithm;
import tokyo.peya.obfuscator.utils.NameUtils;
import tokyo.peya.obfuscator.utils.NodeUtils;
import tokyo.peya.obfuscator.utils.StringManipulationUtils;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

@Slf4j(topic = "Processor/StringEncryption")
public class StringEncryptionTransformer implements IClassTransformer
{
    private static final String PROCESSOR_NAME = "string_encryption";
    private static final Random random = new Random();
    private static final EnabledValue V_ENABLED = new EnabledValue(
            PROCESSOR_NAME,
            "ui.transformers.string_encryption.description",
            DeprecationLevel.AVAILABLE,
            true
    );
    private static final BooleanValue V_ALGO_AES = new BooleanValue(
            PROCESSOR_NAME,
            "algorithm_aes",
            "ui.transformers.string_encryption.algo_aes",
            DeprecationLevel.AVAILABLE,
            true
    );
    private static final BooleanValue V_ALGO_XOR = new BooleanValue(
            PROCESSOR_NAME,
            "algorithm_xor",
            "ui.transformers.string_encryption.algo_xor",
            DeprecationLevel.AVAILABLE,
            true
    );
    private static final BooleanValue V_ALGO_BLOWFISH = new BooleanValue(
            PROCESSOR_NAME,
            "algorithm_blowfish",
            "ui.transformers.string_encryption.algo_blowfish",
            DeprecationLevel.AVAILABLE,
            true
    );
    private static final BooleanValue V_ALGO_DES = new BooleanValue(
            PROCESSOR_NAME,
            "algorithm_des",
            "ui.transformers.string_encryption.algo_des",
            DeprecationLevel.AVAILABLE,
            true
    );
    private final Obfuscator instance;
    private final List<? extends IStringEncryptionAlgorithm> algorithms;

    private ClassNode decryptionClass;

    static
    {
        ValueManager.registerOwner(PROCESSOR_NAME, "ui.transformers.string_encryption");
        ValueManager.registerClass(StringEncryptionTransformer.class);
    }

    public StringEncryptionTransformer(Obfuscator instance)
    {
        this.instance = instance;
        this.algorithms = getAlgorithms();
    }

    private static ClassNode createDecrypters(String packageName, List<? extends IStringEncryptionAlgorithm> algorithms)
    {
        ClassNode cn = new ClassNode();
        cn.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                NameUtils.getClassName(packageName, "StringDecrypters"),
                null,
                "java/lang/Object",
                null
        );

        for (IStringEncryptionAlgorithm entry : algorithms)
        {
            try
            {
                MethodNode method = NodeUtils.getMethod(NodeUtils.toNode(entry.getClass()), "decrypt");

                if (method != null)
                {
                    method.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
                    method.name = "decrypt" + entry.getName();
                    method.desc = "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";

                    cn.methods.add(method);
                }
                else
                    throw new IllegalStateException("No decryption method found for " + entry.getClass().getName());
            }
            catch (IOException e)
            {
                throw new IllegalStateException("No decryption method found for " + entry.getClass().getName(), e);
            }
        }

        cn.visitEnd();

        return cn;
    }

    private MethodNode createInitStringsMethod(ClassNode node, InsnList stringArrayInstructions)
    {
        MethodNode retrieveStringss = new MethodNode(
                ((node.access & Opcodes.ACC_INTERFACE) != 0 ? Opcodes.ACC_PUBLIC: Opcodes.ACC_PRIVATE)
                        | Opcodes.ACC_STATIC,
                this.instance.getNameProvider().toUniqueMethodName(node, "buildLedger", "()V"),
                "()V",
                null,
                new String[0]
        );

        retrieveStringss.instructions = stringArrayInstructions;
        retrieveStringss.instructions.add(new InsnNode(Opcodes.RETURN));
        retrieveStringss.maxStack = 6;

        return retrieveStringss;
    }

    @Override
    public void process(ProcessorCallback callback, ClassNode node)
    {
        if (!V_ENABLED.get())
            return;
        else if (Modifier.isInterface(node.access))
            return;
        String encryptedStringsFieldName = "stringsLedger";
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

        if (this.decryptionClass == null)
        {
            this.decryptionClass = createDecrypters(NameUtils.getPackageName(node), this.algorithms);
            callback.addClass(this.decryptionClass);
        }


        InsnList instructions = this.createEncryptedStringConstants(
                node,
                constants,
                encryptedStringsFieldName,
                constantReferences
        );

        MethodNode retrieveStrings = this.createInitStringsMethod(node, instructions);
        node.methods.add(retrieveStrings);
        NodeUtils.addInvokeOnClassInitialisation(node, retrieveStrings);
    }

    private InsnList createEncryptedStringConstants(
            ClassNode node,
            int constants,
            String encryptedStringsFieldName,
            String[] constantReferences)
    {

        InsnList instructions = new InsnList();

        // 空の配列 (constants 個 ) を生成
        /// anewarray(count, &fieldArray) {
        instructions.add(NodeUtils.generateIntPush(constants));
        instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"));
        instructions.add(new FieldInsnNode(
                Opcodes.PUTSTATIC,
                node.name,
                encryptedStringsFieldName,
                "[Ljava/lang/String;"
        ));
        /// }

        if (this.algorithms.isEmpty())
        {
            log.warn("No string encryption algorithms are enabled, skipping");
            for (int i = 0; i < constants; i++)
            {
                /// aastore(&arrayField, index, &value)
                instructions.add(new FieldInsnNode(
                        Opcodes.GETSTATIC,
                        node.name,
                        encryptedStringsFieldName,
                        "[Ljava/lang/String;"
                ));
                instructions.add(NodeUtils.generateIntPush(i));
                instructions.add(new LdcInsnNode(constantReferences[i]));
                instructions.add(new InsnNode(Opcodes.AASTORE));
            }

            return instructions;
        }

        for (int j = 0; j < constants; j++)
        {
            // ランダムなアルゴリズムを選択
            IStringEncryptionAlgorithm processor = this.algorithms.get(random.nextInt(this.algorithms.size()));
            String decryptionKey = StringManipulationUtils.retrieveStrings(5);

            MethodNode decrypterMethod = NodeUtils.getMethod(
                    this.decryptionClass,
                    processor.getDecryptMethodName()
            );
            assert decrypterMethod != null;

            instructions.add(generateDecrypterInvocation(
                    node,
                    j,
                    encryptedStringsFieldName,
                    decryptionKey,
                    this.decryptionClass.name,
                    decrypterMethod,
                    processor.encrypt(constantReferences[j], decryptionKey)
            ));
        }

        return instructions;
    }

    @Override
    public ObfuscationTransformer getType()
    {
        return ObfuscationTransformer.STRING_ENCRYPTION;
    }

    private static InsnList generateDecrypterInvocation(ClassNode node,
                                                        int constantNumber,
                                                        String encryptedStringsField,
                                                        String decryptionKey,
                                                        String decrypterMethodOwnerName,
                                                        MethodNode decrypterMethod,
                                                        String encryptedString)
    {
        InsnList toAdd = new InsnList();

        LabelNode label = new LabelNode(new Label());
        toAdd.add(label);
        toAdd.add(new LineNumberNode(constantNumber, label));

        /// aastore(&arrayField, index, &value) {
        toAdd.add(new FieldInsnNode(
                          Opcodes.GETSTATIC,
                          node.name,
                          encryptedStringsField,
                          "[Ljava/lang/String;"
                  )

        );
        toAdd.add(NodeUtils.generateIntPush(constantNumber));

        /// invokestatic(*string, *string) {
        toAdd.add(new LdcInsnNode(encryptedString));
        toAdd.add(new LdcInsnNode(decryptionKey));
        toAdd.add(new MethodInsnNode(
                          Opcodes.INVOKESTATIC,
                          decrypterMethodOwnerName,
                            decrypterMethod.name,
                          "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                          false
                  )
        );
        /// }

        toAdd.add(new InsnNode(Opcodes.AASTORE));
        /// }

        return toAdd;
    }

    private static String[] createStringConstantReferences(ClassNode node, String referenceName)
    {
        LinkedList<String> strings = new LinkedList<>();

        int index = 0;
        for (MethodNode method : node.methods)
            for (AbstractInsnNode abstractInsnNode : method.instructions.toArray())
            {
                if (!(abstractInsnNode instanceof LdcInsnNode insnNode))
                    continue;

                if (!(insnNode.cst instanceof String string))
                    continue;

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

    private static List<? extends IStringEncryptionAlgorithm> getAlgorithms()
    {
        List<IStringEncryptionAlgorithm> algorithms = new ArrayList<>();

        if (V_ALGO_AES.get())
            algorithms.add(new AESEncryptionAlgorithm());
        if (V_ALGO_XOR.get())
            algorithms.add(new XOREncryptionAlgorithm());
        if (V_ALGO_BLOWFISH.get())
            algorithms.add(new BlowfishEncryptionAlgorithm());
        if (V_ALGO_DES.get())
            algorithms.add(new DESEncryptionAlgorithm());

        return algorithms;
    }
}
