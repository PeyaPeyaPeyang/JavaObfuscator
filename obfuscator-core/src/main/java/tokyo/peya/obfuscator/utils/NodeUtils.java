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

package tokyo.peya.obfuscator.utils;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;
import tokyo.peya.obfuscator.JavaObfuscator;
import tokyo.peya.obfuscator.clazz.ClassTree;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.function.Predicate;

import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.DCONST_0;
import static org.objectweb.asm.Opcodes.FCONST_0;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_5;
import static org.objectweb.asm.Opcodes.ICONST_M1;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.SIPUSH;

public class NodeUtils
{
    private static final Printer printer = new Textifier();
    private static final TraceMethodVisitor methodPrinter = new TraceMethodVisitor(printer);
    private static final HashMap<Type, String> TYPE_TO_WRAPPER = new HashMap<>();

    static
    {
        TYPE_TO_WRAPPER.put(Type.INT_TYPE, "java/lang/Integer");
        TYPE_TO_WRAPPER.put(Type.VOID_TYPE, "java/lang/Void");
        TYPE_TO_WRAPPER.put(Type.BOOLEAN_TYPE, "java/lang/Boolean");
        TYPE_TO_WRAPPER.put(Type.CHAR_TYPE, "java/lang/Character");
        TYPE_TO_WRAPPER.put(Type.BYTE_TYPE, "java/lang/Byte");
        TYPE_TO_WRAPPER.put(Type.SHORT_TYPE, "java/lang/Short");
        TYPE_TO_WRAPPER.put(Type.FLOAT_TYPE, "java/lang/Float");
        TYPE_TO_WRAPPER.put(Type.LONG_TYPE, "java/lang/Long");
        TYPE_TO_WRAPPER.put(Type.DOUBLE_TYPE, "java/lang/Double");
    }

    public static String prettyprint(AbstractInsnNode insnNode)
    {
        insnNode.accept(methodPrinter);
        StringWriter sw = new StringWriter();
        printer.print(new PrintWriter(sw));
        printer.getText().clear();
        return sw.toString().trim();
    }

    public static String prettyprint(InsnList insnNode)
    {
        insnNode.accept(methodPrinter);
        StringWriter sw = new StringWriter();
        printer.print(new PrintWriter(sw));
        printer.getText().clear();
        return sw.toString().trim();
    }

    public static String prettyprint(MethodNode insnNode)
    {
        insnNode.accept(methodPrinter);
        StringWriter sw = new StringWriter();
        printer.print(new PrintWriter(sw));
        printer.getText().clear();
        return sw.toString().trim();
    }

    public static AbstractInsnNode getWrapperMethod(Type type)
    {
        if (type.getSort() != Type.VOID && TYPE_TO_WRAPPER.containsKey(type))
        {
            return new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    TYPE_TO_WRAPPER.get(type),
                    "valueOf",
                    "(" + type + ")L" + TYPE_TO_WRAPPER.get(type) + ";",
                    false
            );
        }

