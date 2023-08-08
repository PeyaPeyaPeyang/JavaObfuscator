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
import tokyo.peya.obfuscator.configuration.values.EnabledValue;

import java.util.Collections;
import java.util.Random;

public class ShuffleMembersTransformer implements IClassTransformer
{
    private static final String PROCESSOR_NAME = "ShuffleMembers";
    private static final Random random = new Random();

    static
    {

    }

    private final Obfuscator inst;
    private final EnabledValue enabled = new EnabledValue(PROCESSOR_NAME, DeprecationLevel.AVAILABLE, true);

    public ShuffleMembersTransformer(Obfuscator inst)
    {
        this.inst = inst;
    }

    @Override
    public void process(ProcessorCallback callback, ClassNode node)
    {
        if (!this.enabled.get()) return;

        if ((node.access & Opcodes.ACC_ENUM) != 0)
        {
            return;
        }

        Collections.shuffle(node.methods, random);
        Collections.shuffle(node.fields, random);
        Collections.shuffle(node.innerClasses, random);
        Collections.shuffle(node.interfaces, random);

        if (node.invisibleAnnotations != null) Collections.shuffle(node.invisibleAnnotations, random);
        if (node.visibleAnnotations != null) Collections.shuffle(node.visibleAnnotations, random);
        if (node.invisibleTypeAnnotations != null) Collections.shuffle(node.invisibleTypeAnnotations, random);

        for (Object o : node.methods.toArray())
        {
            if (o instanceof MethodNode)
            {
                MethodNode method = (MethodNode) o;
                if (method.invisibleAnnotations != null) Collections.shuffle(method.invisibleAnnotations, random);
                if (method.invisibleLocalVariableAnnotations != null)
                    Collections.shuffle(method.invisibleLocalVariableAnnotations, random);
                if (method.invisibleTypeAnnotations != null)
                    Collections.shuffle(method.invisibleTypeAnnotations, random);
                if (method.visibleAnnotations != null) Collections.shuffle(method.visibleAnnotations, random);
                if (method.visibleLocalVariableAnnotations != null)
                    Collections.shuffle(method.visibleLocalVariableAnnotations, random);
                if (method.visibleTypeAnnotations != null) Collections.shuffle(method.visibleTypeAnnotations, random);

                Collections.shuffle(method.exceptions, random);
                if (method.localVariables != null)
                {
                    Collections.shuffle(method.localVariables, random);
                }
                if (method.parameters != null) Collections.shuffle(method.parameters, random);
            }
        }
        for (Object o : node.methods.toArray())
        {
            if (o instanceof FieldNode)
            {
                FieldNode method = (FieldNode) o;
                if (method.invisibleAnnotations != null) Collections.shuffle(method.invisibleAnnotations, random);
                if (method.invisibleTypeAnnotations != null)
                    Collections.shuffle(method.invisibleTypeAnnotations, random);
                if (method.visibleAnnotations != null) Collections.shuffle(method.visibleAnnotations, random);
                if (method.visibleTypeAnnotations != null) Collections.shuffle(method.visibleTypeAnnotations, random);
            }
        }
    }

    @Override
    public ObfuscationTransformer getType()
    {
        return ObfuscationTransformer.SHUFFLE_MEMBERS;
    }

}
