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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import tokyo.peya.obfuscator.annotations.ObfuscateRule;
import tokyo.peya.obfuscator.annotations.ObfuscationTransformer;
import tokyo.peya.obfuscator.clazz.ClassTree;
import tokyo.peya.obfuscator.clazz.ClassWrapper;
import tokyo.peya.obfuscator.clazz.ModifiedClassWriter;
import tokyo.peya.obfuscator.configuration.Configuration;
import tokyo.peya.obfuscator.configuration.ValueManager;
import tokyo.peya.obfuscator.processor.Packager;
import tokyo.peya.obfuscator.processor.Processors;
import tokyo.peya.obfuscator.processor.naming.INameObfuscationProcessor;
import tokyo.peya.obfuscator.ui.PreviewGenerator;
import tokyo.peya.obfuscator.utils.ExcludePattern;
import tokyo.peya.obfuscator.utils.MissingClassException;
import tokyo.peya.obfuscator.utils.Utils;

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
import java.lang.annotation.Annotation;
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
import java.util.regex.Pattern;
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

    @Getter
    private final Configuration config;
    private final UniqueNameProvider nameProvider;
    private final Packager packager;
    private final HashMap<String, byte[]> files;
    private final Map<String, ClassWrapper> classPath;
    private final HashMap<String, ClassNode> classes;
    private final Map<String, ClassTree> hierarchy;
    private final Set<ClassWrapper> libraryClassNodes;
    private final List<IClassTransformer> processors;
    private final List<INameObfuscationProcessor> nameObfuscationProcessors;
    private final List<Pattern> excludePatterns;

    @Setter
    public ScriptBridge script;
    private boolean mainClassChanged;
    private String mainClass;
    private int computeMode;

    public Obfuscator(Configuration config)
    {
        this.config = config;

        this.nameProvider = new UniqueNameProvider(SETTINGS);
        this.packager = new Packager(this);
        this.files = new HashMap<>();
        this.classPath = new HashMap<>();
        this.classes = new HashMap<>();
        this.hierarchy = new HashMap<>();
        this.libraryClassNodes = new HashSet<>();
        this.excludePatterns = compileExcludePatterns();

        this.mainClass = null;

        this.processors = new ArrayList<>();
        this.nameObfuscationProcessors = new ArrayList<>();

        this.processors.addAll(Processors.createProcessors(this));
        this.nameObfuscationProcessors.addAll(Processors.createNameProcessors(this));
    }

    private static List<Pattern> compileExcludePatterns()
    {
        List<Pattern> result = new ArrayList<>();
        for (String s : SETTINGS.getExcludedClasses().get().split("\n"))
            result.add(ExcludePattern.compileExcludePattern(s));

        return result;
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

    private static boolean isClass(String name)
    {
        return name.endsWith(".class");
    }

    private static void removeObfuscateRuleAnnotations(ClassNode node)
    {
        String obfuscateRulePath = ObfuscateRule.class.getName().replace('.', '/');

        if (node.invisibleTypeAnnotations != null)
            node.invisibleTypeAnnotations.removeIf(typeAnnotationNode -> typeAnnotationNode.desc.equals("L" + obfuscateRulePath + ";"));
    }

    private boolean isExcludedClass(String name)
    {
        for (Pattern pattern : this.excludePatterns)
            if (pattern.matcher(name).matches())
                return true;

        return false;
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

                if (superClass == null)
                {
                    if (!acceptMissingClass)
                        throw new MissingClassException(Localisation.access("logs.obfuscation.hierarchy.missing_super_class.fatal")
                                .set("missingSuperClass", classWrapper.classNode.superName)
                                .set("referencingClass", classWrapper.classNode.name)
                                .get()
                        );

                    tree.missingSuperClass = true;

                    log.warn(Localisation.access("logs.obfuscation.hierarchy.missing_super_class")
                                         .set("missingSuperClass", classWrapper.classNode.superName)
                                         .set("referencingClass", classWrapper.classNode.name)
                                         .get()
                    );
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

                    if (interfaceClass == null)
                    {
                        if (!acceptMissingClass)
                            throw new MissingClassException(Localisation.access("logs.obfuscation.hierarchy.missing_interface.fatal")
                                    .set("missingInterface", s)
                                    .set("referencingClass", classWrapper.classNode.name)
                                    .get()
                            );

                        tree.missingSuperClass = true;

                        log.warn(Localisation.access("logs.obfuscation.hierarchy.missing_interface")
                                .set("missingInterface", s)
                                .set("referencingClass", classWrapper.classNode.name)
                                .get()
                        );
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
        long startTime = System.currentTimeMillis();
        log.info(Localisation.get("logs.obfuscation.classpath.loading"));

        int i = 0;

        LinkedList<byte[]> classList = new LinkedList<>();

        List<File> libraries = new ArrayList<>();
        for (String s : libraryFileNames)
        {
            File file = new File(s);
            if (!file.exists())
            {
                log.warn(Localisation.access("logs.obfuscation.classpath.not_exist")
                        .set("fileName", file.getAbsolutePath())
                        .get()
                );
                continue;
            }
            libraries.add(file);
        }

        for (File file : libraries)
        {
            if (file.isFile())
            {
                log.info(Localisation.access("logs.obfuscation.classpath.loading_each")
                        .set("filePath", file.getAbsolutePath())
                        .set("percent", ++i * 100 / libraries.size())
                        .get()
                );
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
                            log.debug(Localisation.access("logs.obfuscation.classpath.read.reading_file")
                                    .set("fileName", f.getName())
                                    .set("filePath", f.getAbsolutePath())
                                    .get()
                            );
                            try
                            {
                                classList.addAll(loadClasspathFile(f));
                            }
                            catch (IOException e)
                            {
                                log.warn(Localisation.access("logs.obfuscation.classpath.read.fail")
                                        .set("fileName", f.getName())
                                        .set("filePath", f.getAbsolutePath())
                                        .get()
                                );
                            }
                        });
            }
        }

        log.info(Localisation.access("logs.obfuscation.classpath.read.success")
                .set("classes", classList.size())
                .get()
        );

        this.classPath.putAll(parseClassPath(classList, this.config.getNThreads()));
        this.libraryClassNodes.addAll(this.classPath.values());


        log.info(Localisation.access("logs.task_finished")
                             .set("time", Utils.formatTime(System.currentTimeMillis() - startTime))
                             .get()
        );

    }

    private Map<String, ClassWrapper> parseClassPath(final LinkedList<byte[]> byteList, int threads)
    {
        log.info(Localisation.get("logs.obfuscation.classpath.parsing_class"));

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

    private void prepareForProcessing()
    {
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
        loadClasspath(this.config.getLibraries());

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
            log.error(Localisation.get("logs.obfuscation.error.an_error_occurred"), e);
            throw e;  // Re-throw exception
        }
        finally
        {
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

        Map<String, byte[]> toWrite = this.processClasses(this.classes);
        finishOutJar(toWrite, outJar, useStore);
    }

    private void finishProcessing(ZipOutputStream stream)
    {
        try
        {
            log.info(Localisation.get("logs.obfuscation.finishing"));
            stream.flush();
            stream.close();
            log.info(Localisation.get("logs.obfuscation.finished"));
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
            ClassNode cn = PreviewGenerator.toClassNode(classBytes);
            this.classes.put(name, cn);
        }
        catch (Exception e)
        {
            log.warn(Localisation.access("logs.obfuscation.error.fail_read").set("className", name).get(),
                    e
            );
            this.files.put(name, classBytes);
        }
    }

    private void processJarObfuscation(boolean stored, ZipInputStream inJar, ZipOutputStream outJar) throws Exception
    {
        long startTime = System.currentTimeMillis();

        log.info(Localisation.access("logs.obfuscation.reading_input")
                .set("jarName", this.config.getInput())
                .get()
        );
        Map<String, byte[]> classDataMap = readJarClasses(inJar, outJar);

        for (Map.Entry<String, ClassNode> stringClassNodeEntry : this.classes.entrySet())
            this.classPath.put(stringClassNodeEntry.getKey().replace(".class", ""), new ClassWrapper(stringClassNodeEntry.getValue(), false, classDataMap.get(stringClassNodeEntry.getKey())));

        for (ClassNode value : this.classes.values())
            this.libraryClassNodes.add(new ClassWrapper(value, false, null));

        log.info(Localisation.access("logs.task_finished")
                .set("time", Utils.formatTime(System.currentTimeMillis() - startTime))
                .get()
        );

        Map<String, byte[]> toWrite = this.processClasses(this.classes);
        finishOutJar(toWrite, outJar, stored);
    }

    private Map<String, byte[]> generatePackageDecrypter()
    {
        log.info(Localisation.get("logs.obfuscation.transformer.packager.generating_decrypter"));
        long startTime = System.currentTimeMillis();

        ClassNode packagerNode = this.packager.generateEncryptionClass();
        Map<String, ClassNode> packagerMap = new HashMap<>();
        packagerMap.put(packagerNode.name + ".class", packagerNode);
        Map<String, byte[]> toWrite = new HashMap<>(this.processClasses(packagerMap));

        log.info(Localisation.access("logs.task_finished")
                .set("time", Utils.formatTime(System.currentTimeMillis() - startTime))
                .get()
        );

        return toWrite;
    }

    private void finishOutJar(Map<String, byte[]> classes, ZipOutputStream outJar, boolean stored) throws IOException
    {
        log.info(Localisation.access("logs.obfuscation.transforming.writing_artifact")
                .set("outputPath", this.config.getOutput())
                .get()
        );
        long startTime = System.currentTimeMillis();
        Map<String, byte[]> toWrite = new HashMap<>(classes);

        if (this.packager.isEnabled())
            toWrite.putAll(generatePackageDecrypter());


        for (Map.Entry<String, byte[]> stringEntry : toWrite.entrySet())
            writeEntry(outJar, stringEntry.getKey(), stringEntry.getValue(), stored);

        log.info(Localisation.access("logs.task_finished")
                .set("time", Utils.formatTime(System.currentTimeMillis() - startTime))
                .get()
        );

        writeResources(outJar, stored);
    }

    private void writeResources(ZipOutputStream outJar, boolean stored) throws IOException
    {
        long startTime = System.currentTimeMillis();

        log.info(Localisation.get("logs.obfuscation.resources.writing"));
        boolean metaInfoProcessed = false;
        for (Map.Entry<String, byte[]> fileEntry : this.files.entrySet())
        {
            String entryName = fileEntry.getKey();
            byte[] entryData = fileEntry.getValue();

            if (entryName.equals("META-INF/MANIFEST.MF"))
            {
                metaInfoProcessed = true;
                if (this.mainClassChanged)
                {
                    entryData = Utils.replaceMainClass(new String(entryData, StandardCharsets.UTF_8), this.mainClass).getBytes(StandardCharsets.UTF_8);
                    log.info(Localisation.access("logs.obfuscation.resources.main_class.replaced")
                            .set("newMainClass", this.mainClass)
                            .get()
                    );
                }

                log.info(Localisation.get("logs.obfuscation.resources.main_class.manifest_proceed"));
            }

            writeEntry(outJar, entryName, entryData, stored);
        }

        if (!metaInfoProcessed && this.mainClassChanged)
        {
            log.info(Localisation.get("logs.obfuscation.resources.main_class.manifest_added"));
            String manifest = "Manifest-Version: 1.0\nMain-Class: " + this.mainClass + "\n";
            writeEntry(outJar, "META-INF/MANIFEST.MF", manifest.getBytes(StandardCharsets.UTF_8), stored);
        }

        log.info(Localisation.access("logs.task_finished")
                .set("time", Utils.formatTime(System.currentTimeMillis() - startTime))
                .get()
        );
    }

    private Map<String, byte[]> processClasses(Map<String, ClassNode> classes)
    {
        for (INameObfuscationProcessor nameObfuscationProcessor : this.nameObfuscationProcessors)
            nameObfuscationProcessor.transformPost(this, classes);

        long startTime = System.currentTimeMillis();

        int threadCount = this.config.getNThreads();
        log.info(Localisation.access("logs.obfuscation.transformer.begin")
                .set("threads", threadCount)
                .set("classes", classes.size())
                .get()
        );
        Map<String, byte[]> toWrite = this.transformClasses(classes, threadCount);
        log.info(Localisation.access("logs.task_finished")
                .set("time", Utils.formatTime(System.currentTimeMillis() - startTime))
                .get()
        );

        return toWrite;
    }

    public void setMainClass(String newMainClass)
    {
        log.info(Localisation.access("logs.obfuscation.resources.main_class.detected_change")
                .set("newMainClass", newMainClass)
                .get()
        );

        if (this.packager.isEnabled() && this.packager.getMainClass().equals(this.mainClass))
            this.packager.setMainClass(newMainClass);

        this.mainClass = newMainClass;
        this.mainClassChanged = true;

    }

    private Map<String, byte[]> transformClasses(Map<String, ClassNode> classes, int threadCount)
    {
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount, new ThreadFactoryBuilder()
                .setNameFormat("Worker-%d")
                .build()
        );

        LinkedList<Map.Entry<String, ClassNode>> classQueue = new LinkedList<>(classes.entrySet());
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
                boolean isPackagerClassDecrypter = this.packager.isEnabled() && this.packager.isPackagerClassDecrypter(cn);

                try
                {
                    this.computeMode = ModifiedClassWriter.COMPUTE_MAXS;

                    boolean isSkippedByScript = this.script != null && this.script.isObfuscatorEnabled(cn);
                    if (isSkippedByScript || this.isExcludedClass(cn.name))
                    {
                        log.info(Localisation.access("logs.obfuscation.transforming.skipped")
                                             .set("proceedClasses", processed.get())
                                             .set("totalClasses", classes.size())
                                             .set("entryName", entryName)
                                             .get()
                        );
                    }

                    log.debug(Localisation.access("logs.obfuscation.transforming.processing")
                            .set("proceedClasses", processed.get())
                            .set("totalClasses", classes.size())
                            .set("entryName", entryName)
                            .get()
                    );

                    for (IClassTransformer proc : this.processors)
                    {
                        boolean shouldProcess = shouldProcess(cn, proc);
                        if (!shouldProcess)
                        {
                            log.info(Localisation.access("logs.obfuscation.transforming.skipped.annotation")
                                    .set("proceedClasses", processed.get())
                                    .set("totalClasses", classes.size())
                                    .set("entryName", entryName)
                                    .get()
                            );
                            continue;
                        }

                        try
                        {
                            proc.process(callback, cn);
                        }
                        catch (Exception e)
                        {
                            log.error(Localisation.access("logs.obfuscation.transforming.error")
                                    .set("proceedClasses", processed.get())
                                    .set("totalClasses", classes.size())
                                    .set("entryName", entryName)
                                    .get(),
                                    e
                            );

                            throw e;
                        }
                    }

                    if (callback.isForceComputeFrames())
                        cn.methods.forEach(method -> Arrays.stream(method.instructions.toArray())
                                .filter(abstractInsnNode -> abstractInsnNode instanceof FrameNode)
                                .forEach(abstractInsnNode -> method.instructions.remove(abstractInsnNode)));


                    int mode = this.computeMode
                            | (callback.isForceComputeFrames() ? ModifiedClassWriter.COMPUTE_MAXS: 0);

                    log.debug(Localisation.access("logs.obfuscation.transforming.writing")
                            .set("proceedClasses", processed.get())
                            .set("totalClasses", classes.size())
                            .set("entryName", entryName)
                            .set("computingMode", mode)
                            .get()
                    );

                    removeObfuscateRuleAnnotations(cn);

                    ModifiedClassWriter writer = new ModifiedClassWriter(
                            mode
//                                            ModifiedClassWriter.COMPUTE_MAXS |
//                                            ModifiedClassWriter.COMPUTE_FRAMES
                    );
                    cn.accept(writer);

                    entryData = writer.toByteArray();

                    if (this.packager.isEnabled() && !isPackagerClassDecrypter)
                    {
                        entryName = this.packager.encryptName(entryName.replace(".class", ""));
                        entryData = this.packager.encryptClass(entryData);
                    }
                }
                catch (Exception e)
                {
                    log.error(Localisation.access("logs.obfuscation.transforming.error")
                            .set("threadName", Thread.currentThread().getName())
                            .set("proceedClasses", processed.get())
                            .set("totalClasses", classes.size())
                            .set("entryName", entryName)
                            .get(),
                            e
                    );

                    throw e;
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
            catch (InterruptedException e)
            {
                log.error("Error getting future", e);
            }
            catch (ExecutionException e)
            {
                if (e.getCause() instanceof Exception)
                    JavaObfuscator.setLastException((Exception) e.getCause());

                log.error("Error getting future", e);
            }

        executorService.shutdown();

        return toWrite;
    }

    public void writeEntry(ZipOutputStream outJar, String name, byte[] value, boolean stored) throws IOException
    {
        ZipEntry newEntry = new ZipEntry(name);

        log.debug(Localisation.access("logs.obfuscation.copying_entry")
                             .set("entryName", name)
                             .get()
        );

        if (stored)
        {
            CRC32 crc = new CRC32();
            crc.update(value, 0, value.length);

            newEntry.setSize(value.length);
            newEntry.setCrc(crc.getValue());
        }


        outJar.putNextEntry(newEntry);
        outJar.write(value);
    }

    private boolean shouldProcess(ClassNode node, IClassTransformer processor)
    {
        String obfuscateRulePath = ObfuscateRule.class.getName().replace('.', '/');

        ObfuscateRule[] rules = null;
        if (node.invisibleTypeAnnotations != null)
            rules = node.invisibleTypeAnnotations.stream()
                    .filter(typeAnnotationNode -> typeAnnotationNode.desc.equals("L" + obfuscateRulePath + ";"))
                    .map(typeAnnotationNode -> {
                        Object[] values = typeAnnotationNode.values.toArray();
                        if (values.length != 2)
                            throw new RuntimeException("Invalid ObfuscateRule annotation");

                        return new ObfuscateRule()
                        {

                            @Override
                            public Class<? extends Annotation> annotationType()
                            {
                                return ObfuscateRule.class;
                            }

                            @Override
                            public Action value()
                            {
                                return (Action) values[1];
                            }

                            @Override
                            public ObfuscationTransformer[] processors()
                            {
                                return (ObfuscationTransformer[]) values[0];
                            }
                        };
                    })
                    .toArray(ObfuscateRule[]::new);

        if (rules == null)
            return true;

        for (ObfuscateRule rule : rules)
        {
            if (rule.value() == ObfuscateRule.Action.ALLOW)
                continue;

            for (ObfuscationTransformer transformer : rule.processors())
                if (processor.getType() == transformer)
                    return false;
        }

        return true;
    }

    public ClassNode processClass(ClassNode node)
    {
        for (IClassTransformer proc : this.processors)
        {
            if (this.script != null && !this.script.isObfuscatorEnabled(node))
                continue;

            if (this.isExcludedClass(node.name))
                continue;

            try
            {
                proc.process(new ProcessorCallback(), node);
            }
            catch (Exception e)
            {
                log.error(Localisation.access("logs.obfuscation.transforming.error")
                        .set("entryName", node.name)
                        .get(),
                        e
                );
            }
        }

        return node;
    }
}
