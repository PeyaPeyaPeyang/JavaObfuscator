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

package tokyo.peya.obfuscator;

import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import tokyo.peya.obfuscator.annotations.ObfuscateRule;
import tokyo.peya.obfuscator.annotations.ObfuscationTransformer;
import tokyo.peya.obfuscator.clazz.ClassReference;
import tokyo.peya.obfuscator.clazz.ClassTree;
import tokyo.peya.obfuscator.clazz.ClassWrapper;
import tokyo.peya.obfuscator.clazz.ModifiedClassWriter;
import tokyo.peya.obfuscator.clazz.ObfuscatorClassLoader;
import tokyo.peya.obfuscator.configuration.Configuration;
import tokyo.peya.obfuscator.configuration.ValueManager;
import tokyo.peya.obfuscator.processor.InvokeDynamic;
import tokyo.peya.obfuscator.processor.Packager;
import tokyo.peya.obfuscator.processor.Processors;
import tokyo.peya.obfuscator.processor.naming.INameObfuscationProcessor;
import tokyo.peya.obfuscator.processor.naming.entrypoint.EntrypointDelegate;
import tokyo.peya.obfuscator.processor.naming.entrypoint.EntrypointDelegateProvider;
import tokyo.peya.obfuscator.state.ClassReadingContext;
import tokyo.peya.obfuscator.state.ClassesWritingContext;
import tokyo.peya.obfuscator.state.ClasspathReadingContext;
import tokyo.peya.obfuscator.state.EncodingContext;
import tokyo.peya.obfuscator.state.NameProcessingContext;
import tokyo.peya.obfuscator.state.ObfuscationState;
import tokyo.peya.obfuscator.state.ObfuscationStatus;
import tokyo.peya.obfuscator.state.ProcessingContext;
import tokyo.peya.obfuscator.state.ResourcesWritingContext;
import tokyo.peya.obfuscator.ui.PreviewGenerator;
import tokyo.peya.obfuscator.utils.ExcludePattern;
import tokyo.peya.obfuscator.utils.MissingClassException;
import tokyo.peya.obfuscator.utils.ParallelExecutor;
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
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
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
    @Getter
    private final Configuration config;
    private final UniqueNameProvider nameProvider;
    private final EntrypointDelegateProvider entrypointDelegateProvider;
    private final Packager packager;
    private final InvokeDynamic invokeDynamic;
    private final HashMap<String, byte[]> files;
    private final Map<ClassReference, ClassWrapper> classPath;
    private final HashMap<ClassReference, ClassNode> classes;
    private final Map<ClassReference, ClassTree> hierarchy;
    private final Set<ClassWrapper> libraryClassNodes;
    private final List<IClassTransformer> processors;
    private final List<INameObfuscationProcessor> nameObfuscationProcessors;
    private final List<Pattern> excludePatterns;

    @Setter
    private ObfuscationStatus status;
    public ScriptBridge script;
    private boolean entrypointChanged;
    private ClassReference mainClass;
    private int computeMode;

    static
    {
        ValueManager.registerClass(SETTINGS);
    }

    public Obfuscator(Configuration config)
    {
        this.config = config;

        this.nameProvider = new UniqueNameProvider(SETTINGS);
        this.entrypointDelegateProvider = new EntrypointDelegateProvider();
        this.packager = new Packager(this);
        this.invokeDynamic = new InvokeDynamic(this);
        this.files = new HashMap<>();
        this.classPath = new HashMap<>();
        this.classes = new HashMap<>();
        this.hierarchy = new HashMap<>();
        this.libraryClassNodes = new HashSet<>();
        this.excludePatterns = compileExcludePatterns();
        this.status = new ObfuscationStatus();

        this.mainClass = null;

        this.processors = new ArrayList<>();
        this.nameObfuscationProcessors = new ArrayList<>();

        this.processors.addAll(Processors.createProcessors(this));
        this.nameObfuscationProcessors.addAll(Processors.createNameProcessors(this));
    }

    private boolean isExcludedClass(String name)
    {
        for (Pattern pattern : this.excludePatterns)
            if (pattern.matcher(name).matches())
                return true;

        return false;
    }

    public ClassTree getTree(ClassReference ref)
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
        ClassReference ref = ClassReference.of(classWrapper.classNode.name);
        if (this.hierarchy.get(ref) == null)
        {
            ClassTree tree = new ClassTree(classWrapper);
            if (classWrapper.classNode.superName != null)
            {
                ClassReference superRef = ClassReference.of(classWrapper.classNode.superName);
                tree.parentClasses.add(superRef);
                ClassWrapper superClass = this.classPath.get(superRef);

                if (superClass == null)
                {
                    if (!acceptMissingClass)
                        throw new MissingClassException(
                                Localisation.access("logs.obfuscation.hierarchy.missing_super_class.fatal")
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
                    if (this.hierarchy.get(superRef).missingSuperClass)
                        tree.missingSuperClass = true;
                }
            }
            if (classWrapper.classNode.interfaces != null && !classWrapper.classNode.interfaces.isEmpty())
            {
                for (String s : classWrapper.classNode.interfaces)
                {
                    ClassReference interfaceRef = ClassReference.of(s);
                    tree.parentClasses.add(interfaceRef);
                    ClassWrapper interfaceClass = this.classPath.get(interfaceRef);

                    if (interfaceClass == null)
                    {
                        if (!acceptMissingClass)
                            throw new MissingClassException(
                                    Localisation.access("logs.obfuscation.hierarchy.missing_interface.fatal")
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
                        if (this.hierarchy.get(interfaceRef).missingSuperClass)
                            tree.missingSuperClass = true;
                    }
                }
            }

            this.hierarchy.put(ref, tree);
        }
        if (sub != null)
            this.hierarchy.get(ref).subClasses.add(ClassReference.of(sub.classNode.name));
    }

    private List<byte[]> loadClasspathFile(File file, ClasspathReadingContext ctxt) throws IOException
    {
        ZipFile zipIn = new ZipFile(file);
        Enumeration<? extends ZipEntry> entries = zipIn.entries();
        ctxt.setTotalClassesToLoad(zipIn.size());

        boolean isModule = file.getName().endsWith(".jmod");

        List<byte[]> byteList = new ArrayList<>(zipIn.size());

        long loaded = 0;
        while (entries.hasMoreElements())
        {
            ZipEntry ent = entries.nextElement();
            if (ent.getName().endsWith(".class")
                    && (!isModule || !ent.getName().endsWith("module-info.class")
                    && ent.getName().startsWith("classes/")))
            {
                ctxt.setLoadingClassName(file.getName() + " -> " + ent.getName());
                byteList.add(ByteStreams.toByteArray(zipIn.getInputStream(ent)));
            }

            ctxt.setTotalClassesLoaded(++loaded);
        }
        zipIn.close();

        return byteList;
    }

    private void loadClasspath(List<String> libraryFileNames) throws IOException
    {
        ClasspathReadingContext context = new ClasspathReadingContext(this.status);
        context.setTotalFilesToRead(libraryFileNames.size());
        this.status.setState(
                ObfuscationState.READING_CLASS_PATH,
                context
        );

        long startTime = System.currentTimeMillis();
        log.info(Localisation.get("logs.obfuscation.classpath.loading"));

        List<File> libraryFiles = new ArrayList<>();
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
            libraryFiles.add(file);
        }

        int i = 0;
        LinkedList<byte[]> classList = new LinkedList<>();
        for (File file : libraryFiles)
        {
            if (file.isFile())
            {
                log.info(Localisation.access("logs.obfuscation.classpath.loading_each")
                                     .set("filePath", file.getAbsolutePath())
                                     .set("percent", ++i * 100 / libraryFiles.size())
                                     .get()
                );
                classList.addAll(loadClasspathFile(file, context));
                context.setTotalFilesLoaded(i++);
                continue;
            }

            try (Stream<Path> stream = Files.walk(file.toPath()))
            {
                AtomicLong totalFiles = new AtomicLong(0);
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
                              classList.addAll(loadClasspathFile(f, context));
                          }
                          catch (IOException e)
                          {
                              log.warn(Localisation.access("logs.obfuscation.classpath.read.fail")
                                                   .set("fileName", f.getName())
                                                   .set("filePath", f.getAbsolutePath())
                                                   .get()
                              );
                          }

                          context.setTotalFilesLoaded(totalFiles.incrementAndGet());
                      });
            }
        }

        log.info(Localisation.access("logs.obfuscation.classpath.read.success")
                             .set("classes", classList.size())
                             .get()
        );

        this.classPath.putAll(parseClassPath(context, classList, this.config.getNThreads()));
        this.libraryClassNodes.addAll(this.classPath.values());


        log.info(Localisation.access("logs.task_finished")
                             .set("time", Utils.formatTime(System.currentTimeMillis() - startTime))
                             .get()
        );

    }

    private Map<ClassReference, ClassWrapper> parseClassPath(ClasspathReadingContext ctxt, final LinkedList<byte[]> byteList, int threads)
    {
        ctxt.reset();
        ctxt.setTotalClassesToLoad(byteList.size());

        log.info(Localisation.get("logs.obfuscation.classpath.parsing_class"));
        AtomicLong totalClassesRead = new AtomicLong(0);
        return ParallelExecutor.runInParallelAndMerge(
                threads, () -> () -> {
                    Map<ClassReference, ClassWrapper> localMap = new HashMap<>();
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
                        localMap.put(ClassReference.of(node), wrapper);
                        ctxt.setLoadingClassName(node.name);
                        ctxt.setTotalClassesLoaded(totalClassesRead.incrementAndGet());
                    }
                    return localMap;
                }
        );
    }

    public boolean isLibrary(ClassNode classNode)
    {
        return this.libraryClassNodes.stream().anyMatch(e -> e.classNode.name.equals(classNode.name));
    }

    public boolean isLoadedCode(ClassNode classNode)
    {
        return this.classes.containsKey(ClassReference.of(classNode));
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

            if (isClass(this.config.getInput()))  // .jar ではなく, .class ファイルが指定された場合の処理
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
            this.status.setState(ObfuscationState.DONE, null);
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
        ClassReadingContext ctxt = new ClassReadingContext(this.status);
        try (ZipFile zipFile = new ZipFile(this.config.getInput()))
        {
            ctxt.setTotalClassesToRead(zipFile.size());
        }
        this.status.setState(
                ObfuscationState.READING_CLASSES,
                ctxt
        );

        long classes = 0;
        Map<String, byte[]> classDataMap = new HashMap<>();
        while (true)
        {
            ZipEntry entry = input.getNextEntry();

            if (entry == null)
                break;

            String entryName = entry.getName();
            if (entry.isDirectory())
            {
                outJar.putNextEntry(entry);
                continue;
            }

            ctxt.setReadingClassName(this.config.getInput() + " -> " + entry.getName());

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

            // メイン・クラスの差し替え
            this.entrypointDelegateProvider.enableDelegateAuto(entryName, entryData);
            EntrypointDelegate delegate = this.entrypointDelegateProvider.getOptimalDelegateFor(entryName, entryData);
            if (delegate != null)
                this.setMainClass(delegate.getEntrypointClassReference(entryName, entryData));

            if (!entryName.endsWith(".class"))
            {
                this.files.put(entryName, entryData);
                continue;
            }

            registerClassBytes(entryName, entryData);
            classDataMap.put(entryName, entryData);

            ctxt.setTotalClassesRead(++classes);
        }

        return classDataMap;
    }

    private void registerClassBytes(String file, byte[] classBytes)
    {
        try
        {
            ClassNode cn = PreviewGenerator.toClassNode(classBytes);
            this.classes.put(ClassReference.of(cn.name), cn);
        }
        catch (Exception e)
        {
            log.warn(
                    Localisation.access("logs.obfuscation.error.fail_read").get(),
                    e
            );
            this.files.put(file, classBytes);
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

        for (Map.Entry<ClassReference, ClassNode> stringClassNodeEntry : this.classes.entrySet())
            this.classPath.put(
                    stringClassNodeEntry.getKey(),
                    new ClassWrapper(
                            stringClassNodeEntry.getValue(),
                            false,
                            classDataMap.get(stringClassNodeEntry.getKey().getFileNameFull())
                    )
            );

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
        ClassNode obfuscatedPackagerNode = this.processClass(packagerNode);
        ClassReference packagerRef = ClassReference.of(obfuscatedPackagerNode);

        // クラスパスの整合性
        ObfuscatorClassLoader.addTempClass(packagerRef, obfuscatedPackagerNode);

        ModifiedClassWriter writer = new ModifiedClassWriter(3);
        obfuscatedPackagerNode.accept(writer);

        ObfuscatorClassLoader.removeTempClass(packagerRef);

        byte[] packagerBytes = writer.toByteArray();

        log.info(Localisation.access("logs.task_finished")
                             .set("time", Utils.formatTime(System.currentTimeMillis() - startTime))
                             .get()
        );

        return new HashMap<>()
        {{
            this.put(ClassReference.of(obfuscatedPackagerNode.name).getFileNameFull(), packagerBytes);
        }};
    }

    private void finishOutJar(Map<String, byte[]> classes, ZipOutputStream outJar, boolean stored) throws IOException
    {
        log.info(Localisation.access("logs.obfuscation.transforming.writing_artifact")
                             .set("outputPath", this.config.getOutput())
                             .get()
        );
        long startTime = System.currentTimeMillis();

        this.writeClasses(outJar, classes, stored);

        log.info(Localisation.access("logs.task_finished")
                             .set("time", Utils.formatTime(System.currentTimeMillis() - startTime))
                             .get()
        );

        this.writeResources(outJar, stored);
    }

    private void writeClasses(ZipOutputStream outJar, Map<String, byte[]> classes, boolean stored) throws IOException
    {
        ClassesWritingContext ctxt = new ClassesWritingContext(this.status);
        ctxt.setTotalClassesToWrite(classes.size());
        this.status.setState(
                ObfuscationState.WRITING_CLASSES,
                ctxt
        );

        Map<String, byte[]> toWrite = new HashMap<>(classes);

        if (this.packager.isEnabled())
            toWrite.putAll(generatePackageDecrypter());

        long written = 0;
        for (Map.Entry<String, byte[]> stringEntry : toWrite.entrySet())
        {
            ctxt.setWritingClassName(stringEntry.getKey());
            writeEntry(outJar, stringEntry.getKey(), stringEntry.getValue(), stored);
            ctxt.setTotalClassesWritten(++written);
        }
    }

    private void writeResources(ZipOutputStream outJar, boolean stored) throws IOException
    {
        ResourcesWritingContext ctxt = new ResourcesWritingContext(this.status);
        ctxt.setTotalResourcesToWrite(this.files.size());
        this.status.setState(
                ObfuscationState.WRITING_RESOURCES,
                ctxt
        );

        long written = 0;
        long startTime = System.currentTimeMillis();
        log.info(Localisation.get("logs.obfuscation.resources.writing"));
        for (Map.Entry<String, byte[]> fileEntry : this.files.entrySet())
        {
            String entryName = fileEntry.getKey();
            byte[] entryData = fileEntry.getValue();

            ctxt.setWritingResourceName(entryName);
            if (this.entrypointChanged && this.entrypointDelegateProvider.isDelegateEnabled(entryName))
            {
                entryData = this.entrypointDelegateProvider.renameMainClass(
                        entryName,
                        this.mainClass,
                        entryData
                );
                log.info(Localisation.access("logs.obfuscation.resources.main_class.replaced")
                                     .set("newMainClass", this.mainClass)
                                     .get()
                );
            }

            writeEntry(outJar, entryName, entryData, stored);
            ctxt.setTotalResourcesWritten(++written);
        }

        log.info(Localisation.access("logs.task_finished")
                             .set("time", Utils.formatTime(System.currentTimeMillis() - startTime))
                             .get()
        );
    }

    private Map<String, byte[]> processClasses(Map<ClassReference, ClassNode> classes)
    {
        ProcessingContext ctxt = new ProcessingContext(this.status);
        ctxt.setTotalClassesToProcess(classes.size());

        this.status.setState(
                ObfuscationState.PROCESSING_CLASSES,
                ctxt
        );

        long startTime = System.currentTimeMillis();

        int threadCount = this.config.getNThreads();
        log.info(Localisation.access("logs.obfuscation.transformer.begin")
                             .set("threads", threadCount)
                             .set("classes", classes.size())
                             .get()
        );

        Map<ClassReference, ClassNode> transformed = this.transformClasses(ctxt, classes, this.processors, threadCount);
        this.processNameObfuscation(transformed);

        // InvokeDynamic を後から実行することで, NameObfuscationProcessor 後のクラス名変更に対応する
        if (InvokeDynamic.isEnabled())
            transformed = this.transformClasses(ctxt, transformed, List.of(this.invokeDynamic), threadCount);

        Map<String, byte[]> toWrite = this.encodeClasses(transformed, threadCount);
        log.info(Localisation.access("logs.task_finished")
                             .set("time", Utils.formatTime(System.currentTimeMillis() - startTime))
                             .get()
        );

        return toWrite;
    }

    private void processNameObfuscation(
            Map<ClassReference, ClassNode> classes
    )
    {
        NameProcessingContext nameContext = new NameProcessingContext(this.status);
        nameContext.setTotalNamesToProcess((long) classes.size() * (long) this.nameObfuscationProcessors.size());
        this.status.setState(
                ObfuscationState.PROCESSING_CLASS_NAMES,
                nameContext
        );

        for (INameObfuscationProcessor nameObfuscationProcessor : this.nameObfuscationProcessors)
            nameObfuscationProcessor.transformPost(this, nameContext, classes);
    }

    public void setMainClass(ClassReference newMainClass)
    {
        log.info(Localisation.access("logs.obfuscation.resources.main_class.detected_change")
                             .set("newMainClass", newMainClass)
                             .get()
        );

        if (this.packager.isEnabled() && this.packager.getMainClass() == null || Objects.equals(this.mainClass, this.packager.getMainClass()))
            this.packager.setMainClass(newMainClass);

        this.mainClass = newMainClass;
        this.entrypointChanged = true;
    }

    private Map<ClassReference, ClassNode> transformClasses(ProcessingContext ctxt,
                                                            Map<ClassReference, ClassNode> classes,
                                                            List<? extends IClassTransformer> processors,
                                                            int threadCount)
    {
        LinkedList<Map.Entry<ClassReference, ClassNode>> classQueue = new LinkedList<>(classes.entrySet());
        AtomicLong processed = new AtomicLong(0);
        return ParallelExecutor.runInParallelAndMerge(threadCount, () -> () -> {
            Map<ClassReference, ClassNode> toWriteThread = new HashMap<>();

            while (true)
            {
                Map.Entry<ClassReference, ClassNode> classEntry;

                synchronized (classQueue)
                {
                    classEntry = classQueue.poll();
                }

                if (classEntry == null)
                    break;

                ProcessorCallback callback = new ProcessorCallback();
                ClassReference reference = classEntry.getKey();
                ClassNode cn = classEntry.getValue();

                try
                {
                    this.computeMode = ModifiedClassWriter.COMPUTE_MAXS;

                    boolean isSkippedByScript = !(this.script == null || this.script.isObfuscatorEnabled(cn));
                    if (isSkippedByScript || this.isExcludedClass(cn.name)) {
                        log.info(Localisation.access("logs.obfuscation.transforming.skipped")
                                             .set("proceedClasses", processed.get())
                                             .set("totalClasses", classes.size())
                                             .set("entryName", reference)
                                             .get());
                    }

                    log.debug(Localisation.access("logs.obfuscation.transforming.processing")
                                          .set("proceedClasses", processed.get())
                                          .set("totalClasses", classes.size())
                                          .set("entryName", reference)
                                          .get());

                    for (IClassTransformer proc : processors)
                    {
                        boolean shouldProcess = shouldProcess(cn, proc);
                        if (!shouldProcess) {
                            log.info(Localisation.access("logs.obfuscation.transforming.skipped.annotation")
                                                 .set("proceedClasses", processed.get())
                                                 .set("totalClasses", classes.size())
                                                 .set("entryName", reference)
                                                 .get());
                            continue;
                        }

                        try
                        {
                            ctxt.setProcessingClassName(reference.getFileNameFull());
                            proc.process(callback, cn);
                        }
                        catch (Exception e)
                        {
                            log.error(Localisation.access("logs.obfuscation.transforming.error")
                                                  .set("proceedClasses", processed.get())
                                                  .set("totalClasses", classes.size())
                                                  .set("entryName", reference)
                                                  .get(), e);
                            throw e;
                        }
                    }

                    if (callback.isForceComputeFrames())
                        cn.methods.forEach(method -> Arrays.stream(method.instructions.toArray())
                                                           .filter(insn -> insn instanceof FrameNode)
                                                           .forEach(insn -> method.instructions.remove(insn)));

                    this.computeMode = this.computeMode | (callback.isForceComputeFrames() ? ModifiedClassWriter.COMPUTE_FRAMES : 0);

                    removeObfuscateRuleAnnotations(cn);

                    if (!callback.getAdditionalClasses().isEmpty())
                        callback.getAdditionalClasses().forEach(
                                classNode -> {
                                    ClassReference classRef = ClassReference.of(classNode);
                                    toWriteThread.put(classRef, classNode);
                                    this.classPath.put(classRef, new ClassWrapper(classNode, false, null));
                                    this.classes.put(classRef, classNode);
                                }
                        );

                    toWriteThread.put(reference, cn);
                    ctxt.setTotalClassesProcessed(processed.incrementAndGet());
                } catch (Exception e) {
                    log.error(Localisation.access("logs.obfuscation.transforming.error")
                                          .set("threadName", Thread.currentThread().getName())
                                          .set("proceedClasses", processed.get())
                                          .set("totalClasses", classes.size())
                                          .set("entryName", reference)
                                          .get(), e);

                    JavaObfuscator.setLastException(e);
                    throw e;
                }
            }

            return toWriteThread;
        });
    }

    private Map<String, byte[]> encodeClasses(Map<ClassReference, ClassNode> classes, int threadCount)
    {
        EncodingContext ctxt = new EncodingContext(this.status);
        ctxt.setTotalClassesToEncode(classes.size());
        this.status.setState(
                ObfuscationState.ENCODING_CLASSES,
                ctxt
        );

        LinkedList<Map.Entry<ClassReference, ClassNode>> classQueue = new LinkedList<>(classes.entrySet());

        AtomicLong processed = new AtomicLong(0);
        return ParallelExecutor.runInParallelAndMerge(this.config.getNThreads(), () -> () -> {
            Map<String, byte[]> toWriteThread = new HashMap<>();
            while (true)
            {
                Map.Entry<ClassReference, ClassNode> stringClassNodeEntry;

                synchronized (classQueue)
                {
                    stringClassNodeEntry = classQueue.poll();
                }

                if (stringClassNodeEntry == null)
                    break;

                ClassReference entryName = stringClassNodeEntry.getKey();
                String writePath = entryName.getFileNameFull();

                byte[] entryData;
                ClassNode cn = stringClassNodeEntry.getValue();
                boolean isPackagerClassDecrypter =
                        this.packager.isEnabled() && this.packager.isPackagerClassDecrypter(cn);

                try
                {
                    int mode = this.computeMode;
                    log.debug(Localisation.access("logs.obfuscation.transforming.writing")
                                          .set("proceedClasses", processed.get())
                                          .set("totalClasses", classes.size())
                                          .set("entryName", entryName)
                                          .set("computingMode", mode)
                                          .get()
                    );

                    removeObfuscateRuleAnnotations(cn);

                    ctxt.setEncodingClassName(writePath);
                    ModifiedClassWriter writer = new ModifiedClassWriter(
                            mode
//                                            ModifiedClassWriter.COMPUTE_MAXS |
//                                            ModifiedClassWriter.COMPUTE_FRAMES
                    );
                    cn.accept(writer);

                    entryData = writer.toByteArray();

                    if (this.packager.isEnabled() && !isPackagerClassDecrypter)
                    {
                        writePath = this.packager.encryptName(writePath);
                        entryData = this.packager.encryptClass(entryData);
                    }
                }
                catch (Exception e)
                {
                    log.error(
                            Localisation.access("logs.obfuscation.transforming.error")
                                        .set("threadName", Thread.currentThread().getName())
                                        .set("proceedClasses", processed.get())
                                        .set("totalClasses", classes.size())
                                        .set("entryName", entryName)
                                        .get(),
                            e
                    );

                    throw e;
                }

                toWriteThread.put(writePath, entryData);

                ctxt.setTotalClassesEncoded(processed.incrementAndGet());
            }

            return toWriteThread;
        });
    }

    public void writeEntry(ZipOutputStream outJar, String inJarPath, byte[] value, boolean stored) throws IOException
    {
        ZipEntry newEntry = new ZipEntry(inJarPath);

        log.debug(Localisation.access("logs.obfuscation.copying_entry")
                              .set("entryName", inJarPath)
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
        if (this.classPath.isEmpty())
        {
            try
            {
                loadClasspath(this.config.getLibraries());
            }
            catch (IOException e)
            {
                log.error("Failed to load classpath", e);
                throw new IllegalStateException("Failed to load classpath", e);
            }
        }

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
                log.error("ERR!!", e);
            }
        }

        // 以下, 名前系の難読化のための処理

        // 変わったあとも見つけるためのマーカーアノテーションを追加
        AnnotationNode markerAnnotation = new AnnotationNode("LObfuscation$" + new Random().nextInt(1000) + ";");
        if (node.invisibleAnnotations == null)
            node.invisibleAnnotations = new ArrayList<>();
        node.invisibleAnnotations.add(markerAnnotation);

        Map<ClassReference, ClassNode> classPath = new HashMap<>()
        {{
            put(ClassReference.of(node), node);
        }};

        NameProcessingContext nameContext = new NameProcessingContext(this.status);

        // 変形処理をする
        for (INameObfuscationProcessor nameObfuscationProcessor : this.nameObfuscationProcessors)
            nameObfuscationProcessor.transformPost(this, nameContext,  classPath);

        // 変形処理後の, メインクラスを探し出す
        ClassNode mainClassNode = classPath.values().stream()
                                           .filter(cn -> cn.invisibleAnnotations.stream()
                                                                                .anyMatch(annotationNode -> annotationNode.desc.equals(
                                                                                        markerAnnotation.desc)))
                                           .findFirst()
                                           .orElseThrow(() -> new IllegalStateException(
                                                   "Main class not found after transformation"));

        // マーカー・アノテーションを削除
        if (mainClassNode.invisibleAnnotations != null)
            mainClassNode.invisibleAnnotations.removeIf(annotationNode -> annotationNode.desc.equals(markerAnnotation.desc));

        return mainClassNode;
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
}
