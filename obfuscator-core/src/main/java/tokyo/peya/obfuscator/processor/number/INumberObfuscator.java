/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 * Copyright (c) 2025 Peyang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package tokyo.peya.obfuscator.processor.number;

import org.objectweb.asm.tree.InsnList;

/**
 * Obfuscates number.
 */
public interface INumberObfuscator
{
    /**
     * Obfuscates number.
     *
     * @param value Number to obfuscate.
     * @param insns InsnList to add.
     */
    void obfuscate(int value, InsnList insns);

    /**
     * Checks if this obfuscator can apply to this number.
     *
     * @param value Number to check.
     * @return If this obfuscator can apply to this number.
     */
    boolean canApply(int value);

    default InsnList obfuscate(int value)
    {
        InsnList insns = new InsnList();
        this.obfuscate(value, insns);
        return insns;
    }
}
