package tokyo.peya.obfuscator.ui;

import lombok.extern.slf4j.Slf4j;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.ErrorHandler;
import org.codehaus.commons.compiler.Location;
import org.codehaus.commons.compiler.util.Disassembler;
import org.codehaus.commons.compiler.util.reflect.ByteArrayClassLoader;
import org.codehaus.janino.ClassLoaderIClassLoader;
import org.codehaus.janino.Java;
import org.codehaus.janino.Parser;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.SimpleCompiler;
import org.codehaus.janino.UnitCompiler;
import org.codehaus.janino.util.ClassFile;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IContextSource;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import tokyo.peya.obfuscator.JavaObfuscator;
import tokyo.peya.obfuscator.configuration.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IADD;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.ICONST_M1;
import static org.objectweb.asm.Opcodes.IF_ICMPGT;
import static org.objectweb.asm.Opcodes.IF_ICMPLT;
import static org.objectweb.asm.Opcodes.IF_ICMPNE;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.RETURN;

@Slf4j(topic = "Obfuscator/PreviewGenerator")
public class PreviewGenerator
{

    public static ClassNode getRandomInputClass(Path inputFile)
    {
        if (inputFile == null)
            throw new IllegalArgumentException("Input file cannot be null");

        log.debug("Input file: {}", inputFile);
        String extension = inputFile.toString().substring(inputFile.toString().lastIndexOf(".") + 1);
        if (extension.equals("class"))
        {
            log.debug("Input file is a class file!");
            try
            {
                byte[] classBytes = Files.readAllBytes(inputFile);
                return toClassNode(classBytes);
            }
            catch (IOException e)
            {
                log.error("Failed to read class file: {}", e.getLocalizedMessage());
                return generatePreviewClass();
            }
        }

        if (!extension.equals("jar"))
        {
            log.debug("Input file is not a jar file!");
            throw new IllegalArgumentException("Input file must be a jar or class file");
        }

        log.debug("Input file is a jar file!");
        byte[] classBytes = pickOneClassEntryFromZip(inputFile);
        if (classBytes == null)
        {
            log.debug("No class file found in jar, using default hello world class");
            return generatePreviewClass();
        }

        return toClassNode(classBytes);
    }

    private static byte[] pickOneClassEntryFromZip(Path inputFile)
    {
        try (ZipFile zipFile = new ZipFile(inputFile.toFile()))
        {
            Random random = new Random();
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            int count = 0;
            while (entries.hasMoreElements())
            {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class"))
                    count++;
            }
            if (count == 0)
                return null;
            int randomIndex = random.nextInt(count);
            entries = zipFile.entries();
            int currentIndex = 0;
            while (entries.hasMoreElements())
            {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class"))
                {
                    if (currentIndex == randomIndex)
                    {
                        try (InputStream inputStream = zipFile.getInputStream(entry))
                        {
                            return inputStream.readAllBytes();
                        }
                    }
                    currentIndex++;
                }
            }
        }
        catch (IOException e)
        {
            log.error("Failed to read jar file: {}", e.getLocalizedMessage(), e);
            return null;
        }

        log.debug("No class file found in jar");
        return null;
    }

    public static ClassNode toClassNode(byte[] classBytes)
    {
        ClassReader classReader = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);

