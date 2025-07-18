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

package tokyo.peya.obfuscator.processor.naming;

import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import tokyo.peya.obfuscator.Obfuscator;
import tokyo.peya.obfuscator.clazz.ClassReference;
import tokyo.peya.obfuscator.clazz.ClassTree;
import tokyo.peya.obfuscator.clazz.ClassWrapper;
import tokyo.peya.obfuscator.clazz.FieldWrapper;
import tokyo.peya.obfuscator.clazz.MethodWrapper;
import tokyo.peya.obfuscator.configuration.DeprecationLevel;
import tokyo.peya.obfuscator.configuration.ValueManager;
import tokyo.peya.obfuscator.configuration.values.BooleanValue;
import tokyo.peya.obfuscator.configuration.values.EnabledValue;
import tokyo.peya.obfuscator.configuration.values.FilePathValue;
import tokyo.peya.obfuscator.configuration.values.StringValue;
import tokyo.peya.obfuscator.processor.Packager;
import tokyo.peya.obfuscator.state.NameProcessingContext;
import tokyo.peya.obfuscator.utils.ExcludePattern;
import tokyo.peya.obfuscator.utils.NameUtils;
import tokyo.peya.obfuscator.utils.NodeUtils;
import tokyo.peya.obfuscator.utils.Utils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Slf4j(topic = "Processor/Name/NameObfuscation")
public class NameObfuscation implements INameObfuscationProcessor
{
    private static final String PROCESSOR_NAME = "name_obfuscation";
    private static final Random random = new Random();
    private static final EnabledValue V_ENABLED = new EnabledValue(
            PROCESSOR_NAME,
            "ui.transformers.name.description",
            DeprecationLevel.AVAILABLE,
            false
    );
    private static final StringValue V_EXCLUDED_CLASSES = new StringValue(
            PROCESSOR_NAME,
            "excluded_classes",
            "ui.transformers.name.excluded_classes",
            DeprecationLevel.AVAILABLE,
            "me.name.Class\nme.name.*\nio.netty.**",
            5
    );
    private static final StringValue V_EXCLUDED_METHODS = new StringValue(
            PROCESSOR_NAME,
            "excluded_methods",
            "ui.transformers.name.excluded_methods",
            DeprecationLevel.AVAILABLE,
            "me.name.Class.method\nme.name.Class**\nme.name.Class.*",
            5
    );
    private static final StringValue V_EXCLUDED_FIELDS = new StringValue(
            PROCESSOR_NAME,
            "excluded_fields",
            "ui.transformers.name.excluded_fields",
            DeprecationLevel.AVAILABLE,
            "me.name.Class.field\nme.name.Class.*\nme.name.**",
            5
    );
    private static final BooleanValue V_ALLOW_MISSING_LIBRARIES = new BooleanValue(
            PROCESSOR_NAME,
            "allow_missing_libraries",
            "ui.transformers.name.allow_missing_libraries",
            DeprecationLevel.AVAILABLE,
            false
    );
    private static final BooleanValue V_REMAP_CLASSES = new BooleanValue(
            PROCESSOR_NAME,
            "enabled_for_class_names",
            "ui.transformers.name.enable_class",
            DeprecationLevel.AVAILABLE,
            true
    );
    private static final BooleanValue V_REMAP_METHODS = new BooleanValue(
            PROCESSOR_NAME,
            "enabled_for_method_names",
            "ui.transformers.name.enable_method",
            DeprecationLevel.AVAILABLE,
            true
    );
    private static final BooleanValue V_REMAP_FIELDS = new BooleanValue(
            PROCESSOR_NAME,
            "enabled_for_field_names",
            "ui.transformers.name.enable_field",
            DeprecationLevel.AVAILABLE,
            true
    );
    private static final BooleanValue V_RANDOM_PACKAGE = new BooleanValue(
            PROCESSOR_NAME,
            "randomise_package_structure",
            "ui.transformers.name.randomise_package",
            DeprecationLevel.AVAILABLE,
            true
    );
    private static final BooleanValue V_CAMOUFLAGE_PACKAGE = new BooleanValue(
            PROCESSOR_NAME,
            "camouflage_package",
            "ui.transformers.name.camouflage_package",
            DeprecationLevel.AVAILABLE,
            false
    );
    private static final StringValue V_NEW_PACKAGE = new StringValue(
            PROCESSOR_NAME,
            "new_packages",
            "ui.transformers.name.new_packages",
            DeprecationLevel.AVAILABLE,
            "",
            5
    );
    private static final BooleanValue V_RANDOM_SOURCE_FILE = new BooleanValue(
            PROCESSOR_NAME,
            "randomise_source_file_names",
            "ui.transformers.name.randomise_source_file",
            DeprecationLevel.AVAILABLE,
            false
    );
    private static final BooleanValue V_RANDOM_DEBUG_SOURCE_FILE = new BooleanValue(
            PROCESSOR_NAME,
            "randomise_debug_source_file_names",
            "ui.transformers.name.randomise_debug_source_file",
            DeprecationLevel.AVAILABLE,
            false
    );
    private static final StringValue V_NEW_SOURCE_FILE = new StringValue(
            PROCESSOR_NAME,
            "new_source_and_debug_file_names",
            "ui.transformers.name.new_source_names",
            DeprecationLevel.AVAILABLE,
            "",
            5
    );

