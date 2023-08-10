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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import tokyo.peya.obfuscator.IClassTransformer;
import tokyo.peya.obfuscator.ProcessorCallback;
import tokyo.peya.obfuscator.annotations.ObfuscationTransformer;
import tokyo.peya.obfuscator.configuration.DeprecationLevel;
import tokyo.peya.obfuscator.configuration.ValueManager;
import tokyo.peya.obfuscator.configuration.values.EnabledValue;
import tokyo.peya.obfuscator.configuration.values.StringValue;
import tokyo.peya.obfuscator.utils.NameUtils;
import tokyo.peya.obfuscator.utils.NodeUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Slf4j(topic = "Processor/String/HideStrings")
public class HideStringsTransformer implements IClassTransformer
{
    private static final String PROCESSOR_NAME = "HideStrings";
    public static final StringValue V_MAGIC_NUMBER = new StringValue(PROCESSOR_NAME, "Magigic number: Start",
            "Begin marker of the hided string",
            DeprecationLevel.AVAILABLE, "", 1
    );
    private static final EnabledValue V_ENABLED = new EnabledValue(PROCESSOR_NAME,
            "This processor will hide strings in your code in class's debug info. It might break after editing the SourceFile field in the class.",
            DeprecationLevel.AVAILABLE, true
    );
    private static final StringValue V_MAGIC_NUMBER_SPLIT = new StringValue(PROCESSOR_NAME, "Split of the magic number",
            "Delimiter of the hided strings",
            DeprecationLevel.AVAILABLE, "", 1
    );
    private static final StringValue V_MAGIC_NUMBER_END = new StringValue(PROCESSOR_NAME, "End of the magic number",
            "End marker of the hided string",
            DeprecationLevel.AVAILABLE, "", 1
    );
    private static final int MAX_ONE_STRING_LENGTH = 500;
    private static final int MAX_TOTAL_STRING_LENGTH = 65535;

    static
    {
        ValueManager.registerClass(HideStringsTransformer.class);
    }

