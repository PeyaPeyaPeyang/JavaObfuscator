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

package tokyo.peya.obfuscator.processors;

import tokyo.peya.obfuscator.annotations.ObfuscationTransformer;
import tokyo.peya.obfuscator.IClassTransformer;
import tokyo.peya.obfuscator.JarObfuscator;
import tokyo.peya.obfuscator.ProcessorCallback;
import tokyo.peya.obfuscator.utils.values.DeprecationLevel;
import tokyo.peya.obfuscator.utils.values.EnabledValue;
import tokyo.peya.obfuscator.utils.values.ValueManager;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Random;

public class HideMembers implements IClassTransformer
{
    private static final String PROCESSOR_NAME = "HideMembers";
    private static final Random random = new Random();
    private static final EnabledValue V_ENABLED = new EnabledValue(PROCESSOR_NAME, DeprecationLevel.GOOD, true);

    static
    {
        ValueManager.registerClass(HideMembers.class);
    }

    private final JarObfuscator inst;

    public HideMembers(JarObfuscator inst)
    {
        this.inst = inst;
    }

    @Override
    public void process(ProcessorCallback callback, ClassNode node)
    {
        if (!V_ENABLED.get()) return;

        if ((node.access & Opcodes.ACC_INTERFACE) == 0)
        {
            for (MethodNode method : node.methods)
            {
//            if ((method.access & Opcodes.ACC_BRIDGE) == 0 && (method.access & Opcodes.ACC_STATIC) == 0 && !method.name.startsWith("<")) {
//                method.access |= Opcodes.ACC_BRIDGE;
//            }
//            if ((method.access & Opcodes.ACC_SYNTHETIC) == 0) {
                if (method.name.startsWith("<"))
                    continue;
                if ((method.access & Opcodes.ACC_NATIVE) == 0)
                {
                    continue;
                }
                method.access = method.access | Opcodes.ACC_BRIDGE;
                method.access = method.access | Opcodes.ACC_SYNTHETIC;
//            }
            }
        }
        for (FieldNode field : node.fields)
        {
//            if ((field.access & Opcodes.ACC_FINAL) == 0)
            field.access = field.access | Opcodes.ACC_SYNTHETIC;
        }
//        if ((node.access & Opcodes.ACC_FINAL) == 0) {
//            node.access = node.access | Opcodes.ACC_SYNTHETIC;
//        }
    }

    @Override
    public ObfuscationTransformer getType()
    {
        return ObfuscationTransformer.HIDE_MEMBERS;
    }

}
