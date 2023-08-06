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

package tokyo.peya.obfuscator.processor.naming;

import tokyo.peya.obfuscator.IClassTransformer;
import tokyo.peya.obfuscator.JarObfuscator;
import tokyo.peya.obfuscator.ProcessorCallback;
import tokyo.peya.obfuscator.annotations.ObfuscationTransformer;
import tokyo.peya.obfuscator.utils.NameUtils;
import tokyo.peya.obfuscator.configuration.values.BooleanValue;
import tokyo.peya.obfuscator.configuration.DeprecationLevel;
import tokyo.peya.obfuscator.configuration.values.EnabledValue;
import tokyo.peya.obfuscator.configuration.ValueManager;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class InnerClassRemover implements INameObfuscationProcessor, IClassTransformer
{
    private static final String PROCESSOR_NAME = "InnerClassRemover";
    private static final Pattern innerClasses = Pattern.compile(".*[A-Za-z0-9]+\\$[0-9]+");
    private static final EnabledValue V_ENABLED = new EnabledValue(PROCESSOR_NAME, DeprecationLevel.AVAILABLE, true);
    private static final BooleanValue V_REMAP = new BooleanValue(PROCESSOR_NAME, "Remap", DeprecationLevel.SOME_DEPRECATION, false);
    private static final BooleanValue V_REMOVE_METADATA = new BooleanValue(PROCESSOR_NAME, "Remove Metadata", DeprecationLevel.AVAILABLE, true);

    static
    {
        ValueManager.registerClass(InnerClassRemover.class);
    }

    private final JarObfuscator obfuscator;

    public InnerClassRemover(JarObfuscator obfuscator)
    {
        this.obfuscator = obfuscator;
    }

    private static boolean isInnerClass(String name)
    {
        return innerClasses.matcher(name).matches();
    }

    private static void normalizeModifiers(ClassNode classNode)
    {
        if (Modifier.isPrivate(classNode.access) || Modifier.isProtected(classNode.access))
        {
            classNode.access &= ~Opcodes.ACC_PRIVATE;
            classNode.access &= ~Opcodes.ACC_PROTECTED;
            classNode.access |= Opcodes.ACC_PUBLIC;
        }

        if (Modifier.isStatic(classNode.access))
            classNode.access &= ~Opcodes.ACC_STATIC;  // Inner は static にできない
    }

    private static boolean generateAndRegisterRandomName(ClassNode classNode, CustomRemapper remapper)
    {
        if (!isInnerClass(classNode.name))
            return false;

        String newName;

        if (classNode.name.contains("/"))
        {
            String packageName = classNode.name.substring(0, classNode.name.lastIndexOf('/'));
            newName = packageName + "/" + NameUtils.generateClassName(packageName);
        }
        else newName = NameUtils.generateClassName();

        String mappedName;
        do
        {
            mappedName = newName;  // コンパイラによる最適化を防ぐために、mappedNameを使う
        }
        while (!remapper.map(classNode.name, mappedName));  // 他スレッドの割り込みを待つ。

        return true;
    }

    @Override
    public void transformPost(JarObfuscator inst, HashMap<String, ClassNode> nodes)
    {
        if (!(V_ENABLED.get() && V_REMAP.get()))
            return;

        final List<ClassNode> classNodes = new ArrayList<>(this.obfuscator.getClasses().values());

        final Map<String, ClassNode> updatedClasses = new HashMap<>();
        final CustomRemapper remapper = new CustomRemapper();

        for (ClassNode classNode : classNodes)
            generateAndRegisterRandomName(classNode, remapper);

        for (final ClassNode classNode : classNodes)
        {
            this.obfuscator.getClasses().remove(classNode.name + ".class");

            ClassNode newNode = new ClassNode();
            ClassRemapper classRemapper = new ClassRemapper(newNode, remapper);
            classNode.accept(classRemapper);

            normalizeModifiers(newNode);

            updatedClasses.put(newNode.name + ".class", newNode);
        }

        this.obfuscator.getClasses().putAll(updatedClasses);
    }

    @Override
    public void process(ProcessorCallback callback, ClassNode node)
    {
        if (!this.V_ENABLED.get() || !this.V_REMOVE_METADATA.get()) return;

        node.outerClass = null;
        node.innerClasses.clear();

        node.outerMethod = null;
        node.outerMethodDesc = null;
    }

    @Override
    public ObfuscationTransformer getType()
    {
        return ObfuscationTransformer.INNER_CLASS_REMOVER;
    }
}
