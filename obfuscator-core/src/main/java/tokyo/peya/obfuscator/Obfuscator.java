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

package tokyo.peya.obfuscator;

import com.google.common.io.ByteStreams;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import tokyo.peya.obfuscator.clazz.ModifiedClassWriter;
import tokyo.peya.obfuscator.processor.Processors;
import tokyo.peya.obfuscator.clazz.ClassWrapper;
import tokyo.peya.obfuscator.processor.naming.INameObfuscationProcessor;
import tokyo.peya.obfuscator.processor.Packager;
import tokyo.peya.obfuscator.clazz.ClassTree;
import tokyo.peya.obfuscator.utils.MissingClassException;
import tokyo.peya.obfuscator.utils.NameUtils;
import tokyo.peya.obfuscator.utils.Utils;
import tokyo.peya.obfuscator.configuration.Configuration;
import tokyo.peya.obfuscator.configuration.ValueManager;
import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Getter
@Slf4j(topic = "Obfuscator")
public class Obfuscator
{
    private static final JObfSettings SETTINGS = new JObfSettings();

    static
    {
        ValueManager.registerClass(SETTINGS);
    }

    private final Configuration config;
    private final HashMap<String, byte[]> files;
    private final Map<String, ClassWrapper> classPath;
    private final HashMap<String, ClassNode> classes;
    private final Map<String, ClassTree> hierarchy;
    private final Set<ClassWrapper> libraryClassNodes;
    private final List<IClassTransformer> processors;
    private final List<INameObfuscationProcessor> nameObfuscationProcessors;
    public ScriptBridge script;
    private boolean mainClassChanged;
    private String mainClass;
    private int computeMode;

    public Obfuscator(Configuration config)
    {
        this.config = config;

        this.files = new HashMap<>();
        this.classPath = new HashMap<>();
        this.classes = new HashMap<>();
        this.hierarchy = new HashMap<>();
        this.libraryClassNodes = new HashSet<>();

        this.mainClass = null;

        this.processors = new ArrayList<>();
        this.nameObfuscationProcessors = new ArrayList<>();

        this.processors.addAll(Processors.createProcessors(this));
        this.nameObfuscationProcessors.addAll(Processors.createNameProcessors(this));
    }

    private static ZipInputStream getInJarStream(String inputJarPath) throws FileNotFoundException
    {
        try
        {
            return new ZipInputStream(new BufferedInputStream(new FileInputStream(inputJarPath)));
        }
        catch (FileNotFoundException e)
        {
            throw new FileNotFoundException("Could not open input file: " + e.getMessage());
        }
    }

    private static ZipOutputStream getOutJarStream(String outputJarPath, boolean stored) throws FileNotFoundException
    {
        try
        {
            OutputStream out = outputJarPath == null ? new ByteArrayOutputStream(): new FileOutputStream(outputJarPath);
            ZipOutputStream outJar = new ZipOutputStream(new BufferedOutputStream(out));
            outJar.setMethod(stored ? ZipOutputStream.STORED: ZipOutputStream.DEFLATED);

            if (stored)
                outJar.setLevel(Deflater.NO_COMPRESSION);

            return outJar;
        }
        catch (FileNotFoundException e)
        {
            throw new FileNotFoundException("Could not open output file: " + e.getMessage());
        }
    }

    public ClassTree getTree(String ref)
    {
        if (!this.hierarchy.containsKey(ref))
        {
            ClassWrapper wrapper = this.classPath.get(ref);

            if (wrapper == null)
                return null;

            this.buildHierarchy(wrapper, null, false);
        }

        return this.hierarchy.get(ref);
    }

