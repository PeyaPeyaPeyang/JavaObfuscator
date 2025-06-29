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

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.ClassNode;
import tokyo.peya.obfuscator.IClassTransformer;
import tokyo.peya.obfuscator.Obfuscator;
import tokyo.peya.obfuscator.ProcessorCallback;
import tokyo.peya.obfuscator.annotations.ObfuscationTransformer;
import tokyo.peya.obfuscator.clazz.ClassReference;
import tokyo.peya.obfuscator.configuration.DeprecationLevel;
import tokyo.peya.obfuscator.configuration.ValueManager;
import tokyo.peya.obfuscator.configuration.values.BooleanValue;
import tokyo.peya.obfuscator.configuration.values.EnabledValue;
import tokyo.peya.obfuscator.state.NameProcessingContext;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class InnerClassRemover implements INameObfuscationProcessor, IClassTransformer
{
    private static final String PROCESSOR_NAME = "inner_class_remover";
    private static final Pattern INNER_CLASSES = Pattern.compile(".*[A-Za-z0-9_]+\\$[A-Za-z0-9_]+");
    private static final EnabledValue V_ENABLED = new EnabledValue(
            PROCESSOR_NAME,
            "ui.transformers.inner_class.description",
            DeprecationLevel.AVAILABLE,
            true
    );
    private static final BooleanValue V_REMAP = new BooleanValue(
            PROCESSOR_NAME,
            "relocate_classes",
            "ui.transformers.inner_class.relocate_classes",
            DeprecationLevel.AVAILABLE,
            false
    );
    private static final BooleanValue V_REMOVE_METADATA = new BooleanValue(
            PROCESSOR_NAME,
            "remove_metadata",
            "ui.transformers.inner_class.erase_metadata",
            DeprecationLevel.AVAILABLE,
            true
    );
    private final Obfuscator obfuscator;

    static
    {
        ValueManager.registerOwner(PROCESSOR_NAME, "ui.transformers.inner_class");
        ValueManager.registerClass(InnerClassRemover.class);
    }

    public InnerClassRemover(Obfuscator obfuscator)
    {
        this.obfuscator = obfuscator;
    }

    private boolean generateAndRegisterRandomName(ClassNode classNode, CustomRemapper remapper)
    {
        if (!isInnerClass(classNode.name))
            return false;

        String newName;

        if (classNode.name.contains("/"))
        {
            String packageName = classNode.name.substring(0, classNode.name.lastIndexOf('/'));
            newName = packageName + "/" + this.obfuscator.getNameProvider().generateClassName(packageName);
        }
        else newName = this.obfuscator.getNameProvider().generateClassName();

        String mappedName;
        do
        {
            mappedName = newName;  // コンパイラによる最適化を防ぐために、mappedNameを使う
        }
        while (!remapper.map(classNode.name, mappedName));  // 他スレッドの割り込みを待つ。

        return true;
    }

    @Override
    public void transformPost(Obfuscator inst, NameProcessingContext ctxt, Map<ClassReference, ClassNode> nodes)
    {
        if (!(V_ENABLED.get() && V_REMAP.get())) // remap のみ
            return;

        final List<ClassNode> classNodes = new ArrayList<>(nodes.values());

        final Map<ClassReference, ClassNode> updatedClasses = new HashMap<>();
        final CustomRemapper remapper = new CustomRemapper(this.obfuscator);

        for (ClassNode classNode : classNodes)
            this.generateAndRegisterRandomName(classNode, remapper);

        for (final ClassNode classNode : classNodes)
        {
            ctxt.setProcessingName(classNode.name);

            nodes.remove(ClassReference.of(classNode));

            ClassNode newNode = new ClassNode();
            ClassRemapper classRemapper = new ClassRemapper(newNode, remapper);
            classNode.accept(classRemapper);

            normalizeModifiers(newNode);

            updatedClasses.put(ClassReference.of(newNode), newNode);
            ctxt.incrementTotalNamesProcessed();
        }

        nodes.putAll(updatedClasses);
    }

    @Override
    public void process(ProcessorCallback callback, ClassNode node)
    {
        if (!(V_ENABLED.get() && V_REMOVE_METADATA.get())) return;  // メタデータのみ

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

    private static boolean isInnerClass(String name)
    {
        return INNER_CLASSES.matcher(name).matches();
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
}
