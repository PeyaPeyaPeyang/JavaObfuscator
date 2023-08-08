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

package tokyo.peya.obfuscator.utils;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.lang.reflect.Modifier;

public class VariableProvider
{
    private int max;
    private int argumentSize;

    private VariableProvider()
    {

    }

    public VariableProvider(MethodNode method)
    {
        this();

        if (!Modifier.isStatic(method.access)) registerExisting(0, Type.getType("Ljava/lang/Object;"));

        for (Type argumentType : Type.getArgumentTypes(method.desc))
        {
            registerExisting(argumentType.getSize() + this.max - 1, argumentType);
        }

        this.argumentSize = this.max;

        for (AbstractInsnNode abstractInsnNode : method.instructions.toArray())
        {
            if (abstractInsnNode instanceof VarInsnNode)
            {
                registerExisting(((VarInsnNode) abstractInsnNode).var, Utils.getType((VarInsnNode) abstractInsnNode));
            }
        }
    }

    private void registerExisting(int var, Type type)
    {
        if (var >= this.max) this.max = var + type.getSize();
    }

    public boolean isUnallocated(int var)
    {
        return var >= this.max;
    }

    public boolean isArgument(int var)
    {
        return var < this.argumentSize;
    }

    public int allocateVar()
    {
        return this.max++;
    }

}