    private static final BooleanValue V_SAVE_MAPPINGS = new BooleanValue(
            PROCESSOR_NAME,
            "save_mappings",
            "ui.transformers.name.save_mappings",
            DeprecationLevel.AVAILABLE,
            false
    );
    private static final FilePathValue V_MAPPINGS_FILE_TO_SAVE = new FilePathValue(
            PROCESSOR_NAME,
            "mappings_file_to_save",
            "ui.transformers.name.mappings_file",
            DeprecationLevel.AVAILABLE,
            null
    );
    private final Obfuscator obfuscator;
    private final List<Pattern> excludedClassesPatterns = new ArrayList<>();
    private final List<Pattern> excludedMethodsPatterns = new ArrayList<>();
    private final List<Pattern> excludedFieldsPatterns = new ArrayList<>();
    private List<String> packageNames;
    private List<String> sourceFileNames;

    static
    {
        ValueManager.registerOwner(PROCESSOR_NAME, "ui.transformers.name");
        ValueManager.registerClass(NameObfuscation.class);
    }

    public NameObfuscation(Obfuscator obfuscator)
    {
        this.obfuscator = obfuscator;
    }

    public void setupRandomizers()
    {
        if (V_RANDOM_PACKAGE.get())
        {
            String[] newPackages = V_NEW_PACKAGE.get().split("\n");
            this.packageNames = Arrays.asList(newPackages);
        }

        if (V_RANDOM_SOURCE_FILE.get() || V_RANDOM_DEBUG_SOURCE_FILE.get())
        {
            String[] newSourceFiles = V_NEW_SOURCE_FILE.get().split("\n");
            this.sourceFileNames = Arrays.asList(newSourceFiles);
        }
    }

    public String generateRandomPackage(String oldPackage)
    {
        String retVal;
        if (this.packageNames.size() == 1)
        {
            String packageName = this.packageNames.get(0);
            if (V_CAMOUFLAGE_PACKAGE.get())
                retVal = CommonPackageTrees.getRandomPackage();
            else if (packageName.isEmpty())
                retVal = this.obfuscator.getNameProvider().generateClassName(oldPackage);
            else
                retVal = packageName;
        }
        else
            retVal = this.packageNames.get(random.nextInt(this.packageNames.size()));

        if (retVal.contains("."))
            retVal = retVal.replace(".", "/");
        if (retVal.startsWith("/"))
            retVal = retVal.substring(1);
        if (!retVal.endsWith("/"))
            retVal = retVal + "/";

        return retVal;

    }

    private void compileExcludePatterns()
    {
        for (String s : V_EXCLUDED_CLASSES.get().split("\n"))
            this.excludedClassesPatterns.add(ExcludePattern.compileExcludePattern(s));
        for (String s : V_EXCLUDED_METHODS.get().split("\n"))
            this.excludedMethodsPatterns.add(ExcludePattern.compileExcludePattern(s));
        for (String s : V_EXCLUDED_FIELDS.get().split("\n"))
            this.excludedFieldsPatterns.add(ExcludePattern.compileExcludePattern(s));
    }

    private List<ClassWrapper> buildHierarchies(Collection<? extends ClassNode> nodes, boolean ifAcceptMissingLib)
    {
        List<ClassWrapper> classWrappers = new ArrayList<>();

        for (ClassNode value : nodes)
        {
            ClassWrapper cw = new ClassWrapper(value, false, new byte[0]);
            classWrappers.add(cw);

            this.obfuscator.buildHierarchy(cw, null, ifAcceptMissingLib);
        }

        return classWrappers;
    }