        return new InsnNode(Opcodes.NOP);
    }

    public static AbstractInsnNode getTypeNode(Type type)
    {
        if (TYPE_TO_WRAPPER.containsKey(type))
            return new FieldInsnNode(Opcodes.GETSTATIC, TYPE_TO_WRAPPER.get(type), "TYPE", "Ljava/lang/Class;");

        return new LdcInsnNode(type);
    }

    public static AbstractInsnNode getUnWrapMethod(Type type)
    {
        if (TYPE_TO_WRAPPER.containsKey(type))
        {
            String internalName = Utils.getInternalName(type);
            return new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    TYPE_TO_WRAPPER.get(type),
                    internalName + "Value",
                    "(L" + TYPE_TO_WRAPPER.get(type) + ";)" + type,
                    false
            );
        }

        return new InsnNode(Opcodes.NOP);
    }

    public static boolean isIntegerNumber(AbstractInsnNode ain)
    {
        if (ain.getOpcode() == BIPUSH || ain.getOpcode() == SIPUSH)
            return true;
        if (ain.getOpcode() >= ICONST_M1 && ain.getOpcode() <= ICONST_5)
            return true;
        if (ain instanceof LdcInsnNode ldc)
            return ldc.cst instanceof Integer;

        return false;
    }

    public static boolean isEnum(AbstractInsnNode ain)
    {
        if (ain instanceof FieldInsnNode fin)
            return fin.desc.startsWith("L")
                    && fin.desc.endsWith(";")
                    && fin.desc.substring(1, fin.desc.length() - 1)
                               .equals(Utils.getInternalName(Type.getType(Enum.class)));
        return false;
    }

    public static AbstractInsnNode generateIntPush(int i)
    {
        if (i <= 5 && i >= -1)
            return new InsnNode(i + 3); //iconst_i
        if (i >= -128 && i <= 127)
            return new IntInsnNode(BIPUSH, i);

        if (i >= -32768 && i <= 32767)
            return new IntInsnNode(SIPUSH, i);
        return new LdcInsnNode(i);
    }

    public static int getIntValue(AbstractInsnNode node)
    {
        if (node.getOpcode() >= ICONST_M1 && node.getOpcode() <= ICONST_5)
            return node.getOpcode() - 3;
        if (node.getOpcode() == SIPUSH || node.getOpcode() == BIPUSH)
            return ((IntInsnNode) node).operand;
        if (node instanceof LdcInsnNode && ((LdcInsnNode) node).cst instanceof Integer)
            return (int) ((LdcInsnNode) node).cst;

        throw new IllegalArgumentException(node + " isn't an integer node");
    }

    public static MethodInsnNode toCallNode(final MethodNode method, final ClassNode classNode)
    {
        return new MethodInsnNode(
                Modifier.isStatic(method.access) ? Opcodes.INVOKESTATIC: Opcodes.INVOKEVIRTUAL,
                classNode.name,
                method.name,
                method.desc,
                false
        );
    }

    public static InsnList removeFromOpcode(InsnList insnList, int code)
    {
        for (AbstractInsnNode node : insnList.toArray().clone())
            if (node.getOpcode() == code)
                insnList.remove(node);
        return insnList;
    }

    public static boolean isConditionalGoto(AbstractInsnNode abstractInsnNode)
    {
        return abstractInsnNode.getOpcode() >= Opcodes.IFEQ && abstractInsnNode.getOpcode() <= Opcodes.IF_ACMPNE;
    }

    public static int getFreeSlot(MethodNode method)
    {
        int max = 0;
        for (AbstractInsnNode ain : method.instructions.toArray())
            if (ain instanceof VarInsnNode varInsn && varInsn.var > max)
                max = varInsn.var;

        return max + 1;
    }

    public static MethodNode getMethod(final ClassNode classNode, final String name)
    {
        for (final MethodNode method : classNode.methods)
            if (method.name.equals(name))
                return method;
        return null;
    }

    public static ClassNode toNode(final String className) throws IOException
    {
        try (InputStream is = JavaObfuscator.class.getResourceAsStream("/" + className.replace('.', '/') + ".class"))
        {
            final ClassReader classReader = new ClassReader(is);
            final ClassNode classNode = new ClassNode();

            classReader.accept(classNode, 0);

            return classNode;
        }
    }

    public static ClassNode toNode(Class<?> clazz) throws IOException
    {
        return toNode(clazz.getName());
    }

    public static int getInvertedJump(int opcode)
    {
        return switch (opcode)
        {
            case Opcodes.IFEQ -> Opcodes.IFNE;
            case Opcodes.IFNE -> Opcodes.IFEQ;
            case Opcodes.IF_ACMPEQ -> Opcodes.IF_ACMPNE;
            case Opcodes.IF_ACMPNE -> Opcodes.IF_ACMPEQ;
            default -> -1;
        };
    }

    public static boolean isNormalMethod(MethodNode method)
    {
        return !Modifier.isNative(method.access) && !Modifier.isAbstract(method.access) && method.instructions.size() != 0;
    }

    public static boolean isNormalClass(ClassNode node)
    {
        return (node.access & Opcodes.ACC_ENUM) == 0 && (node.access & Opcodes.ACC_INTERFACE) == 0;
    }

    public static AbstractInsnNode methodCall(ClassNode classNode, MethodNode methodNode)
    {
        int opcode = Opcodes.INVOKEVIRTUAL;

        if (Modifier.isInterface(classNode.access))
            opcode = Opcodes.INVOKEINTERFACE;
        if (Modifier.isStatic(methodNode.access))
            opcode = Opcodes.INVOKESTATIC;
        if (methodNode.name.startsWith("<"))
            opcode = Opcodes.INVOKESPECIAL;

        return new MethodInsnNode(opcode, classNode.name, methodNode.name, methodNode.desc, false);
    }

    public static void insertOn(InsnList instructions, Predicate<? super AbstractInsnNode> predicate, InsnList toAdd)
    {
        for (AbstractInsnNode abstractInsnNode : instructions.toArray())
        {
            if (predicate.test(abstractInsnNode))
                instructions.insertBefore(abstractInsnNode, toAdd);
        }
    }

    public static InsnList nullPush()
    {
        InsnList insns = new InsnList();

        insns.add(new InsnNode(Opcodes.ACONST_NULL));

        return insns;
    }

    public static InsnList notNullPush()
    {
        throw new RuntimeException("Not implemented");
//        InsnList insns = new InsnList();

//        insns.add(new LdcInsnNode(Math.random() * 100));
//        insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false));
//        insns.add(new TypeInsnNode());
//        insns.add(new LdcInsnNode(Type.getType("Ljava/lang/System;")));
//        insns.add(new FieldInsnNode(""));
//        return insns;
    }

    public static InsnList debugString(String s)
    {
        InsnList insns = new InsnList();
        insns.add(new LdcInsnNode(s));
        insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false));
        insns.add(new InsnNode(Opcodes.POP));
        return insns;
    }

    public static AbstractInsnNode nullValueForType(Type returnType)
    {
        return switch (returnType.getSort())
        {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> new InsnNode(ICONST_0);
            case Type.FLOAT -> new InsnNode(FCONST_0);
            case Type.DOUBLE -> new InsnNode(DCONST_0);
            case Type.LONG -> new InsnNode(LCONST_0);
            case Type.ARRAY, Type.OBJECT -> new InsnNode(ACONST_NULL);
            default -> throw new UnsupportedOperationException();
        };
    }

    public static void insertAfterInvokeSpecial(InsnList instructions, int margin, AbstractInsnNode... insertions)
    {
        int i = 0;
        boolean invokeSpecialFound = false;
        for (AbstractInsnNode insnNode : instructions.toArray())
        {
            if (!(insnNode instanceof MethodInsnNode methodInsnNode))
                continue;

            if (methodInsnNode.getOpcode() == Opcodes.INVOKESPECIAL)
                invokeSpecialFound = true;

            if (invokeSpecialFound)
                if (i++ == margin)
                {
                    AbstractInsnNode current = insnNode;
                    for (AbstractInsnNode insertion : insertions)
                    {
                        instructions.insert(current, insertion);
                        current = insertion;
                    }
                    return;
                }
        }

        // invokespecial がなかった -> そのまま追加

        for (AbstractInsnNode insertion : insertions)
            instructions.add(insertion);

    }

    public static void insertAfterInvokeSpecial(InsnList instructions, AbstractInsnNode insertion)
    {
        insertAfterInvokeSpecial(instructions, 0, insertion);
    }

    public static boolean isSpecialMethod(MethodNode method)
    {
        return method.name.equals("<init>") || method.name.equals("<clinit>");
    }

    public static boolean isEnumSpecialMethod(MethodNode node)
    {
        return node.name.equals("valueOf") || node.name.equals("values");
    }

    public static boolean isSpecialMethod(MethodNode method, ClassTree tree)
    {
        if (isSpecialMethod(method))
            return true;

        boolean isEnum = tree.parentClasses.contains("java/lang/Enum");
        if (isEnum)
            return isEnumSpecialMethod(method);

        return isAnnotationSpecialMethod(method, tree.classWrapper.classNode);
    }

    public static boolean isAnnotationSpecialMethod(MethodNode field, ClassNode clazz)
    {
        boolean isInterface = Modifier.isInterface(clazz.access);
        if (!isInterface)
            return false;

        boolean isAnnotation = (clazz.access & Opcodes.ACC_ANNOTATION) != 0;
        return isAnnotation && field.name.equals("value");
    }

    public static MethodNode getOrCreateCLInit(ClassNode node)
    {
        MethodNode clInit = NodeUtils.getMethod(node, "<clinit>");
        if (clInit == null)
        {
            clInit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, new String[0]);
            node.methods.add(clInit);
        }
        if (clInit.instructions == null)
            clInit.instructions = new InsnList();

        return clInit;
    }

    public static boolean isBeforeThanInitializer(AbstractInsnNode insnNode, MethodNode method, String owner)
    {

        AbstractInsnNode current = insnNode;
        while (current != null)
        {
            if (!method.instructions.contains(current))
                return false; // 探索終わり

            if (current.getOpcode() == Opcodes.INVOKESPECIAL)
            {
                MethodInsnNode methodInsnNode = (MethodInsnNode) current;
                if (methodInsnNode.owner.equals(owner) && methodInsnNode.name.equals("<init>"))
                    return true;
            }
            current = current.getNext();
        }

        return false;
    }

    public static void addInvokeOnClassInitialisation(ClassNode node, MethodNode method)
    {
        MethodNode clInit = NodeUtils.getOrCreateCLInit(node);
        if (clInit.instructions.getFirst() == null)
        {
            clInit.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    node.name,
                    method.name,
                    method.desc,
                    false
            ));
            clInit.instructions.add(new InsnNode(Opcodes.RETURN));
        }
        else
            clInit.instructions.insertBefore(
                    clInit.instructions.getFirst(),
                    new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            node.name,
                            method.name,
                            method.desc,
                            false
                    )
            );

    }

    public static boolean isEntryPoint(MethodNode method)
    {
        return method.name.equals("main")
                && method.desc.equals("([Ljava/lang/String;)V")
                && (method.access & Opcodes.ACC_STATIC) != 0
                && (method.access & Opcodes.ACC_PUBLIC) != 0;
    }

    public static boolean hasEntryPoint(ClassNode node)
    {
        return node.methods.stream().anyMatch(NodeUtils::isEntryPoint);
    }

    public static void combineInstructions(InsnList target, InsnList source)
    {
        if (source == null || source.size() == 0)
            return;

        for (AbstractInsnNode ain : source.toArray())
            target.add(ain);
    }
}
