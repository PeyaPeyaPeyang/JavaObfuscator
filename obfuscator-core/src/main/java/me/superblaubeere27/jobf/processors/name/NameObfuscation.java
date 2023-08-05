/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package me.superblaubeere27.jobf.processors.name;

import lombok.extern.slf4j.Slf4j;
import me.superblaubeere27.jobf.JObfImpl;
import me.superblaubeere27.jobf.utils.ClassTree;
import me.superblaubeere27.jobf.utils.NameUtils;
import me.superblaubeere27.jobf.utils.Utils;
import me.superblaubeere27.jobf.utils.values.BooleanValue;
import me.superblaubeere27.jobf.utils.values.DeprecationLevel;
import me.superblaubeere27.jobf.utils.values.EnabledValue;
import me.superblaubeere27.jobf.utils.values.FilePathValue;
import me.superblaubeere27.jobf.utils.values.StringValue;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Slf4j(topic = "obfuscator")
public class NameObfuscation implements INameObfuscationProcessor
{
    private static final String PROCESSOR_NAME = "NameObfuscation";
    private static final Random random = new Random();
    private final EnabledValue enabled = new EnabledValue(PROCESSOR_NAME, DeprecationLevel.OK, false);
    private final StringValue excludedClasses = new StringValue(PROCESSOR_NAME, "Excluded classes", null, DeprecationLevel.GOOD, "me.name.Class\nme.name.*\nio.netty.**", 5);
    private final StringValue excludedMethods = new StringValue(PROCESSOR_NAME, "Excluded methods", null, DeprecationLevel.GOOD, "me.name.Class.method\nme.name.Class**\nme.name.Class.*", 5);
    private final StringValue excludedFields = new StringValue(PROCESSOR_NAME, "Excluded fields", null, DeprecationLevel.GOOD, "me.name.Class.field\nme.name.Class.*\nme.name.**", 5);
    private final BooleanValue shouldPackage = new BooleanValue(PROCESSOR_NAME, "Package", DeprecationLevel.OK, false);
    private final StringValue newPackage = new StringValue(PROCESSOR_NAME, "New Packages", null, DeprecationLevel.GOOD, "", 5);
    private final BooleanValue acceptMissingLibraries = new BooleanValue(PROCESSOR_NAME, "Accept Missing Libraries", DeprecationLevel.GOOD, false);
    private final FilePathValue mappingsToSave = new FilePathValue(PROCESSOR_NAME, "Mappings to save", null, DeprecationLevel.GOOD, null);
    private final List<Pattern> excludedClassesPatterns = new ArrayList<>();
    private final List<Pattern> excludedMethodsPatterns = new ArrayList<>();
    private final List<Pattern> excludedFieldsPatterns = new ArrayList<>();
    private List<String> packageNames;

    public void setupPackages()
    {
        if (this.shouldPackage.getObject())
        {
            String[] newPackages = this.newPackage.getObject().split("\n");
            this.packageNames = Arrays.asList(newPackages);
        }
    }

    public String getPackageName()
    {
        if (!this.shouldPackage.getObject())
            return "";

        String retVal;
        if (this.packageNames.size() == 1 && this.packageNames.get(0).equalsIgnoreCase("common"))
            retVal = CommonPackageTrees.getRandomPackage();
        else
            retVal = this.packageNames.get(random.nextInt(this.packageNames.size()));

        if (retVal.startsWith("/"))
            retVal = retVal.substring(1);
        if (!retVal.endsWith("/"))
            retVal = retVal + "/";

        return retVal;

    }

    private void compileExcludePatterns()
    {
        for (String s : this.excludedClasses.getObject().split("\n"))
            this.excludedClassesPatterns.add(compileExcludePattern(s));
        for (String s : this.excludedMethods.getObject().split("\n"))
            this.excludedMethodsPatterns.add(compileExcludePattern(s));
        for (String s : this.excludedFields.getObject().split("\n"))
            this.excludedFieldsPatterns.add(compileExcludePattern(s));
    }

