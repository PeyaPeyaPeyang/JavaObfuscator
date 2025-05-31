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
    private static final String PROCESSOR_NAME = "string_encryption";
    private static final Random random = new Random();
    private static final EnabledValue V_ENABLED = new EnabledValue(PROCESSOR_NAME, "ui.transformers.string_encryption.description", DeprecationLevel.AVAILABLE, true);
    private static final BooleanValue V_ALGO_AES = new BooleanValue(PROCESSOR_NAME, "algorithm_aes", "ui.transformers.string_encryption.algo_aes", DeprecationLevel.AVAILABLE, true);
    private static final BooleanValue V_ALGO_XOR = new BooleanValue(PROCESSOR_NAME, "algorithm_xor", "ui.transformers.string_encryption.algo_xor", DeprecationLevel.AVAILABLE, true);
    private static final BooleanValue V_ALGO_BLOWFISH = new BooleanValue(PROCESSOR_NAME, "algorithm_blowfish", "ui.transformers.string_encryption.algo_blowfish", DeprecationLevel.AVAILABLE, true);
    private static final BooleanValue V_ALGO_DES = new BooleanValue(PROCESSOR_NAME, "algorithm_des", "ui.transformers.string_encryption.algo_des", DeprecationLevel.AVAILABLE, true);


    static
    {
        ValueManager.registerOwner(PROCESSOR_NAME, "ui.transformers.string_encryption");
        ValueManager.registerClass(StringEncryptionTransformer.class);
    }

    private final Obfuscator instance;
    private final List<? extends IStringEncryptionAlgorithm> algorithms;

    public StringEncryptionTransformer(Obfuscator instance)
    {
        this.instance = instance;
        this.algorithms = getAlgorithms();
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
                        node.name,
                        processorMethodName,
                        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                        false
                )
        );
        /// }

        toAdd.add(new InsnNode(Opcodes.AASTORE));
        /// }

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

        HashMap<IStringEncryptionAlgorithm, String> encryptionMethodMap = new HashMap<>();
        for (IStringEncryptionAlgorithm algorithm : this.algorithms)
            encryptionMethodMap.put(
                    algorithm,
                    this.instance.getNameProvider().toUniqueMethodName(
                            node,
                            "decrypt" + algorithm.getName(),
                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
                    )
            );

        InsnList encryptedStringConstants = this.createEncryptedStringConstants(
                node,
                constants,
                encryptedStringsFieldName,
                encryptionMethodMap,
                constantReferences
        );

        MethodNode retrieveStringss = this.createInitStringsMethod(node, encryptedStringConstants);
        node.methods.add(retrieveStringss);
        NodeUtils.addInvokeOnClassInitialisation(node, retrieveStringss);

        // 実際に復号化するメソッドを追加
        deployDecryptionMethods(node, encryptionMethodMap);
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
        /// anewarray(count, &fieldArray) {
        instructions.add(NodeUtils.generateIntPush(constants));
        instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"));
        instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, encryptedStringsFieldName, "[Ljava/lang/String;"));
        /// }

        if (this.algorithms.isEmpty())
        {
            log.warn("No string encryption algorithms are enabled, skipping");
            for (int i = 0; i < constants; i++)
            {
                /// aastore(&arrayField, index, &value)
                instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, node.name, encryptedStringsFieldName, "[Ljava/lang/String;"));
                instructions.add(NodeUtils.generateIntPush(i));
                instructions.add(new LdcInsnNode(constantReferences[i]));
                instructions.add(new InsnNode(Opcodes.AASTORE));
            }

            return instructions;
        }

        for (int j = 0; j < constants; j++)
        {
            IStringEncryptionAlgorithm processor = this.algorithms.get(random.nextInt(this.algorithms.size()));
            String decryptionKey = StringManipulationUtils.retrieveStrings(5);

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

    @Override
    public ObfuscationTransformer getType()
    {
        return ObfuscationTransformer.STRING_ENCRYPTION;
    }

}