    @Override
    public void transformPost(Obfuscator inst, NameProcessingContext ctxt, Map<ClassReference, ClassNode> nodes)
    {
        if (!V_ENABLED.get())
            return;

        try
        {
            HashMap<String, String> mappings = new HashMap<>();

            compileExcludePatterns();

            log.info("Building Hierarchy...");
            List<ClassWrapper> classWrappers = buildHierarchies(nodes.values(), V_ALLOW_MISSING_LIBRARIES.get());
            log.info("Finished building hierarchy");

            long current = System.currentTimeMillis();

            log.info("Generating mappings...");

            this.setupRandomizers();
            this.processClasses(classWrappers, mappings);

            if (V_SAVE_MAPPINGS.get())
            {
                String mappingFile = V_MAPPINGS_FILE_TO_SAVE.get();
                if (mappingFile == null)
                    mappingFile = this.obfuscator.getConfig().getMapping();
                if (mappingFile != null)
                    this.saveMappingsFile(mappingFile, mappings);
            }

            log.info(String.format(
                    "... Finished generating mappings (%s)",
                    Utils.formatTime(System.currentTimeMillis() - current)
            ));

            log.info("Applying mappings...");
            current = System.currentTimeMillis();
            this.writeClasses(ctxt, mappings, classWrappers, nodes);
            log.info(String.format(
                    "... Finished applying mappings (%s)",
                    Utils.formatTime(System.currentTimeMillis() - current)
            ));
        }
        finally
        {
            this.excludedClassesPatterns.clear();
            this.excludedMethodsPatterns.clear();
            this.excludedFieldsPatterns.clear();
        }
    }

    private void processClasses(Collection<? extends ClassWrapper> classWrappers, Map<String, String> mappings)
    {
        classWrappers.stream()
                     .filter(classWrapper -> !this.isClassExcluded(classWrapper))
                     .forEach(classWrapper -> this.processClass(classWrapper, mappings));
    }

    private void processClass(ClassWrapper clazz, Map<String, String> mappings)
    {
        clazz.classNode.access &= ~Opcodes.ACC_PRIVATE;
        clazz.classNode.access &= ~Opcodes.ACC_PROTECTED;
        clazz.classNode.access |= Opcodes.ACC_PUBLIC;
        // Inner を引っ張り出したときに, static がついてるとバグる
        clazz.classNode.access &= ~Opcodes.ACC_STATIC;

        if (V_REMAP_FIELDS.get())
            this.processFields(clazz, mappings);
        if (V_REMAP_METHODS.get())
            this.processMethods(clazz, mappings);

        if (V_RANDOM_SOURCE_FILE.get() || V_RANDOM_DEBUG_SOURCE_FILE.get())
            assignRandomSourceNameToClass(clazz.classNode);

        boolean hasNativeMethod = hasNativeMethodInClass(clazz.classNode);
        boolean enableClassRename = V_REMAP_CLASSES.get();

        if (hasNativeMethod || !enableClassRename)
        {
            if (hasNativeMethod)
                log.info("Renaming class " + clazz.originalRef + " is automatically excluded because it has native methods.");
            String originalName = clazz.originalRef.getFullQualifiedName();
            mappings.put(originalName, originalName);
            return;
        }

        String packageName;
        if (V_RANDOM_PACKAGE.get())
            packageName = generateRandomPackage(clazz.originalRef.getPackage());
        else
        {
            packageName = clazz.originalRef.getPackage();
            if (!(packageName.isEmpty() || packageName.endsWith("/")))
                packageName = packageName + "/";
        }

        ClassReference newName = ClassReference.of(packageName, this.obfuscator.getNameProvider().generateClassName());
        mappings.put(clazz.originalRef.getFullQualifiedName(), newName.getFullQualifiedName());

        if (clazz.originalRef.equals(this.obfuscator.getMainClass()))  // MANIFEST.MFの改変のため
            this.obfuscator.setMainClass(newName);
    }

    private void assignRandomSourceNameToClass(ClassNode node)
    {
        boolean isSourceNameSpecified = !this.sourceFileNames.isEmpty();

        String newSourceName;
        String newSourceDebugName;
        if (isSourceNameSpecified)
        {
            newSourceName = this.sourceFileNames.get(random.nextInt(this.sourceFileNames.size()));
            newSourceDebugName = this.sourceFileNames.get(random.nextInt(this.sourceFileNames.size()));
        }
        else
        {
            newSourceName = this.obfuscator.getNameProvider().generateClassName() + ".java";
            newSourceDebugName = newSourceName;
        }

        if (V_RANDOM_SOURCE_FILE.get())
            node.sourceFile = newSourceName;
        if (V_RANDOM_DEBUG_SOURCE_FILE.get())
            node.sourceDebug = newSourceDebugName;
    }