    private static List<ClassWrapper> buildHierarchies(Collection<? extends ClassNode> nodes, boolean ifAcceptMissingLib)
    {
        List<ClassWrapper> classWrappers = new ArrayList<>();

        for (ClassNode value : nodes)
        {
            ClassWrapper cw = new ClassWrapper(value, false, new byte[0]);
            classWrappers.add(cw);

            JObfImpl.INSTANCE.buildHierarchy(cw, null, ifAcceptMissingLib);
        }

        return classWrappers;
    }

    @Override
    public void transformPost(JObfImpl inst, HashMap<String, ClassNode> nodes)
    {
        if (!this.enabled.getObject())
            return;

        try
        {
            HashMap<String, String> mappings = new HashMap<>();

            compileExcludePatterns();

            log.info("Building Hierarchy...");
            List<ClassWrapper> classWrappers = buildHierarchies(nodes.values(), this.acceptMissingLibraries.getObject());
            log.info("Finished building hierarchy");

            long current = System.currentTimeMillis();
            log.info("Generating mappings...");

            NameUtils.setup();
            this.setupPackages();

            processClasses(classWrappers, mappings);

            if (this.mappingsToSave.getObject() != null)
                this.saveMappingsFile(mappings);

            log.info(String.format("... Finished generating mappings (%s)", Utils.formatTime(System.currentTimeMillis() - current)));

            log.info("Applying mappings...");
            current = System.currentTimeMillis();
            writeClasses(mappings, classWrappers);
            log.info(String.format("... Finished applying mappings (%s)", Utils.formatTime(System.currentTimeMillis() - current)));
        }
        finally
        {
            this.excludedClassesPatterns.clear();
            this.excludedMethodsPatterns.clear();
            this.excludedFieldsPatterns.clear();
        }
    }

    private void processClasses(Collection<ClassWrapper> classWrappers, Map<String, String> mappings)
    {
        classWrappers.stream()
                .filter(classWrapper -> !this.isClassExcluded(classWrapper))
                .forEach(classWrapper -> processClass(classWrapper, mappings));
    }

    private void processClass(ClassWrapper clazz, Map<String, String> mappings)
    {
        clazz.classNode.access &= ~Opcodes.ACC_PRIVATE;
        clazz.classNode.access &= ~Opcodes.ACC_PROTECTED;
        clazz.classNode.access |= Opcodes.ACC_PUBLIC;
        // Inner を引っ張り出したときに, static がついてるとバグる
        clazz.classNode.access &= ~Opcodes.ACC_STATIC;

        processFields(clazz, mappings);
        processMethods(clazz, mappings);

        if (hasNativeMethodInClass(clazz.classNode))
        {
            log.info("Renaming class " + clazz.originalName + " is automatically excluded because it has native methods.");
            mappings.put(clazz.originalName, clazz.originalName);
        }
        else
            mappings.put(clazz.originalName, getPackageName() + NameUtils.generateClassName());
    }

    private static boolean hasNativeMethodInClass(ClassNode classNode)
    {
        return classNode.methods.stream().anyMatch(methodNode -> Modifier.isNative(methodNode.access));
    }


    private void processMethods(ClassWrapper classWrapper, Map<String, String> mappings)
    {
        Predicate<MethodWrapper> isExclude = method ->
                !isMethodExcluded(classWrapper.originalName, method)
                || !canRenameMethodTree(mappings, new HashSet<>(), method, classWrapper.originalName);

        classWrapper.methods.stream()
                .filter(isExclude.negate())
                .forEach(methodWrapper -> processMethod(methodWrapper, classWrapper.originalName, mappings));
    }