        return classNode;
    }

    public static ClassNode obfuscate(ClassNode classNode, Configuration config) throws IOException
    {
        return JavaObfuscator.obfuscateClass(classNode, config);
    }

    public static String classNodeToCode(ClassNode node)
    {
        if (node == null)
            throw new IllegalArgumentException("ClassNode cannot be null");

        Fernflower fernflower = new Fernflower(
                new InMemoryResultSaver(),
                new HashMap<>()
                {{
                    this.put("rbr", "0"); // Hide bridge methods
                    this.put("dgs", "1"); // Decompile generic signatures
                    this.put("ind", "  "); // Indent spaces
                    this.put("lit", "1"); // Literal expressions as is
                }},
                new FernflowerLogProxy()
        );

        ClassSource source = new ClassSource(node);
        fernflower.addSource(source);
        fernflower.decompileContext();

        return source.sink.output;
    }

    public static byte[] compile(String sourceCode) throws Exception
    {
        Parser parser = new Parser(new Scanner(null,  new StringReader(sourceCode)));
        Java.AbstractCompilationUnit compilationUnit = parser.parseAbstractCompilationUnit();
        UnitCompiler compiler = new UnitCompiler(
                compilationUnit,
                new ClassLoaderIClassLoader(ClassLoader.getSystemClassLoader())
        );
        compiler.setCompileErrorHandler(new CompileErrorHandlerMock(compiler));

        List<ClassFile> cfs = new ArrayList<>();
        compiler.compileUnit(true, true, true, cfs::add);

        if (cfs.isEmpty())
        {
            log.error("Compilation failed, no class files generated");
            throw new IllegalStateException("Compilation failed, no class files generated");
        }

        ClassFile classFile = cfs.get(0);
        return classFile.toByteArray();
    }

    private static class CompileErrorHandlerMock implements ErrorHandler
    {
        private static final Field fCompileErrorCount;

        static {
            try {
                Field field = UnitCompiler.class.getDeclaredField("compileErrorCount");
                field.setAccessible(true);
                fCompileErrorCount = field;
            } catch (NoSuchFieldException e) {
                throw new RuntimeException("Failed to access compileErrorCount field", e);
            }
        }


        private final UnitCompiler compiler;
        public CompileErrorHandlerMock(UnitCompiler compiler)
        {
            this.compiler = compiler;
        }

        @Override
        public void handleError(String s, Location location) throws CompileException
        {
            log.warn("Compile error: {} at {}", s, location);
            this.resetCompileErrorCount();
        }

        private void resetCompileErrorCount()
        {
            try {
                fCompileErrorCount.setInt(this.compiler, 0);
            } catch (IllegalAccessException e) {
                log.error("Failed to reset compile error count", e);
            }
        }
    }

    public static ClassNode generatePreviewClass()
    {
        ClassNode cn = new ClassNode();

        cn.visit(Opcodes.V1_8, ACC_PUBLIC | ACC_SUPER, "Main", null, "java/lang/Object", null);

        cn.visitSource("Main.java", null);

        cn.visitInnerClass("java/lang/invoke/MethodHandles$Lookup", "java/lang/invoke/MethodHandles", "Lookup", ACC_PUBLIC | ACC_FINAL | ACC_STATIC);

        {
            FieldVisitor fv = cn.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "MAX_NUMBER", "I", null, 100);
            fv.visitEnd();
        }

        MethodVisitor mv;
        {
            mv = cn.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            Label label0 = new Label();
            mv.visitLabel(label0);
            mv.visitLineNumber(4, label0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            Label label1 = new Label();
            mv.visitLabel(label1);
            mv.visitLocalVariable("this", "LMain;", null, label0, label1, 0);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        {
            mv = cn.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
            mv.visitCode();
            Label label0 = new Label();
            mv.visitLabel(label0);
            mv.visitLineNumber(8, label0);
            mv.visitLdcInsn("Hello, World!");
            mv.visitVarInsn(ASTORE, 1);
            Label label1 = new Label();
            mv.visitLabel(label1);
            mv.visitLineNumber(11, label1);
            mv.visitFrame(Opcodes.F_APPEND,1, new Object[] {"java/lang/String"}, 0, null);
            mv.visitTypeInsn(NEW, "java/util/Random");
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, "java/util/Random", "<init>", "()V", false);
            mv.visitIntInsn(BIPUSH, 100);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/Random", "nextInt", "(I)I", false);
            mv.visitInsn(ICONST_1);
            mv.visitInsn(IADD);
            mv.visitVarInsn(ISTORE, 2);
            Label label2 = new Label();
            mv.visitLabel(label2);
            mv.visitLineNumber(12, label2);
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitVarInsn(ILOAD, 2);
            mv.visitInvokeDynamicInsn("makeConcatWithConstants", "(I)Ljava/lang/String;", new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false), new Object[]{"Random number is \u0001"});
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
            Label label3 = new Label();
            mv.visitLabel(label3);
            mv.visitLineNumber(13, label3);
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn("Enter a number between 1 and 100: ");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);
            Label label4 = new Label();
            mv.visitLabel(label4);
            mv.visitLineNumber(14, label4);
            mv.visitMethodInsn(INVOKESTATIC, "Main", "getUserInput", "()Ljava/lang/String;", false);
            mv.visitVarInsn(ASTORE, 3);
            Label label5 = new Label();
            mv.visitLabel(label5);
            mv.visitLineNumber(15, label5);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitMethodInsn(INVOKESTATIC, "Main", "parseInt", "(Ljava/lang/String;)I", false);
            mv.visitVarInsn(ISTORE, 4);
            Label label6 = new Label();
            mv.visitLabel(label6);
            mv.visitLineNumber(16, label6);
            mv.visitVarInsn(ILOAD, 4);
            mv.visitInsn(ICONST_1);
            Label label7 = new Label();
            mv.visitJumpInsn(IF_ICMPLT, label7);
            mv.visitVarInsn(ILOAD, 4);
            mv.visitIntInsn(BIPUSH, 100);
            mv.visitJumpInsn(IF_ICMPGT, label7);
            Label label8 = new Label();
            mv.visitLabel(label8);
            mv.visitLineNumber(17, label8);
            mv.visitVarInsn(ILOAD, 4);
            mv.visitVarInsn(ILOAD, 2);
            Label label9 = new Label();
            mv.visitJumpInsn(IF_ICMPNE, label9);
            Label label10 = new Label();
            mv.visitLabel(label10);
            mv.visitLineNumber(18, label10);
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn("You guessed the number!");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
            Label label11 = new Label();
            mv.visitLabel(label11);
            mv.visitLineNumber(19, label11);
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
            Label label12 = new Label();
            mv.visitLabel(label12);
            mv.visitLineNumber(20, label12);
            mv.visitInsn(RETURN);
            mv.visitLabel(label9);
            mv.visitLineNumber(23, label9);
            mv.visitFrame(Opcodes.F_APPEND,3, new Object[] {Opcodes.INTEGER, "java/lang/String", Opcodes.INTEGER}, 0, null);
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn("Try again!");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
            Label label13 = new Label();
            mv.visitJumpInsn(GOTO, label13);
            mv.visitLabel(label7);
            mv.visitLineNumber(25, label7);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn("Please enter a number between 1 and 100.");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
            mv.visitLabel(label13);
            mv.visitLineNumber(27, label13);
            mv.visitFrame(Opcodes.F_CHOP,3, null, 0, null);
            mv.visitJumpInsn(GOTO, label1);
            Label label14 = new Label();
            mv.visitLabel(label14);
            mv.visitLocalVariable("randomNumber", "I", null, label2, label13, 2);
            mv.visitLocalVariable("userInputString", "Ljava/lang/String;", null, label5, label13, 3);
            mv.visitLocalVariable("userInput", "I", null, label6, label13, 4);
            mv.visitLocalVariable("args", "[Ljava/lang/String;", null, label0, label14, 0);
            mv.visitLocalVariable("helloWorld", "Ljava/lang/String;", null, label1, label14, 1);
            mv.visitMaxs(2, 5);
            mv.visitEnd();
        }
        {
            mv = cn.visitMethod(ACC_PUBLIC | ACC_STATIC, "getUserInput", "()Ljava/lang/String;", null, null);
            mv.visitCode();
            Label label0 = new Label();
            mv.visitLabel(label0);
            mv.visitLineNumber(31, label0);
            mv.visitTypeInsn(NEW, "java/util/Scanner");
            mv.visitInsn(DUP);
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "in", "Ljava/io/InputStream;");
            mv.visitMethodInsn(INVOKESPECIAL, "java/util/Scanner", "<init>", "(Ljava/io/InputStream;)V", false);
            mv.visitVarInsn(ASTORE, 0);
            Label label1 = new Label();
            mv.visitLabel(label1);
            mv.visitLineNumber(32, label1);
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn("Enter a string: ");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);
            Label label2 = new Label();
            mv.visitLabel(label2);
            mv.visitLineNumber(33, label2);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/Scanner", "nextLine", "()Ljava/lang/String;", false);
            mv.visitInsn(ARETURN);
            Label label3 = new Label();
            mv.visitLabel(label3);
            mv.visitLocalVariable("scanner", "Ljava/util/Scanner;", null, label1, label3, 0);
            mv.visitMaxs(3, 1);
            mv.visitEnd();
        }
        {
            mv = cn.visitMethod(ACC_PUBLIC | ACC_STATIC, "parseInt", "(Ljava/lang/String;)I", null, null);
            mv.visitCode();
            Label label0 = new Label();
            Label label1 = new Label();
            Label label2 = new Label();
            mv.visitTryCatchBlock(label0, label1, label2, "java/lang/NumberFormatException");
            mv.visitLabel(label0);
            mv.visitLineNumber(38, label0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false);
            mv.visitLabel(label1);
            mv.visitInsn(IRETURN);
            mv.visitLabel(label2);
            mv.visitLineNumber(39, label2);
            mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/NumberFormatException"});
            mv.visitVarInsn(ASTORE, 1);
            Label label3 = new Label();
            mv.visitLabel(label3);
            mv.visitLineNumber(40, label3);
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn("Invalid input. Please enter a number.");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
            Label label4 = new Label();
            mv.visitLabel(label4);
            mv.visitLineNumber(41, label4);
            mv.visitInsn(ICONST_M1);
            mv.visitInsn(IRETURN);
            Label label5 = new Label();
            mv.visitLabel(label5);
            mv.visitLocalVariable("var2", "Ljava/lang/NumberFormatException;", null, label3, label5, 1);
            mv.visitLocalVariable("input", "Ljava/lang/String;", null, label0, label5, 0);
            mv.visitMaxs(2, 2);
            mv.visitEnd();
        }
        cn.visitEnd();

        return cn;
    }

    static class InMemoryResultSaver implements IResultSaver
    {
        @Override
        public void saveClassFile(String s, String s1, String s2, String s3, int[] ints)
        {
        }

        @Override
        public void closeArchive(String s, String s1)
        {

        }

        @Override
        public void saveClassEntry(String s, String s1, String s2, String s3, String s4)
        {

        }

        @Override
        public void copyEntry(String s, String s1, String s2, String s3)
        {

        }

        @Override
        public void saveDirEntry(String s, String s1, String s2)
        {

        }

        @Override
        public void createArchive(String s, String s1, Manifest manifest)
        {

        }

        @Override
        public void copyFile(String s, String s1, String s2)
        {

        }

        @Override
        public void saveFolder(String s)
        {

        }

        @Override
        public byte[] getCodeLineData(int[] mappings)
        {
            return IResultSaver.super.getCodeLineData(mappings);
        }
    }

    private static class MockOutputSink implements IContextSource.IOutputSink
    {
        private String output;

        @Override
        public void begin()
        {

        }

        @Override
        public void acceptClass(String s, String s1, String s2, int[] ints)
        {
            this.output = s2;
        }

        @Override
        public void acceptDirectory(String s)
        {

        }

        @Override
        public void acceptOther(String s)
        {

        }

        @Override
        public void close() throws IOException
        {

        }
    }

    private static class ClassSource implements IContextSource
    {
        private final ClassNode classNode;
        private final MockOutputSink sink;

        public ClassSource(ClassNode classNode)
        {
            this.classNode = classNode;
            this.sink = new MockOutputSink();
        }

        @Override
        public String getName()
        {
            return "Preview";
        }

        @Override
        public Entries getEntries()
        {
            return new Entries(
                    Collections.singletonList(new Entry(this.classNode.name.replace("/", "."), -1)),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }

        @Override
        public InputStream getInputStream(String s) throws IOException
        {
            if (s.equals(this.classNode.name.replace("/", ".") + ".class"))
            {
                ClassWriter cw = new ClassWriter(0);
                this.classNode.accept(cw);

                byte[] classBytes = cw.toByteArray();
                return new ByteArrayInputStream(classBytes);
            }
            else
            {
                throw new IOException("Class not found: " + s);
            }
        }

        @Override
        public IOutputSink createOutputSink(IResultSaver saver)
        {
            return this.sink;
        }
    }

    private static class FernflowerLogProxy extends IFernflowerLogger
    {
        @Override
        public void writeMessage(String message, Severity severity)
        {
            switch (severity)
            {
                case INFO:
                    log.info(message);
                    break;
                case WARN:
                    log.warn(message);
                    break;
                case ERROR:
                    log.error(message);
                    break;
                default:
                    log.debug(message);
            }
        }

        @Override
        public void writeMessage(String s, Severity severity, Throwable throwable)
        {
            switch (severity)
            {
                case INFO:
                    log.info(s, throwable);
                    break;
                case WARN:
                    log.warn(s, throwable);
                    break;
                case ERROR:
                    log.error(s, throwable);
                    break;
                default:
                    log.debug(s, throwable);
            }
        }

        @Override
        public void writeMessage(String message, Throwable throwable)
        {
            log.error(message, throwable);
        }
    }
}