    private void processMethods(ClassWrapper classWrapper, Map<String, String> mappings)
    {
        Predicate<MethodWrapper> isExclude = method ->
                isMethodExcluded(classWrapper.originalRef.getFullQualifiedDotName(), method)
                        || !canRenameMethodTree(mappings, new HashSet<>(), method, classWrapper.originalRef);

        classWrapper.methods.stream()
                            .filter(isExclude.negate())
                            .forEach(methodWrapper -> this.processMethod(methodWrapper, classWrapper, mappings));
    }

    private void processMethod(MethodWrapper methodWrapper, ClassWrapper ownerClass, Map<String, String> mappings)
    {
        if (Modifier.isPrivate(methodWrapper.methodNode.access) || Modifier.isProtected(methodWrapper.methodNode.access))
        {
            methodWrapper.methodNode.access &= ~Opcodes.ACC_PRIVATE;
            methodWrapper.methodNode.access &= ~Opcodes.ACC_PROTECTED;
            methodWrapper.methodNode.access |= Opcodes.ACC_PUBLIC;
        }

        if (Modifier.isNative(methodWrapper.methodNode.access))
            log.warn("Native method found in class " + ownerClass.originalRef + " method " + methodWrapper.methodNode.name + methodWrapper.methodNode.desc);
        else
            this.renameMethodTree(
                    mappings,
                    new HashSet<>(),
                    methodWrapper,
                    ownerClass.originalRef,
                    this.obfuscator.getNameProvider()
                                   .generateMethodName(ownerClass.classNode, methodWrapper.originalDescription)
            );
    }

    private void processFields(ClassWrapper classWrapper, Map<String, String> mappings)
    {
        Predicate<FieldWrapper> isExclude = field ->
                isFieldExcluded(classWrapper.originalRef.getFullQualifiedDotName(), field)
                        || !canRenameFieldTree(mappings, new HashSet<>(), field, classWrapper.originalRef);

        classWrapper.fields.stream()
                           .filter(isExclude.negate())
                           .forEach(fieldWrapper -> processField(fieldWrapper, classWrapper, mappings));
    }

    private void processField(FieldWrapper field, ClassWrapper ownerClass, Map<String, String> mappings)
    {
        field.fieldNode.access &= ~Opcodes.ACC_PRIVATE;
        field.fieldNode.access &= ~Opcodes.ACC_PROTECTED;
        field.fieldNode.access |= Opcodes.ACC_PUBLIC;

        this.renameFieldTree(
                new HashSet<>(),
                field,
                ownerClass.originalRef,
                this.obfuscator.getNameProvider().generateFieldName(ownerClass.classNode),
                mappings
        );
    }

    private void writeClasses(NameProcessingContext ctxt,
                              HashMap<String, String> mappings,
                              List<? extends ClassWrapper> classWrappers,
                              Map<ClassReference, ? super ClassNode> ledger)
    {
        Remapper simpleRemapper = new MemberRemapper(mappings);

        for (ClassWrapper classWrapper : classWrappers)
        {
            ctxt.setProcessingName(classWrapper.originalRef.getFullQualifiedName());
            writeClass(classWrapper, simpleRemapper, ledger);
            ctxt.incrementTotalNamesProcessed();
        }
    }

