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

import lombok.Getter;
import lombok.Setter;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import tokyo.peya.obfuscator.Localisation;
import tokyo.peya.obfuscator.Obfuscator;
import tokyo.peya.obfuscator.configuration.DeprecationLevel;
import tokyo.peya.obfuscator.configuration.ValueManager;
import tokyo.peya.obfuscator.configuration.values.BooleanValue;
import tokyo.peya.obfuscator.configuration.values.EnabledValue;
import tokyo.peya.obfuscator.configuration.values.StringValue;
import tokyo.peya.obfuscator.utils.NodeUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Random;

import static org.objectweb.asm.Opcodes.T_BYTE;

public class Packager
{
    private static final Random RANDOM = new Random();
    private static final String PROCESSOR_NAME = "packager";

    private static final EnabledValue V_ENABLED = new EnabledValue(
            PROCESSOR_NAME,
            "ui.transformers.packager.description",
            DeprecationLevel.AVAILABLE,
            false
    );
    private static final BooleanValue V_AUTO_FIND_MAIN_CLASS = new BooleanValue(
            PROCESSOR_NAME,
            "auto_find_main_class",
            "ui.transformers.packager.auto_find_main_class",
            DeprecationLevel.AVAILABLE,
            true
    );
    private static final StringValue V_MAIN_CLASS = new StringValue(
            PROCESSOR_NAME,
            "main_class",
            "ui.transformers.packager.main_class",
            DeprecationLevel.AVAILABLE,
            "org.example.Main"
    );

    private final Obfuscator instance;
    private final byte[] key;
    @Getter
    @Setter
    private String mainClass;

    static
    {
        ValueManager.registerOwner(PROCESSOR_NAME, "ui.transformers.packager");
        ValueManager.registerClass(Packager.class);
    }

    public Packager(Obfuscator instance)
    {
        this.instance = instance;
        this.mainClass = V_AUTO_FIND_MAIN_CLASS.get() ? instance.getMainClass(): V_MAIN_CLASS.get();

        this.key = new byte[RANDOM.nextInt(40) + 10];
        for (int i = 0; i < this.key.length; i++)
            this.key[i] = (byte) (RANDOM.nextInt(126) + 1);

        if (!V_ENABLED.get())
            return;

        if (V_AUTO_FIND_MAIN_CLASS.get() && this.mainClass == null)
            throw new IllegalArgumentException("[Packager] " + Localisation.get(
                    "ui.transformers.packager.no_main_class_found"));
    }

    public boolean isEnabled()
    {
        return V_ENABLED.get();
    }

    public byte[] encryptClass(byte[] data)
    {
        return xor(data, this.key);
    }

    public String encryptName(String name)
    {
        return new String(xor(name.replace("/", ".").getBytes(StandardCharsets.UTF_8), this.key));
    }

