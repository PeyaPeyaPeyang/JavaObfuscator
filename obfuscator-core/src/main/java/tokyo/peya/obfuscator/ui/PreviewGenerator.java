package tokyo.peya.obfuscator.ui;

import lombok.extern.slf4j.Slf4j;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.ErrorHandler;
import org.codehaus.commons.compiler.Location;
import org.codehaus.commons.compiler.util.reflect.ByteArrayClassLoader;
import org.codehaus.janino.SimpleCompiler;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IContextSource;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import tokyo.peya.obfuscator.JavaObfuscator;
import tokyo.peya.obfuscator.configuration.Configuration;
import tokyo.peya.obfuscator.utils.NodeUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Random;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j(topic = "Obfuscator/PreviewGenerator")
public class PreviewGenerator
{
    public static final ClassNode DEFAULT_HELLO_WORLD_CLASS;

    static {
        ClassNode helloWorldClass;
        try
        {
            int i = Main.MAX_NUMBER;
            log.trace("Default hello world class max number: {}", i);
            helloWorldClass = NodeUtils.toNode(Main.class);
        }
        catch (IOException e)
        {
            helloWorldClass = null;
        }
        DEFAULT_HELLO_WORLD_CLASS = helloWorldClass;
    }

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
                return DEFAULT_HELLO_WORLD_CLASS;
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
            return DEFAULT_HELLO_WORLD_CLASS;
        }

        return toClassNode(classBytes);
    }

    private static byte[] pickOneClassEntryFromZip(Path inputFile)
    {
        try (ZipFile zipFile = new ZipFile(inputFile.toFile());)
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

        Fernflower fernflower  = new Fernflower(
                new InMemoryResultSaver(),
                new HashMap<>()
                {{
                    this.put("gen", "0");
                    this.put("dgs", "1");
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
        SimpleCompiler compiler = new SimpleCompiler();
        compiler.setCompileErrorHandler(new CompileErrorHandlerMock());
        compiler.setDebuggingInformation(true, true, true);
        compiler.cook(sourceCode);

        ClassLoader  classLoader = compiler.getClassLoader();
        Field field = ByteArrayClassLoader.class.getDeclaredField("classes");
        field.setAccessible(true);
        // noinspection unchecked
        HashMap<String, byte[]> classes = (HashMap<String, byte[]>) field.get(classLoader);
        for (String className : classes.keySet())
        {
            if (!className.contains("$"))
            {
                byte[] classBytes = classes.get(className);
                if (classBytes != null)
                    return classBytes;
            }
        }

        return null;
    }

    private static class CompileErrorHandlerMock implements ErrorHandler
    {
        @Override
        public void handleError(String s, Location location) throws CompileException
        {
            log.error("Compile error: {}", s);
        }
    }


    static class InMemoryResultSaver implements IResultSaver {
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