    private void writeClass(ClassWrapper classWrapper, Remapper simpleRemapper,
                            Map<ClassReference, ? super ClassNode> ledger)
    {
        ClassNode classNode = classWrapper.classNode;
        boolean isPackagerRelatedClass = this.obfuscator.getPackager().isPackagerClassDecrypter(classNode);

        ClassNode copy = new ClassNode();
        for (int i = 0; i < copy.methods.size(); i++)
        {
            MethodWrapper originalMethodWrapper = classWrapper.methods.get(i);
            // check native
            if (Modifier.isNative(originalMethodWrapper.methodNode.access))
            {
                log.info("Automatically excluded " + classWrapper.originalRef + "#" + originalMethodWrapper.methodNode.name + " because it is native.");
                continue;
            }

            originalMethodWrapper.methodNode = copy.methods.get(i);

                    /*for (AbstractInsnNode insn : methodNode.instructions.toArray()) { // TODO: Fix lambdas + interface
                        if (insn instanceof InvokeDynamicInsnNode) {
                            InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;
                            if (indy.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")) {
                                Handle handle = (Handle) indy.bsmArgs[1];
                                String newName = mappings.get(handle.getOwner() + '.' + handle.getName() + handle.getDesc());
                                if (newName != null) {
                                    indy.name = newName;
                                    indy.bsm = new Handle(handle.getTag(), handle.getOwner(), newName, handle.getDesc(), false);
                                }
                            }
                        }
                    }*/
        }

        classNode.accept(new ClassRemapper(copy, simpleRemapper));

        if (copy.fields != null)
            for (int i = 0; i < copy.fields.size(); i++)
                classWrapper.fields.get(i).fieldNode = copy.fields.get(i);

        if (isPackagerRelatedClass)
            copy = this.obfuscator.getPackager().asPackagerClassDecrypter(copy);

        ledger.remove(classWrapper.originalRef);
        classWrapper.classNode = copy;
        ledger.put(ClassReference.of(classWrapper.classNode), classWrapper.classNode);

        ClassWriter writer = new ClassWriter(0);
        classWrapper.classNode.accept(writer);
        classWrapper.originalClass = writer.toByteArray();

        this.obfuscator.getClasses().remove(classWrapper.originalRef);
        this.obfuscator.getClasses().put(ClassReference.of(classWrapper.classNode), classWrapper.classNode);

        ledger.remove(classWrapper.originalRef);
        ledger.put(ClassReference.of(classWrapper.classNode), classWrapper.classNode);
    }