    public void buildHierarchy(ClassWrapper classWrapper, ClassWrapper sub, boolean acceptMissingClass)
    {
        if (this.hierarchy.get(classWrapper.classNode.name) == null)
        {
            ClassTree tree = new ClassTree(classWrapper);
            if (classWrapper.classNode.superName != null)
            {
                tree.parentClasses.add(classWrapper.classNode.superName);
                ClassWrapper superClass = this.classPath.get(classWrapper.classNode.superName);

                if (superClass == null && !acceptMissingClass)
                    throw new MissingClassException(classWrapper.classNode.superName + " (referenced in " + classWrapper.classNode.name + ") is missing in the classPath.");
                else if (superClass == null)
                {
                    tree.missingSuperClass = true;

                    log.warn("Missing class: " + classWrapper.classNode.superName + " (No methods of subclasses will be remapped)");
                }
                else
                {
                    buildHierarchy(superClass, classWrapper, acceptMissingClass);

                    // Inherit the missingSuperClass state
                    if (this.hierarchy.get(classWrapper.classNode.superName).missingSuperClass)
                        tree.missingSuperClass = true;
                }
            }
            if (classWrapper.classNode.interfaces != null && !classWrapper.classNode.interfaces.isEmpty())
            {
                for (String s : classWrapper.classNode.interfaces)
                {
                    tree.parentClasses.add(s);
                    ClassWrapper interfaceClass = this.classPath.get(s);

                    if (interfaceClass == null && !acceptMissingClass)
                        throw new MissingClassException(s + " (referenced in " + classWrapper.classNode.name + ") is missing in the classPath.");
                    else if (interfaceClass == null)
                    {
                        tree.missingSuperClass = true;

                        log.warn("Missing interface class: " + s + " (No methods of subclasses will be remapped)");
                    }
                    else
                    {
                        this.buildHierarchy(interfaceClass, classWrapper, acceptMissingClass);

                        // Inherit the missingSuperClass state
                        if (this.hierarchy.get(s).missingSuperClass)
                            tree.missingSuperClass = true;
                    }
                }
            }
            this.hierarchy.put(classWrapper.classNode.name, tree);
        }
        if (sub != null)
        {
            this.hierarchy.get(classWrapper.classNode.name).subClasses.add(sub.classNode.name);
        }
    }

    private List<byte[]> loadClasspathFile(File file) throws IOException
    {
        ZipFile zipIn = new ZipFile(file);
        Enumeration<? extends ZipEntry> entries = zipIn.entries();

        boolean isModule = file.getName().endsWith(".jmod");

        List<byte[]> byteList = new ArrayList<>(zipIn.size());

        while (entries.hasMoreElements())
        {
            ZipEntry ent = entries.nextElement();
            if (ent.getName().endsWith(".class") && (!isModule || !ent.getName().endsWith("module-info.class") && ent.getName().startsWith("classes/")))
                byteList.add(ByteStreams.toByteArray(zipIn.getInputStream(ent)));
        }
        zipIn.close();

        return byteList;
    }

    private void loadClasspath(List<String> libraryFileNames) throws IOException
    {
        int i = 0;

        LinkedList<byte[]> classList = new LinkedList<>();

        List<File> libraries = new ArrayList<>();
        for (String s : libraryFileNames)
        {
            File file = new File(s);
            if (!file.exists())
            {
                log.warn("Library file " + s + " does not exist!");
                continue;
            }
            libraries.add(file);
        }

        for (File file : libraries)
        {
            if (file.isFile())
            {
                log.info("Loading " + file.getAbsolutePath() + " (" + (i++ * 100 / libraries.size()) + "%)");
                classList.addAll(loadClasspathFile(file));
                continue;
            }

            try (Stream<Path> stream = Files.walk(file.toPath()))
            {
                stream.map(Path::toFile)
                        .filter(f -> f.getName().endsWith(".jar")
                                || f.getName().endsWith(".zip")
                                || f.getName().endsWith(".jmod"))
                        .forEach(f -> {
                            log.info("Loading " + f.getName() + " (from " + file.getAbsolutePath() + ") to memory");
                            try
                            {
                                classList.addAll(loadClasspathFile(f));
                            }
                            catch (IOException e)
                            {
                                log.error("Failed to load " + f.getName() + " (from " + file.getAbsolutePath() + ") to memory", e);
                            }
                        });
            }
        }

        log.info("Read " + classList.size() + " class files to memory");
        log.info("Parsing class files...");

        this.classPath.putAll(parseClassPath(classList, this.config.getNThreads()));

        this.libraryClassNodes.addAll(this.classPath.values());
    }

    private Map<String, ClassWrapper> parseClassPath(final LinkedList<byte[]> byteList, int threads)
    {
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        Callable<Map<String, ClassWrapper>> runnable = () -> {
            Map<String, ClassWrapper> map = new HashMap<>();
            while (true)
            {
                byte[] bytes;
                synchronized (byteList)
                {
                    if (byteList.isEmpty())
                        break;

                    bytes = byteList.removeFirst();
                }

                ClassReader reader = new ClassReader(bytes);
                ClassNode node = new ClassNode();
                reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

                ClassWrapper wrapper = new ClassWrapper(node, true, bytes);
                map.put(node.name, wrapper);
            }

            return map;
        };

        List<Future<Map<String, ClassWrapper>>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++)
            futures.add(executor.submit(runnable));