    private static void processMethod(MethodWrapper methodWrapper, String ownerClassName, Map<String, String> mappings)
    {
        if (Modifier.isPrivate(methodWrapper.methodNode.access) || Modifier.isProtected(methodWrapper.methodNode.access))
        {
            methodWrapper.methodNode.access &= ~Opcodes.ACC_PRIVATE;
            methodWrapper.methodNode.access &= ~Opcodes.ACC_PROTECTED;
            methodWrapper.methodNode.access |= Opcodes.ACC_PUBLIC;
        }

        if (Modifier.isNative(methodWrapper.methodNode.access))
            log.warn("Native method found in class " + ownerClassName + " method " + methodWrapper.methodNode.name + methodWrapper.methodNode.desc);
        else
            renameMethodTree(mappings, new HashSet<>(), methodWrapper, ownerClassName, NameUtils.generateMethodName(ownerClassName, methodWrapper.originalDescription));
    }

    private void processFields(ClassWrapper classWrapper, Map<String, String> mappings)
    {
        Predicate<FieldWrapper> isExclude = field ->
                !isFieldExcluded(classWrapper.originalName, field)
                || !canRenameFieldTree(mappings, new HashSet<>(), field, classWrapper.originalName);

        classWrapper.fields.stream()
                .filter(isExclude.negate())
                .forEach(fieldWrapper -> processField(fieldWrapper, classWrapper.originalName, mappings));
    }

    private static void processField(FieldWrapper field, String ownerName, Map<String, String> mappings)
    {
        if (Modifier.isPrivate(field.fieldNode.access) || Modifier.isProtected(field.fieldNode.access))
        {
            field.fieldNode.access &= ~Opcodes.ACC_PRIVATE;
            field.fieldNode.access &= ~Opcodes.ACC_PROTECTED;
            field.fieldNode.access |= Opcodes.ACC_PUBLIC;
        }

        renameFieldTree(new HashSet<>(), field, ownerName, NameUtils.generateFieldName(ownerName), mappings);
    }

    private static void writeClasses(HashMap<String, String> mappings, List<ClassWrapper> classWrappers)
    {
        Remapper simpleRemapper = new MemberRemapper(mappings);

        for (ClassWrapper classWrapper : classWrappers)
            writeClass(classWrapper, simpleRemapper);
    }
    