    private static MethodNode getGenerateStringsMethod(ClassNode cn, String ledgerFieldName,
                                                       String magicNumber, String magicNumberSplit, String magicNumberEnd)
    {

        MethodNode generateStrings = new MethodNode(
                ((cn.access & Opcodes.ACC_INTERFACE) != 0 ? Opcodes.ACC_PUBLIC: Opcodes.ACC_PRIVATE) | Opcodes.ACC_STATIC,
                NameUtils.generateMethodName(cn, "()V"),
                "()V",
                null,
                new String[0]
        );

        LabelNode start = new LabelNode(new Label());
        LabelNode end = new LabelNode(new Label());
        InsnList toAdd = new InsnList();
        toAdd.add(start);

        /// astore(&object = [Lj//String;, &var = 0) {
        /// invokevirtual(&object = j/l/StackTraceElement, method = getFileName, descriptor = ()Ljava/lang/String;) {
        /// aaload (&arrayref = [Lj/l/StackTraceElement;, &index = 0) {
        /// invokevirtual(&object = j/l/Exception, method = getStackTrace, descriptor = ()[Lj/l/StackTraceElement;) {
        /// invokespecial(&object = j/l/Exception, method = <init>, descriptor = ()V) {

        /// dup(value) {
        toAdd.add(new TypeInsnNode(Opcodes.NEW, "java/lang/Exception"));
        toAdd.add(new InsnNode(Opcodes.DUP));
        /// }

        toAdd.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                        "java/lang/Exception", "<init>", "()V", false
                )
        );
        /// }

        toAdd.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                        "java/lang/Exception", "getStackTrace", "()[Ljava/lang/StackTraceElement;", false
                )
        );
        /// }

        /// iconst_0 {
        toAdd.add(new InsnNode(Opcodes.ICONST_0));
        /// }

        toAdd.add(new InsnNode(Opcodes.AALOAD));
        /// }

        toAdd.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                        "java/lang/StackTraceElement", "getFileName", "()Ljava/lang/String;", false
                )
        );
        /// }

        toAdd.add(new VarInsnNode(Opcodes.ASTORE, 0));
        /// };

        /// putstatic(&object = j/l/Class, field = ledgerFieldName, descriptor = [Lj/l/String;) {
        /// invokevirtual(&object = j/l/String, method = substring, descriptor = (II)Lj/l/String; {
        toAdd.add(new VarInsnNode(Opcodes.ALOAD, 0));
        /// iadd(I, I) {
        /// invokevirtual(&object = j/l/String, method = indexOf, descriptor = (Lj/l/String)I, args = V_MAGIC_NUMBER) {
        toAdd.add(new VarInsnNode(Opcodes.ALOAD, 0));  // さっきのファイル名
        toAdd.add(new LdcInsnNode(magicNumber));
        toAdd.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "indexOf", "(Ljava/lang/String;)I", false));
        /// }
        toAdd.add(new InsnNode(Opcodes.ICONST_1));
        toAdd.add(new InsnNode(Opcodes.IADD));
        /// }

        /// invokevirtual(&object = j/l/String, method = lastIndexOf, descriptor = (Lj/l/String)I, args = V_MAGIC_NUMBER_END) {
        toAdd.add(new VarInsnNode(Opcodes.ALOAD, 0));  // さっきのファイル名
        toAdd.add(new LdcInsnNode(magicNumberEnd));
        toAdd.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "lastIndexOf", "(Ljava/lang/String;)I", false));
        /// }

        toAdd.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false));
        /// }

        /// invokevirtual(&object = j/l/String, method = split, descriptor = (Lj/l/String;)[Lj/l/String;, args = V_MAGIC_NUMBER_SPLIT) {
        toAdd.add(new LdcInsnNode(magicNumberSplit));
        toAdd.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false));
        /// }

        toAdd.add(new FieldInsnNode(Opcodes.PUTSTATIC, cn.name, ledgerFieldName, "[Ljava/lang/String;"));
        /// }

        toAdd.add(end);
        toAdd.add(new InsnNode(Opcodes.RETURN));
        generateStrings.instructions = toAdd;
        generateStrings.maxStack = 4;
        generateStrings.maxLocals = 4;

        return generateStrings;
    }

    private static String buildLedger(Collection<String> values, String magicNumber, String magicNumberSplit, String magicNumberEnd)
    {
        StringBuilder sb = new StringBuilder(magicNumber);
        for (String s : values)
        {
            sb.append(s);
            sb.append(magicNumberSplit);
        }
        sb.append(magicNumberEnd);

        return sb.toString();
    }

    private static Map<Integer, String> createConstantReferences(ClassNode owner,
                                                                 MethodNode method,
                                                                 String cacheFieldName,
                                                                 int startIndex,
                                                                 int ledgerLength)
    {
        Map<Integer, String> hiddenStrings = new HashMap<>();

        for (AbstractInsnNode abstractInsnNode : method.instructions.toArray())
        {
            if (!(abstractInsnNode instanceof LdcInsnNode))
                continue;

            LdcInsnNode ldc = (LdcInsnNode) abstractInsnNode;
            if (!(ldc.cst instanceof String))
                continue;

            String string = (String) ldc.cst;

            if (string.length() > MAX_ONE_STRING_LENGTH)
            {
                log.warn("String constant is too long: " + string.substring(0, 10) + "...(" +
                        string.length() + " chrs > " + MAX_ONE_STRING_LENGTH + " chrs), skipping");
                continue;
            }

            if (ledgerLength + string.length() > MAX_TOTAL_STRING_LENGTH)
            {
                log.warn("Total string length is too long: " + ledgerLength + " + " + string.length() + " > " + MAX_TOTAL_STRING_LENGTH + ", skipping");

                if (ledgerLength == MAX_TOTAL_STRING_LENGTH)
                    break;
                else
                    continue;
            }

            InsnList insnList = new InsnList();
            insnList.add(new FieldInsnNode(Opcodes.GETSTATIC, owner.name, cacheFieldName, "[Ljava/lang/String;"));
            insnList.add(NodeUtils.generateIntPush(startIndex++));
            insnList.add(new InsnNode(Opcodes.AALOAD));

            method.instructions.insert(abstractInsnNode, insnList);
            method.instructions.remove(abstractInsnNode);

            ledgerLength += string.length();
            hiddenStrings.put(startIndex, string);
        }

        return hiddenStrings;
    }

    private static String getOrRandom(StringValue value)
    {
        if (value.get() == null || value.get().isEmpty())
            return String.valueOf((char) new Random().nextInt(0xFFFF));
        else
            return value.get();
    }

    @Override
    public void process(ProcessorCallback callback, ClassNode node)
    {
        if (!V_ENABLED.get())
            return;

        String fieldName = NameUtils.generateFieldName(node);
        HashMap<Integer, String> hiddenStrings = new HashMap<>();

        int hidedStringCount = 0;

        int methodCount = 0;
        MethodNode methodNode = null;

        int ledgerLength = 0;
        for (MethodNode method : node.methods)
        {
            Map<Integer, String> strings = createConstantReferences(
                    node,
                    method,
                    fieldName,
                    hidedStringCount,
                    ledgerLength
            );

            if (!strings.isEmpty())
            {
                hiddenStrings.putAll(strings);

                hidedStringCount += strings.size();
                methodCount++;
                methodNode = method;
            }
        }

        // メモリ削減のために, 1つのメソッドにしか文字列値がない場合は再利用を想定しないで消す。
        if (methodCount == 1)
        {
            InsnList toAdd = new InsnList();
            toAdd.add(new InsnNode(Opcodes.ACONST_NULL));
            toAdd.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, fieldName, "[Ljava/lang/String;"));

            NodeUtils.insertOn(methodNode.instructions, insnNode -> insnNode.getOpcode() >= Opcodes.IRETURN && insnNode.getOpcode() <= Opcodes.RETURN, toAdd);
        }

        if (hidedStringCount <= 0)
            return;

        String magicNumber = getOrRandom(V_MAGIC_NUMBER);
        String magicNumberSplit = getOrRandom(V_MAGIC_NUMBER_SPLIT);
        String magicNumberEnd = getOrRandom(V_MAGIC_NUMBER_END);

        node.sourceDebug = null;
        node.sourceFile = buildLedger(hiddenStrings.values(), magicNumber, magicNumberSplit, magicNumberEnd);
        node.fields.add(new FieldNode(
                        ((node.access & Opcodes.ACC_INTERFACE) != 0 ? Opcodes.ACC_PUBLIC: Opcodes.ACC_PRIVATE) | Opcodes.ACC_STATIC,
                        fieldName,
                        "[Ljava/lang/String;",
                        null,
                        null
                )
        );


        MethodNode mGenerateStrings = getGenerateStringsMethod(node, fieldName,
                magicNumber, magicNumberSplit, magicNumberEnd
        );
        node.methods.add(mGenerateStrings);

        MethodNode clInit = NodeUtils.getOrCreateCLInit(node);
        clInit.instructions.insertBefore(
                clInit.instructions.getFirst(),
                NodeUtils.methodCall(node, mGenerateStrings)
        );
    }

    @Override
    public ObfuscationTransformer getType()
    {
        return ObfuscationTransformer.HIDE_STRINGS;
    }
}
