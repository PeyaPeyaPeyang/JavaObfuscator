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

package tokyo.peya.obfuscator.clazz;

import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;

/**
 * Wrapper for ClassNodes.
 *
 * @author ItzSomebody
 */
public class ClassWrapper
{
    /**
     * Attached class node.
     */
    public ClassNode classNode;

    /**
     * Original name of ClassNode. Really useful when class got renamed.
     */
    public ClassReference originalRef;

    /**
     * Quick way of figuring out if this is represents library class or not.
     */
    public boolean libraryNode;

    /**
     * Required if you wanna load it at runtime. (For COMPUTE_FRAMES)
     */
    public byte[] originalClass;

    /**
     * Methods.
     */
    public ArrayList<MethodWrapper> methods = new ArrayList<>();

    /**
     * Fields.
     */
    public ArrayList<FieldWrapper> fields = new ArrayList<>();

    /**
     * Creates a ClassWrapper object.
     *
     * @param classNode     the attached {@link ClassNode}.
     * @param libraryNode   is this a library class?
     * @param originalClass Original bytes of the class
     */
    public ClassWrapper(ClassNode classNode, boolean libraryNode, byte[] originalClass)
    {
        this.classNode = classNode;
        this.originalRef = ClassReference.of(classNode);
        this.libraryNode = libraryNode;
        this.originalClass = originalClass;

        ClassWrapper instance = this;
        classNode.methods.forEach(methodNode -> this.methods.add(new MethodWrapper(
                methodNode, instance, methodNode.name,
                methodNode.desc
        )));
        if (classNode.fields != null)
        {
            classNode.fields.forEach(fieldNode -> this.fields.add(new FieldWrapper(
                    fieldNode, instance, fieldNode.name,
                    fieldNode.desc
            )));
        }
    }
}