    private static void writeClass(ClassWrapper classWrapper, Remapper simpleRemapper)
    {
        ClassNode classNode = classWrapper.classNode;

        ClassNode copy = new ClassNode();
        for (int i = 0; i < copy.methods.size(); i++)
        {
            MethodWrapper originalMethodWrapper = classWrapper.methods.get(i);
            // check native
            if (Modifier.isNative(originalMethodWrapper.methodNode.access))
            {
                log.info("Automatically excluded " + classWrapper.originalName + "#" + originalMethodWrapper.methodNode.name + " because it is native.");
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

        classWrapper.classNode = copy;
        JObfImpl.classes.remove(classWrapper.originalName + ".class");
        JObfImpl.classes.put(classWrapper.classNode.name + ".class", classWrapper.classNode);
        //            JObfImpl.INSTANCE.getClassPath().put();
        //            this.getClasses().put(classWrapper.classNode.name, classWrapper);

        ClassWriter writer = new ClassWriter(0);
        classWrapper.classNode.accept(writer);
        classWrapper.originalClass = writer.toByteArray();

        JObfImpl.INSTANCE.getClassPath().put(classWrapper.classNode.name, classWrapper);
    }

    private void saveMappingsFile(HashMap<String, String> mappings)
    {
        try (FileOutputStream fos = new FileOutputStream(this.mappingsToSave.getObject()))
        {
            for (Map.Entry<String, String> entry: mappings.entrySet())
                fos.write((entry.getKey() + " -> " + entry.getValue() + "\n").getBytes());
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private Pattern compileExcludePattern(String s)
    {
        StringBuilder sb = new StringBuilder();
        // s.replace('.', '/').replace("**", ".*").replace("*", "[^/]*")

        char[] chars = s.toCharArray();

        for (int i = 0; i < chars.length; i++)
        {
            char c = chars[i];

            if (c == '*')
            {
                if (chars.length - 1 != i && chars[i + 1] == '*')
                {
                    sb.append(".*");  // Linux風の Glob に対応
                    i++;
                }
                else
                {
                    sb.append("[^/]*");
                }
            }
            else if (c == '.')
                sb.append('/');  // パッケージ名は . ではなく / で区切る
            else
                sb.append(c);
        }

        return Pattern.compile(sb.toString());
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

    private boolean canRenameMethodTree(Map<String, String> mappings, Set<ClassTree> visited, MethodWrapper methodWrapper, String owner)
    {
        ClassTree tree = JObfImpl.INSTANCE.getTree(owner);

        if (tree == null)
            return false;

        if (visited.contains(tree))
            return true;

        visited.add(tree);

        if (tree.missingSuperClass || Modifier.isNative(methodWrapper.methodNode.access))
            return false;

        if (mappings.containsKey(owner + '.' + methodWrapper.originalName + methodWrapper.originalDescription))
            return true;

        if (!methodWrapper.owner.originalName.equals(owner) && tree.classWrapper.libraryNode)
            for (MethodNode mn : tree.classWrapper.classNode.methods)
                if (mn.name.equals(methodWrapper.originalName)
                        && mn.desc.equals(methodWrapper.originalDescription))
                    return false;

        for (String parent : tree.parentClasses)
            if (!(parent == null || canRenameMethodTree(mappings, visited, methodWrapper, parent)))
                return false;

        for (String sub : tree.subClasses)
            if (!(sub == null || canRenameMethodTree(mappings, visited, methodWrapper, sub)))
                return false;

        return true;
    }

    private static void renameMethodTree(Map<String, String> mappings, Set<ClassTree> visited, MethodWrapper MethodWrapper, String className,
                                  String newName)
    {
        ClassTree tree = JObfImpl.INSTANCE.getTree(className);

        if (!(tree.classWrapper.libraryNode || visited.contains(tree)))
            return;

        mappings.put(className + '.' + MethodWrapper.originalName + MethodWrapper.originalDescription, newName);
        visited.add(tree);
        for (String parentClass : tree.parentClasses)
            renameMethodTree(mappings, visited, MethodWrapper, parentClass, newName);

        for (String subClass : tree.subClasses)
            renameMethodTree(mappings, visited, MethodWrapper, subClass, newName);

    }

    private boolean canRenameFieldTree(Map<String, String> mappings, Set<ClassTree> visited, FieldWrapper fieldWrapper, String owner)
    {
        ClassTree tree = JObfImpl.INSTANCE.getTree(owner);

        if (tree == null)
            return false;

        if (visited.contains(tree))
            return true;

        visited.add(tree);

        if (tree.missingSuperClass)
            return false;

        if (mappings.containsKey(owner + '.' + fieldWrapper.originalName + '.' + fieldWrapper.originalDescription))
            return true;
        if (!fieldWrapper.owner.originalName.equals(owner) && tree.classWrapper.libraryNode)
            for (FieldNode fn : tree.classWrapper.classNode.fields)
                if (fieldWrapper.originalName.equals(fn.name) && fieldWrapper.originalDescription.equals(fn.desc))
                    return false;

        for (String parent : tree.parentClasses)
            if (!(parent == null || canRenameFieldTree(mappings, visited, fieldWrapper, parent)))
                return false;

        for (String sub : tree.subClasses)
            if (!(sub == null || canRenameFieldTree(mappings, visited, fieldWrapper, sub)))
                return false;

        return true;
    }

    private static void renameFieldTree(HashSet<ClassTree> visited, FieldWrapper fieldWrapper, String owner, String newName, Map<String, String> mappings)
    {
        ClassTree tree = JObfImpl.INSTANCE.getTree(owner);

        if (tree.classWrapper.libraryNode || visited.contains(tree))
            return;

        mappings.put(owner + '.' + fieldWrapper.originalName + '.' + fieldWrapper.originalDescription, newName);
        visited.add(tree);
        for (String parentClass : tree.parentClasses)
            renameFieldTree(visited, fieldWrapper, parentClass, newName, mappings);
        for (String subClass : tree.subClasses)
            renameFieldTree(visited, fieldWrapper, subClass, newName, mappings);
    }
}