        Map<String, ClassWrapper> map = new HashMap<>();
        for (Future<Map<String, ClassWrapper>> future : futures)
        {
            try
            {
                map.putAll(future.get());
            }
            catch (InterruptedException | ExecutionException e)
            {
                log.error("Failed to parse classpath", e);
            }
        }

        executor.shutdown();
        return map;
    }

    public boolean isLibrary(ClassNode classNode)
    {
        return this.libraryClassNodes.stream().anyMatch(e -> e.classNode.name.equals(classNode.name));
    }

    public boolean isLoadedCode(ClassNode classNode)
    {
        return this.classes.containsKey(classNode.name);
    }

    public void setScript(ScriptBridge script)
    {
        this.script = script;
    }

    private void prepareForProcessing()
    {
        NameUtils.applySettings(SETTINGS);
        NameUtils.setup();

        try
        {
            this.script = StringUtils.isBlank(this.config.getScript()) ? null:
                    new ScriptBridge(this.config.getScript());
        }
        catch (Exception e)
        {
            log.error("Failed to load script", e);
        }
    }

    public void process() throws Exception
    {
        long startTime = System.currentTimeMillis();
        log.info("Loading classpath...");
        loadClasspath(this.config.getLibraries());

        log.info("... Finished after " + Utils.formatTime(System.currentTimeMillis() - startTime));

        ZipOutputStream outJar = null;
        ZipInputStream inJar = null;
        this.prepareForProcessing();

        try
        {
            boolean useStore = SETTINGS.getUseStore().get();

            outJar = getOutJarStream(this.config.getOutput(), useStore);

            if (isClass(this.config.getInput()))
            {
                this.processOneClassObfuscation(this.config.getInput(), outJar);
                return;
            }

            inJar = getInJarStream(this.config.getInput());

            this.processJarObfuscation(useStore, inJar, outJar);
        }
        catch (InterruptedException ignored)
        {
        }
        catch (Exception e)
        {
            log.error("An error has occurred while processing the jar", e);
            throw e;  // Re-throw exception
        }
        finally
        {
            NameUtils.cleanUp();

            System.gc();

            if (outJar != null)
                finishProcessing(outJar);

            if (inJar != null)
                try
                {
                    inJar.close();
                }
                catch (IOException ignored)
                {
                }
        }
    }

    private void processOneClassObfuscation(String input, ZipOutputStream outJar) throws Exception
    {
        boolean useStore = SETTINGS.getUseStore().get();

        Path path = Paths.get(input);
        String fileName = path.getFileName().toString();

        try (InputStream in = Files.newInputStream(path);
                ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            Utils.copy(in, out);

            byte[] bytes = out.toByteArray();
            registerClassBytes(fileName, bytes);
        }

        Map<String, byte[]> toWrite = this.processClasses();
        finishOutJar(toWrite, outJar, useStore);
    }

    private static boolean isClass(String name)
    {
        return name.endsWith(".class");
    }

    private void finishProcessing(ZipOutputStream stream)
    {
        try
        {
            log.info("Finishing...");
            stream.flush();
            stream.close();
            log.info(">>> Processing completed. If you found a bug / if the output is invalid please open an issue at https://github.com/PeyaPeyaPeyang/JavaObfuscator/issues");
        }
        catch (Exception ignored)
        {
        }
    }

    private Map<String, byte[]> readJarClasses(ZipInputStream input, ZipOutputStream outJar) throws IOException
    {
        Map<String, byte[]> classDataMap = new HashMap<>();

        while (true)
        {
            ZipEntry entry = input.getNextEntry();

            if (entry == null)
                break;

            if (entry.isDirectory())
            {
                outJar.putNextEntry(entry);
                continue;
            }

            byte[] data = new byte[4096];
            ByteArrayOutputStream entryBuffer = new ByteArrayOutputStream();

            int len;
            do
            {
                len = input.read(data);
                if (len > 0)
                    entryBuffer.write(data, 0, len);
            }
            while (len != -1);

            byte[] entryData = entryBuffer.toByteArray();

            String entryName = entry.getName();

            if (!entryName.endsWith(".class"))
            {
                if (entryName.equals("META-INF/MANIFEST.MF"))
                    this.mainClass = Utils.getMainClass(new String(entryData, StandardCharsets.UTF_8));

                this.files.put(entryName, entryData);
                continue;
            }

            registerClassBytes(entryName, entryData);
            classDataMap.put(entryName, entryData);
        }

        return classDataMap;
    }

    private void registerClassBytes(String name, byte[] classBytes)
    {
        try
        {
            ClassReader cr = new ClassReader(classBytes);
            ClassNode cn = new ClassNode();

            //ca = new LineInjectorAdaptor(ASM4, cn);

            cr.accept(cn, 0);
            this.classes.put(name, cn);
        }
        catch (Exception e)
        {
            log.warn("Failed to read class " + name, e);
            this.files.put(name, classBytes);
        }
    }

    private void processJarObfuscation(boolean stored, ZipInputStream inJar, ZipOutputStream outJar) throws Exception
    {
        long startTime = System.currentTimeMillis();

        log.info("Reading input jar...");
        Map<String, byte[]> classDataMap = readJarClasses(inJar, outJar);

        for (Map.Entry<String, ClassNode> stringClassNodeEntry : this.classes.entrySet())
            this.classPath.put(stringClassNodeEntry.getKey().replace(".class", ""), new ClassWrapper(stringClassNodeEntry.getValue(), false, classDataMap.get(stringClassNodeEntry.getKey())));

        for (ClassNode value : this.classes.values())
            this.libraryClassNodes.add(new ClassWrapper(value, false, null));

        log.info("... Finished after " + Utils.formatTime(System.currentTimeMillis() - startTime));

        Map<String, byte[]> toWrite = this.processClasses();
        finishOutJar(toWrite, outJar, stored);
    }

    private void finishOutJar(Map<String, byte[]> classes, ZipOutputStream outJar, boolean stored) throws IOException
    {

        log.info("Writing classes...");
        long startTime = System.currentTimeMillis();
        for (Map.Entry<String, byte[]> stringEntry : classes.entrySet())
            writeEntry(outJar, stringEntry.getKey(), stringEntry.getValue(), stored);
        log.info("... Finished after " + Utils.formatTime(System.currentTimeMillis() - startTime));

        writeResources(outJar, stored);

        startTime = System.currentTimeMillis();

        if (Packager.INSTANCE.isEnabled())
        {
            log.info("Packaging...");
            writeEntry(outJar, Packager.INSTANCE.getDecryptionClassName() + ".class", Packager.INSTANCE.generateEncryptionClass(), stored);
            outJar.closeEntry();
            log.info("... Finished after " + Utils.formatTime(System.currentTimeMillis() - startTime));
        }
    }

    private void writeResources(ZipOutputStream outJar, boolean stored) throws IOException
    {
        long startTime = System.currentTimeMillis();

        log.info("Writing resources...");
        for (Map.Entry<String, byte[]> fileEntry : this.files.entrySet())
        {
            String entryName = fileEntry.getKey();
            byte[] entryData = fileEntry.getValue();

            if (entryName.equals("META-INF/MANIFEST.MF"))
            {
                if (Packager.INSTANCE.isEnabled())
                    entryData = Utils.replaceMainClass(new String(entryData, StandardCharsets.UTF_8), Packager.INSTANCE.getDecryptionClassName()).getBytes(StandardCharsets.UTF_8);
                else if (this.mainClassChanged)
                {
                    entryData = Utils.replaceMainClass(new String(entryData, StandardCharsets.UTF_8), this.mainClass).getBytes(StandardCharsets.UTF_8);
                    log.info("Replaced Main-Class with " + this.mainClass);
                }

                log.info("Processed MANIFEST.MF");
            }
            log.info("Copying " + entryName);

            writeEntry(outJar, entryName, entryData, stored);
        }
        log.info("... Finished after " + Utils.formatTime(System.currentTimeMillis() - startTime));
    }

    private Map<String, byte[]> processClasses() throws Exception
    {
        for (INameObfuscationProcessor nameObfuscationProcessor : this.nameObfuscationProcessors)
            nameObfuscationProcessor.transformPost(this, this.classes);

        if (Packager.INSTANCE.isEnabled())
            Packager.INSTANCE.init();

        long startTime = System.currentTimeMillis();

        int threadCount = this.config.getNThreads();
        log.info("Transforming with " + threadCount + " threads...");
        Map<String, byte[]> toWrite = this.transformClasses(threadCount);
        log.info("... Finished after " + Utils.formatTime(System.currentTimeMillis() - startTime));

        return toWrite;
    }

    private Map<String, byte[]> transformClasses(int threadCount)
    {
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        LinkedList<Map.Entry<String, ClassNode>> classQueue = new LinkedList<>(this.classes.entrySet());
        Map<String, byte[]> toWrite = new HashMap<>();

        AtomicInteger processed = new AtomicInteger(0);
        Callable<Map<String, byte[]>> runnable = () -> {
            Map<String, byte[]> toWriteThread = new HashMap<>();
            while (true)
            {
                Map.Entry<String, ClassNode> stringClassNodeEntry;

                synchronized (classQueue)
                {
                    stringClassNodeEntry = classQueue.poll();
                }

                if (stringClassNodeEntry == null)
                    break;

                ProcessorCallback callback = new ProcessorCallback();

                String entryName = stringClassNodeEntry.getKey();
                byte[] entryData;
                ClassNode cn = stringClassNodeEntry.getValue();

                try
                {

                    this.computeMode = ModifiedClassWriter.COMPUTE_MAXS;

                    if (this.script == null || this.script.isObfuscatorEnabled(cn))
                    {
                        log.info(String.format(
                                "[%s] (%s/%s), Processing %s",
                                Thread.currentThread().getName(),
                                processed.get(),
                                this.classes.size(),
                                entryName
                        ));

                        for (IClassTransformer proc : this.processors)
                            try
                            {
                                proc.process(callback, cn);
                            }
                            catch (Exception e)
                            {
                                log.error(String.format(
                                        "[%s] (%s/%s), Error transforming %s",
                                        Thread.currentThread().getName(),
                                        this.classes.size(),
                                        processed.get(),
                                        entryName
                                ), e);
                            }
                    }
                    else
                        log.info(String.format(
                                "[%s] (%s/%s), Skipping %s",
                                Thread.currentThread().getName(),
                                processed.get(),
                                this.classes.size(),
                                entryName
                        ));

                    if (callback.isForceComputeFrames())
                        cn.methods.forEach(method -> Arrays.stream(method.instructions.toArray())
                                .filter(abstractInsnNode -> abstractInsnNode instanceof FrameNode)
                                .forEach(abstractInsnNode -> method.instructions.remove(abstractInsnNode)));


                    int mode = this.computeMode
                            | (callback.isForceComputeFrames() ? ModifiedClassWriter.COMPUTE_FRAMES: 0);

                    log.debug(String.format(
                            "[%s] (%s/%s), Writing (computeMode = %s) %s",
                            Thread.currentThread().getName(),
                            processed.get(),
                            this.classes.size(),
                            mode,
                            entryName
                    ));

                    ModifiedClassWriter writer = new ModifiedClassWriter(
                            mode
//                                            ModifiedClassWriter.COMPUTE_MAXS |
//                                            ModifiedClassWriter.COMPUTE_FRAMES
                    );
                    cn.accept(writer);

                    entryData = writer.toByteArray();
                }
                catch (Throwable e)
                {
                    log.error(String.format(
                            "[%s] (%s/%s), Error writing %s",
                            Thread.currentThread().getName(),
                            processed.get(),
                            this.classes.size(),
                            entryName
                    ), e);
                    ModifiedClassWriter writer = new ModifiedClassWriter(ModifiedClassWriter.COMPUTE_MAXS
                            //                            | ModifiedClassWriter.COMPUTE_FRAMES
                    );
                    cn.accept(writer);


                    entryData = writer.toByteArray();
                }
                try
                {
                    if (Packager.INSTANCE.isEnabled())
                    {
                        entryName = Packager.INSTANCE.encryptName(entryName.replace(".class", ""));
                        entryData = Packager.INSTANCE.encryptClass(entryData);
                    }
                }
                catch (Exception e)
                {
                    log.error(String.format(
                            "[%s] (%s/%s), Error processing package %s",
                            Thread.currentThread().getName(),
                            processed.get(),
                            this.classes.size(),
                            entryName
                    ), e);
                }

                toWriteThread.put(entryName, entryData);

                processed.incrementAndGet();
            }

            return toWriteThread;
        };

        List<Future<Map<String, byte[]>>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++)
            futures.add(executorService.submit(runnable));

        for (Future<Map<String, byte[]>> future : futures)
            try
            {
                toWrite.putAll(future.get());
            }
            catch (InterruptedException | ExecutionException e)
            {
                log.error("Error getting future", e);
            }

        executorService.shutdown();

        return toWrite;
    }

    public void writeEntry(ZipOutputStream outJar, String name, byte[] value, boolean stored) throws IOException
    {
        ZipEntry newEntry = new ZipEntry(name);


        if (stored)
        {
            CRC32 crc = new CRC32();
            crc.update(value);

            newEntry.setSize(value.length);
            newEntry.setCrc(crc.getValue());
        }


        outJar.putNextEntry(newEntry);
        outJar.write(value);
    }
}