    public ClassNode generateEncryptionClass()
    {
        if (this.instance.getClasses().keySet()
                         .stream()
                         .noneMatch(s -> s.equals(this.mainClass + ".class")))
            throw new IllegalArgumentException("[Packager] " + Localisation.get(
                    "ui.transformers.packager.no_main_class_found"));

        String decryptionClassName = "BootstrapLoader";
        String keyFieldName = "KEYS";
        String xorMethodName = "decrypt";
        String getBytesMethodName = "getBytes";

        ClassNode cw = new ClassNode();

        FieldVisitor fv;
        MethodVisitor mv;

        cw.visit(
                Opcodes.V1_6,
                Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
                decryptionClassName,
                null,
                "java/lang/ClassLoader",
                new String[0]
        );

        {
            fv = cw.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, keyFieldName, "[B", null, null);
            fv.visitEnd();
        }
        {
            mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            NodeUtils.generateIntPush(this.key.length).accept(mv);
            mv.visitIntInsn(Opcodes.NEWARRAY, T_BYTE);

            for (int i = 0; i < this.key.length; i++)
            {
                mv.visitInsn(Opcodes.DUP);
                NodeUtils.generateIntPush(i).accept(mv);
                NodeUtils.generateIntPush(this.key[i]).accept(mv);
                mv.visitInsn(Opcodes.BASTORE);
            }

            mv.visitFieldInsn(Opcodes.PUTSTATIC, decryptionClassName, keyFieldName, "[B");
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(8, 0);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, new String[0]);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/ClassLoader", "<init>", "()V", false);
            mv.visitInsn(Opcodes.RETURN);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitLocalVariable("this", "L" + decryptionClassName + ";", null, l0, l1, 0);
            mv.visitMaxs(5, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(
                    Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
                    "main",
                    "([Ljava/lang/String;)V",
                    null,
                    new String[0]
            );
            mv.visitCode();
            Label l0 = new Label();
            Label l1 = new Label();
            Label l2 = new Label();
            mv.visitTryCatchBlock(l0, l1, l2, "java/lang/Exception");
            mv.visitLabel(l0);
            mv.visitTypeInsn(Opcodes.NEW, decryptionClassName);
            mv.visitInsn(Opcodes.DUP);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, decryptionClassName, "<init>", "()V", false);
            mv.visitVarInsn(Opcodes.ASTORE, 1);
            Label l3 = new Label();
            mv.visitLabel(l3);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitLdcInsn(Objects.requireNonNull(this.mainClass));
            mv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    "java/lang/ClassLoader",
                    "findClass",
                    "(Ljava/lang/String;)Ljava/lang/Class;",
                    false
            );
            mv.visitVarInsn(Opcodes.ASTORE, 2);
            Label l4 = new Label();
            mv.visitLabel(l4);
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitLdcInsn("main");
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
            mv.visitInsn(Opcodes.DUP);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitLdcInsn(Type.getType("[Ljava/lang/String;"));
            mv.visitInsn(Opcodes.AASTORE);
            mv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    "java/lang/Class",
                    "getMethod",
                    "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;",
                    false
            );
            Label l5 = new Label();
            mv.visitLabel(l5);
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
            mv.visitInsn(Opcodes.DUP);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.AASTORE);
            mv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    "java/lang/reflect/Method",
                    "invoke",
                    "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                    false
            );
            mv.visitInsn(Opcodes.POP);
            mv.visitLabel(l1);
            Label l6 = new Label();
            mv.visitJumpInsn(Opcodes.GOTO, l6);
            mv.visitLabel(l2);
            mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Exception"});
            mv.visitVarInsn(Opcodes.ASTORE, 1);
            Label l7 = new Label();
            mv.visitLabel(l7);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Exception", "printStackTrace", "()V", false);
            mv.visitLabel(l6);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            mv.visitInsn(Opcodes.RETURN);
            Label l8 = new Label();
            mv.visitLabel(l8);
            mv.visitLocalVariable("args", "[Ljava/lang/String;", null, l0, l8, 0);
            mv.visitLocalVariable("classLoader", "L" + decryptionClassName + ";", null, l3, l1, 1);
            mv.visitLocalVariable("entryPoint", "Ljava/lang/Class;", "Ljava/lang/Class<*>;", l4, l1, 2);
            mv.visitLocalVariable("e", "Ljava/lang/Exception;", null, l7, l6, 1);
            mv.visitMaxs(10, 3);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(
                    Opcodes.ACC_PROTECTED,
                    "findClass",
                    "(Ljava/lang/String;)Ljava/lang/Class;",
                    "(Ljava/lang/String;)Ljava/lang/Class<*>;",
                    new String[]{"java/lang/ClassNotFoundException"}
            );
            mv.visitCode();
            Label l0 = new Label();
            Label l1 = new Label();
            Label l2 = new Label();
            mv.visitTryCatchBlock(l0, l1, l2, "java/lang/Exception");
            mv.visitLabel(l0);
            mv.visitLineNumber(27, l0);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitLdcInsn("UTF-8");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "getBytes", "(Ljava/lang/String;)[B", false);
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    decryptionClassName,
                    getBytesMethodName,
                    "([B)[B",
                    false
            );
            mv.visitFieldInsn(Opcodes.GETSTATIC, decryptionClassName, keyFieldName, "[B");
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, decryptionClassName, xorMethodName, "([B[B)[B", false);
            mv.visitVarInsn(Opcodes.ASTORE, 2);
            Label l3 = new Label();
            mv.visitLabel(l3);
            mv.visitLineNumber(29, l3);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitInsn(Opcodes.ARRAYLENGTH);
            mv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    decryptionClassName,
                    "defineClass",
                    "(Ljava/lang/String;[BII)Ljava/lang/Class;",
                    false
            );
            mv.visitLabel(l1);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitLabel(l2);
            mv.visitLineNumber(30, l2);
            mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Exception"});
            mv.visitVarInsn(Opcodes.ASTORE, 2);
            Label l4 = new Label();
            mv.visitLabel(l4);
            mv.visitLineNumber(31, l4);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    "java/lang/ClassLoader",
                    "findClass",
                    "(Ljava/lang/String;)Ljava/lang/Class;",
                    false
            );
            mv.visitInsn(Opcodes.ARETURN);
            Label l5 = new Label();
            mv.visitLabel(l5);
            mv.visitLocalVariable("classLoader", "L" + decryptionClassName + ";", null, l0, l5, 0);
            mv.visitLocalVariable("encryptedName", "Ljava/lang/String;", null, l0, l5, 1);
            mv.visitLocalVariable("keys", "[B", null, l3, l2, 2);
            mv.visitLocalVariable("e", "Ljava/lang/Exception;", null, l4, l5, 2);
            mv.visitMaxs(7, 3);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(
                    Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC,
                    xorMethodName,
                    "([B[B)[B",
                    null,
                    new String[0]
            );
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ARRAYLENGTH);
            mv.visitIntInsn(Opcodes.NEWARRAY, T_BYTE);
            mv.visitVarInsn(Opcodes.ASTORE, 2);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ISTORE, 3);
            Label l2 = new Label();
            mv.visitLabel(l2);
            Label l3 = new Label();
            mv.visitJumpInsn(Opcodes.GOTO, l3);
            Label l4 = new Label();
            mv.visitLabel(l4);
            mv.visitFrame(Opcodes.F_APPEND, 2, new Object[]{"[B", Opcodes.INTEGER}, 0, null);
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitVarInsn(Opcodes.ILOAD, 3);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ILOAD, 3);
            mv.visitInsn(Opcodes.BALOAD);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 3);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitInsn(Opcodes.ARRAYLENGTH);
            mv.visitInsn(Opcodes.IREM);
            mv.visitInsn(Opcodes.BALOAD);
            mv.visitInsn(Opcodes.IXOR);
            mv.visitInsn(Opcodes.I2B);
            mv.visitInsn(Opcodes.BASTORE);
            Label l5 = new Label();
            mv.visitLabel(l5);
            mv.visitIincInsn(3, 1);
            mv.visitLabel(l3);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            mv.visitVarInsn(Opcodes.ILOAD, 3);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ARRAYLENGTH);
            mv.visitJumpInsn(Opcodes.IF_ICMPLT, l4);
            Label l6 = new Label();
            mv.visitLabel(l6);
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitInsn(Opcodes.ARETURN);
            Label l7 = new Label();
            mv.visitLabel(l7);
            mv.visitLocalVariable("encryptedClassBytes", "[B", null, l0, l7, 0);
            mv.visitLocalVariable("keys", "[B", null, l0, l7, 1);
            mv.visitLocalVariable("decryptedClassBytes", "[B", null, l1, l7, 2);
            mv.visitLocalVariable("i", "I", null, l2, l6, 3);
            mv.visitMaxs(10, 4);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(
                    Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC,
                    getBytesMethodName,
                    "([B)[B",
                    null,
                    new String[]{"java/io/IOException"}
            );
            mv.visitCode();
            // .class を付ける
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/String");
            mv.visitInsn(Opcodes.DUP);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETSTATIC, decryptionClassName, keyFieldName, "[B");
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, decryptionClassName, xorMethodName, "([B[B)[B", false);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>", "([B)V", false);
            mv.visitLdcInsn(".class");
            mv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    "java/lang/String",
                    "concat",
                    "(Ljava/lang/String;)Ljava/lang/String;",
                    false
            );
            mv.visitVarInsn(Opcodes.ASTORE, 0);
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitLineNumber(47, l0);
            mv.visitLdcInsn(Type.getType("L" + decryptionClassName + ";"));
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    "java/lang/Class",
                    "getResourceAsStream",
                    "(Ljava/lang/String;)Ljava/io/InputStream;",
                    false
            );
            mv.visitVarInsn(Opcodes.ASTORE, 1);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitLineNumber(49, l1);
            mv.visitTypeInsn(Opcodes.NEW, "java/io/ByteArrayOutputStream");
            mv.visitInsn(Opcodes.DUP);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/ByteArrayOutputStream", "<init>", "()V", false);
            mv.visitVarInsn(Opcodes.ASTORE, 2);
            Label l2 = new Label();
            mv.visitLabel(l2);
            mv.visitLineNumber(52, l2);
            mv.visitIntInsn(Opcodes.SIPUSH, 16384);
            mv.visitIntInsn(Opcodes.NEWARRAY, T_BYTE);
            mv.visitVarInsn(Opcodes.ASTORE, 4);
            Label l3 = new Label();
            mv.visitLabel(l3);
            mv.visitLineNumber(54, l3);
            Label l4 = new Label();
            mv.visitJumpInsn(Opcodes.GOTO, l4);
            Label l5 = new Label();
            mv.visitLabel(l5);
            mv.visitLineNumber(55, l5);
            mv.visitFrame(
                    Opcodes.F_FULL,
                    5,
                    new Object[]{
                            "java/lang/String", "java/io/InputStream", "java/io/ByteArrayOutputStream", Opcodes.INTEGER,
                            "[B"
                    },
                    0,
                    new Object[]{}
            );
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitVarInsn(Opcodes.ALOAD, 4);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ILOAD, 3);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/ByteArrayOutputStream", "write", "([BII)V", false);
            mv.visitLabel(l4);
            mv.visitLineNumber(54, l4);
            mv.visitFrame(
                    Opcodes.F_FULL,
                    5,
                    new Object[]{
                            "java/lang/String", "java/io/InputStream", "java/io/ByteArrayOutputStream", Opcodes.TOP,
                            "[B"
                    },
                    0,
                    new Object[]{}
            );
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ALOAD, 4);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ALOAD, 4);
            mv.visitInsn(Opcodes.ARRAYLENGTH);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/InputStream", "read", "([BII)I", false);
            mv.visitInsn(Opcodes.DUP);
            mv.visitVarInsn(Opcodes.ISTORE, 3);
            Label l6 = new Label();
            mv.visitLabel(l6);
            mv.visitInsn(Opcodes.ICONST_M1);
            mv.visitJumpInsn(Opcodes.IF_ICMPNE, l5);
            Label l7 = new Label();
            mv.visitLabel(l7);
            mv.visitLineNumber(58, l7);
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/ByteArrayOutputStream", "flush", "()V", false);
            Label l8 = new Label();
            mv.visitLabel(l8);
            mv.visitLineNumber(60, l8);
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/ByteArrayOutputStream", "toByteArray", "()[B", false);
            mv.visitInsn(Opcodes.ARETURN);
            Label l9 = new Label();
            mv.visitLabel(l9);
            mv.visitLocalVariable("chunkName", "Ljava/lang/String;", null, l0, l9, 0);
            mv.visitLocalVariable("is", "Ljava/io/InputStream;", null, l1, l9, 1);
            mv.visitLocalVariable("baos", "Ljava/io/ByteArrayOutputStream;", null, l2, l9, 2);
            mv.visitLocalVariable("read", "I", null, l6, l9, 3);
            mv.visitLocalVariable("chunkData", "[B", null, l3, l9, 4);
            mv.visitMaxs(8, 5);
            mv.visitEnd();
        }
        cw.visitEnd();

        ClassWriter classWriter1 = new ClassWriter(0);
        cw.accept(classWriter1);

        // this.instance.getClassPath().put(cw.name, new ClassWrapper(cw, false, clazz));
        this.instance.setMainClass(cw.name);

        return new ClassDecrypterClass(cw);
    }

    public boolean isPackagerClassDecrypter(ClassNode cn)
    {
        return cn instanceof ClassDecrypterClass;
    }

    public ClassNode asPackagerClassDecrypter(ClassNode cn)
    {
        return new ClassDecrypterClass(cn);
    }

    private static byte[] xor(byte[] data, byte[] key)
    {
        byte[] result = new byte[data.length];

        for (int i = 0; i < data.length; i++)
            result[i] = (byte) (data[i] ^ key[i % key.length]);

        return result;
    }

    private static class ClassDecrypterClass extends ClassNode
    {
        public ClassDecrypterClass(ClassNode parent)
        {
            super(Opcodes.ASM9);
            this.version = parent.version;
            this.access = parent.access;
            this.name = parent.name;
            this.signature = parent.signature;
            this.superName = parent.superName;
            this.interfaces = parent.interfaces;
            this.sourceFile = parent.sourceFile;
            this.sourceDebug = parent.sourceDebug;
            this.outerClass = parent.outerClass;
            this.outerMethod = parent.outerMethod;
            this.outerMethodDesc = parent.outerMethodDesc;
            this.visibleAnnotations = parent.visibleAnnotations;
            this.invisibleAnnotations = parent.invisibleAnnotations;
            this.visibleTypeAnnotations = parent.visibleTypeAnnotations;
            this.invisibleTypeAnnotations = parent.invisibleTypeAnnotations;
            this.attrs = parent.attrs;

            this.methods = parent.methods;
            this.fields = parent.fields;
            this.innerClasses = parent.innerClasses;
            this.module = parent.module;
            this.nestHostClass = parent.nestHostClass;
            this.nestMembers = parent.nestMembers;
            this.permittedSubclasses = parent.permittedSubclasses;
            this.recordComponents = parent.recordComponents;
        }
    }

}
