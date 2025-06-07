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

package tokyo.peya.obfuscator.clazz;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import tokyo.peya.obfuscator.JavaObfuscator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ObfuscatorClassLoader extends ClassLoader
{
    public static ObfuscatorClassLoader INSTANCE = new ObfuscatorClassLoader();

    private Map<ClassReference, ClassNode> tempClasses;

    public ObfuscatorClassLoader()
    {
        super(ObfuscatorClassLoader.class.getClassLoader());

        this.tempClasses = new ConcurrentHashMap<>();
    }

    public static void addTempClass(ClassReference ref, ClassNode classNode)
    {
        if (INSTANCE.tempClasses.containsKey(ref))
            return;

        INSTANCE.tempClasses.put(ref, classNode);
    }

    public static void removeTempClass(ClassReference ref)
    {
        INSTANCE.tempClasses.remove(ref);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException
    {
        ClassReference ref = ClassReference.of(name);
        if (this.tempClasses.containsKey(ref))
        {
            ClassNode classNode = this.tempClasses.get(ref);
            if (classNode == null)
                throw new ClassNotFoundException(name);

            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(classWriter);

            byte[] classBytes = classWriter.toByteArray();
            try
            {
                return defineClass(name, classBytes, 0, classBytes.length);
            }
            catch (ClassFormatError classFormatError)
            {
                classFormatError.printStackTrace();
                try
                {
                    Files.write(new File("A:/invalid.class").toPath(), classBytes);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }

        if (JavaObfuscator.getCurrentSession().getClassPath().containsKey(ref))
        {
            ClassWrapper classWrapper = JavaObfuscator.getCurrentSession().getClassPath().get(ref);

            if (classWrapper == null || classWrapper.originalClass == null)
                throw new ClassNotFoundException(name);

            try
            {
                return defineClass(name, classWrapper.originalClass, 0, classWrapper.originalClass.length);
            }
            catch (ClassFormatError classFormatError)
            {
                classFormatError.printStackTrace();
                try
                {
                    Files.write(new File("A:/invalid.class").toPath(), classWrapper.originalClass);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }

        return super.findClass(name);
    }
}