    private void saveMappingsFile(String file, HashMap<String, String> mappings)
    {
        String[] mappingEntries = mappings.entrySet().stream()
                                          .map(entry -> recogniseMappingEntry(
                                                  mappings,
                                                  entry.getKey(),
                                                  entry.getValue()
                                          ))
                                          .distinct()
                                          .toArray(String[]::new);

        try (FileOutputStream fos = new FileOutputStream(file))
        {
            for (String mappingEntry : mappingEntries)
                fos.write((mappingEntry + '\n').getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private boolean isClassExcluded(ClassWrapper classWrapper)
    {
        String str = classWrapper.classNode.name;

        for (Pattern excludedMethodsPattern : this.excludedClassesPatterns)
            if (excludedMethodsPattern.matcher(str).matches())
            {
                log.info("Class '" + classWrapper.classNode.name + "' was excluded from name obfuscation by regex '" + excludedMethodsPattern.pattern() + "'");
                return true;
            }

        return false;
    }

    private boolean isMethodExcluded(String owner, MethodWrapper methodWrapper)
    {
        String str = owner + '.' + methodWrapper.originalName;

        for (Pattern excludedMethodsPattern : this.excludedMethodsPatterns)
            if (excludedMethodsPattern.matcher(str).matches())
                return true;

        if (NodeUtils.isEntryPoint(methodWrapper.methodNode))
        {
            log.info("Method '" + methodWrapper.methodNode.name + "' was automatically excluded from name obfuscation because it is an entry point.");
            return true;
        }

        return false;
    }

    private boolean isFieldExcluded(String owner, FieldWrapper methodWrapper)
    {
        String str = owner + '.' + methodWrapper.originalName;

        for (Pattern excludedMethodsPattern : this.excludedFieldsPatterns)
            if (excludedMethodsPattern.matcher(str).matches())
                return true;

        return false;
    }

    private boolean canRenameMethodTree(Map<String, String> mappings, Set<ClassTree> visited,
                                        MethodWrapper methodWrapper, ClassReference owner)
    {
        ClassTree tree = this.obfuscator.getTree(owner);

        if (tree == null)
            return false;

        if (NodeUtils.isSpecialMethod(methodWrapper.methodNode, tree))
            return false;

        if (visited.contains(tree))
            return true;

        visited.add(tree);

        if (tree.missingSuperClass || Modifier.isNative(methodWrapper.methodNode.access))
            return false;

        if (mappings.containsKey(owner.getFullQualifiedName() + '.' + methodWrapper.originalName + methodWrapper.originalDescription))
            return true;

        if (!methodWrapper.owner.originalRef.equals(owner) && tree.classWrapper.libraryNode)
            for (MethodNode mn : tree.classWrapper.classNode.methods)
                if (mn.name.equals(methodWrapper.originalName)
                        && mn.desc.equals(methodWrapper.originalDescription))
                    return false;

        for (ClassReference parent : tree.parentClasses)
            if (!(parent == null || canRenameMethodTree(mappings, visited, methodWrapper, parent)))
                return false;

        for (ClassReference sub : tree.subClasses)
            if (!(sub == null || canRenameMethodTree(mappings, visited, methodWrapper, sub)))
                return false;

        return true;
    }

    private void renameMethodTree(Map<String, String> mappings, Set<ClassTree> visited, MethodWrapper MethodWrapper,
                                  ClassReference classRef,
                                  String newName)
    {
        ClassTree tree = this.obfuscator.getTree(classRef);

        if (tree.classWrapper.libraryNode || visited.contains(tree))
            return;

        mappings.put(classRef.getFullQualifiedName() + '.' + MethodWrapper.originalName + MethodWrapper.originalDescription, newName);
        visited.add(tree);
        for (ClassReference parentClass : tree.parentClasses)
            this.renameMethodTree(mappings, visited, MethodWrapper, parentClass, newName);

        for (ClassReference subClass : tree.subClasses)
            this.renameMethodTree(mappings, visited, MethodWrapper, subClass, newName);

    }

    private boolean canRenameFieldTree(Map<String, String> mappings, Set<ClassTree> visited, FieldWrapper fieldWrapper,
                                       ClassReference owner)
    {
        ClassTree tree = this.obfuscator.getTree(owner);

        if (tree == null)
            return false;

        if (visited.contains(tree))
            return true;

        visited.add(tree);

        if (tree.missingSuperClass)
            return false;

        if (mappings.containsKey(owner.getFullQualifiedName() + '.' + fieldWrapper.originalName + '.' + fieldWrapper.originalDescription))
            return true;
        if (!fieldWrapper.owner.originalRef.equals(owner) && tree.classWrapper.libraryNode)
            for (FieldNode fn : tree.classWrapper.classNode.fields)
                if (fieldWrapper.originalName.equals(fn.name) && fieldWrapper.originalDescription.equals(fn.desc))
                    return false;

        for (ClassReference parent : tree.parentClasses)
            if (!(parent == null || canRenameFieldTree(mappings, visited, fieldWrapper, parent)))
                return false;

        for (ClassReference sub : tree.subClasses)
            if (!(sub == null || canRenameFieldTree(mappings, visited, fieldWrapper, sub)))
                return false;

        return true;
    }

    private void renameFieldTree(HashSet<ClassTree> visited, FieldWrapper fieldWrapper, ClassReference owner, String newName,
                                 Map<String, String> mappings)
    {
        ClassTree tree = this.obfuscator.getTree(owner);

        if (tree.classWrapper.libraryNode || visited.contains(tree))
            return;

        mappings.put(owner.getFullQualifiedName() + '.' + fieldWrapper.originalName + '.' + fieldWrapper.originalDescription, newName);
        visited.add(tree);
        for (ClassReference parentClass : tree.parentClasses)
            renameFieldTree(visited, fieldWrapper, parentClass, newName, mappings);
        for (ClassReference subClass : tree.subClasses)
            renameFieldTree(visited, fieldWrapper, subClass, newName, mappings);
    }

    private static boolean hasNativeMethodInClass(ClassNode classNode)
    {
        return classNode.methods.stream().anyMatch(methodNode -> Modifier.isNative(methodNode.access));
    }

    private static String recogniseMappingEntry(HashMap<String, String> mappings, String original, String mapped)
    {
        String[] entryContents = original.split("\\.");
        if (entryContents.length <= 1)
            return "CL: " + mapped + " " + original;

        String className = entryContents[0];
        String memberName = entryContents[1];
        String fieldDesc = entryContents.length > 2 ? entryContents[2]: null;

        String mappedClass = mappings.get(className);

        if (fieldDesc != null)
            return "FD: " + mappedClass + "/" + mapped + " " + className + "/" + memberName + " " + fieldDesc;
        else
        {
            int descStart = memberName.indexOf('(');
            if (descStart != -1)
            {
                String methodDesc = memberName.substring(descStart);
                memberName = memberName.substring(0, descStart);
                return "MD: " + mappedClass + "/" + mapped + " " + methodDesc + " " + className + "/" + memberName + " " + methodDesc;
            }
            else
                return "# MD: " + mappedClass + "/" + memberName + " " + mapped;
        }
    }
}
