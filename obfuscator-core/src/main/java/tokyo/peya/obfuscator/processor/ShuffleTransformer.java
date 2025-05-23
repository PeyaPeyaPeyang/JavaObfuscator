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

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import tokyo.peya.obfuscator.IClassTransformer;
import tokyo.peya.obfuscator.Obfuscator;
import tokyo.peya.obfuscator.ProcessorCallback;
import tokyo.peya.obfuscator.annotations.ObfuscationTransformer;
import tokyo.peya.obfuscator.configuration.DeprecationLevel;
import tokyo.peya.obfuscator.configuration.ValueManager;
import tokyo.peya.obfuscator.configuration.values.BooleanValue;
import tokyo.peya.obfuscator.configuration.values.EnabledValue;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class ShuffleTransformer implements IClassTransformer
{
    private static final String PROCESSOR_NAME = "Shuffler";
    private static final Random RANDOM = new Random();

    private static final EnabledValue V_ENABLED = new EnabledValue(PROCESSOR_NAME, "ui.transformers.shuffler.description", DeprecationLevel.AVAILABLE, true);
    private static final BooleanValue V_SHUFFLE_CLASS_STRUCTURE = new BooleanValue(
            PROCESSOR_NAME,
            "Shuffle class structure",
            "ui.transformers.shuffler.shuffle_class_structure",
            DeprecationLevel.AVAILABLE,
            true
    );
    private static final BooleanValue V_SHUFFLE_METHOD_STRUCTURE = new BooleanValue(
            PROCESSOR_NAME,
            "Shuffle method structure",
            "ui.transformers.shuffler.shuffle_method_structure",
            DeprecationLevel.AVAILABLE,
            true
    );
    private static final BooleanValue V_SHUFFLE_FIELD_STRUCTURE = new BooleanValue(
            PROCESSOR_NAME,
            "Shuffle field structure",
            "ui.transformers.shuffler.shuffle_field_structure",
            DeprecationLevel.AVAILABLE,
            true
    );
    private static final BooleanValue V_SHUFFLE_ANNOTATIONS = new BooleanValue(
            PROCESSOR_NAME,
            "Shuffle annotations order in all places",
            "ui.transformers.shuffler.shuffle_annotations",
            DeprecationLevel.AVAILABLE,
            true
    );

    private static final BooleanValue V_SHUFFLE_DEBUG_CLASS_NAMES = new BooleanValue(
            PROCESSOR_NAME,
            "Shuffle source file names among other classes",
            "ui.transformers.shuffler.shuffle_debug_class_names",
            DeprecationLevel.AVAILABLE,
            true
    );

    static
    {
        ValueManager.registerOwner(PROCESSOR_NAME, "ui.transformers.shuffler");
        ValueManager.registerClass(ShuffleTransformer.class);
    }

    private final Obfuscator instance;

    public ShuffleTransformer(Obfuscator instance)
    {
        this.instance = instance;
    }

    private static void shuffleIfPresent(List<?> collection)
    {
        if (collection != null)
            Collections.shuffle(collection, RANDOM);
    }

    private static void shuffleIfPresent(List<?> collection, BooleanValue value)
    {
        if (collection != null && value.get())
            Collections.shuffle(collection, RANDOM);
    }

    private static void processField(FieldNode field)
    {
        if (V_SHUFFLE_ANNOTATIONS.get())
        {
            shuffleIfPresent(field.invisibleAnnotations);
            shuffleIfPresent(field.invisibleTypeAnnotations);
            shuffleIfPresent(field.visibleAnnotations);
            shuffleIfPresent(field.visibleTypeAnnotations);
        }
    }

    private static void processMethod(MethodNode method)
    {
        shuffleIfPresent(method.tryCatchBlocks);
        shuffleIfPresent(method.exceptions);
        shuffleIfPresent(method.localVariables);
        shuffleIfPresent(method.parameters);

        if (V_SHUFFLE_ANNOTATIONS.get())
        {
            shuffleIfPresent(method.invisibleAnnotations);
            shuffleIfPresent(method.invisibleLocalVariableAnnotations);
            shuffleIfPresent(method.invisibleTypeAnnotations);
            shuffleIfPresent(method.visibleAnnotations);
            shuffleIfPresent(method.visibleLocalVariableAnnotations);
            shuffleIfPresent(method.visibleTypeAnnotations);
        }
    }

    private static void processClassStructure(ClassNode node)
    {
        shuffleIfPresent(node.methods);
        shuffleIfPresent(node.fields);
        shuffleIfPresent(node.innerClasses);
        shuffleIfPresent(node.interfaces);

        if (V_SHUFFLE_ANNOTATIONS.get())
        {
            shuffleIfPresent(node.invisibleAnnotations);
            shuffleIfPresent(node.invisibleTypeAnnotations);
            shuffleIfPresent(node.visibleAnnotations);
            shuffleIfPresent(node.visibleTypeAnnotations);
        }
    }

    @Override
    public void process(ProcessorCallback callback, ClassNode node)
    {
        if (!V_ENABLED.get())
            return;

        boolean isEnum = (node.access & Opcodes.ACC_ENUM) != 0;
        if (isEnum)
            return;

        if (V_SHUFFLE_CLASS_STRUCTURE.get())
            processClassStructure(node);

        if (V_SHUFFLE_METHOD_STRUCTURE.get())
            for (MethodNode o : node.methods)
                processMethod(o);

        if (V_SHUFFLE_FIELD_STRUCTURE.get())
            for (FieldNode o : node.fields)
                processField(o);

        if (V_SHUFFLE_DEBUG_CLASS_NAMES.get())
            stealDebugNameFromAnotherClass(node);
    }

    private void stealDebugNameFromAnotherClass(ClassNode myNode)
    {
        Collection<ClassNode> classes = this.instance.getClasses().values();

        ClassNode node = classes.stream()
                .skip(RANDOM.nextInt(classes.size()))
                .findFirst()
                .orElse(null);
        assert node != null;

        String theirSource = node.sourceFile;
        String theirSourceDebug = node.sourceDebug;

        node.sourceFile = myNode.sourceFile;
        node.sourceDebug = myNode.sourceDebug;

        myNode.sourceFile = theirSource;
        myNode.sourceDebug = theirSourceDebug;
    }

    @Override
    public ObfuscationTransformer getType()
    {
        return ObfuscationTransformer.SHUFFLE_MEMBERS;
    }

}
