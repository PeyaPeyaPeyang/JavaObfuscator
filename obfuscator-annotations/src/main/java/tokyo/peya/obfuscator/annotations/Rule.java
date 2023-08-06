/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package tokyo.peya.obfuscator.annotations;

public @interface Rule
{
    Action value();

    ObfuscationTransformer[] processors() default {ObfuscationTransformer.FLOW_OBFUSCATION,
            ObfuscationTransformer.LINE_NUMBER_REMOVER,
            ObfuscationTransformer.NUMBER_OBFUSCATION,
            ObfuscationTransformer.STRING_ENCRYPTION,
            ObfuscationTransformer.HWID_PROTECTION,
            ObfuscationTransformer.PEEPHOLE_OPTIMIZER,
            ObfuscationTransformer.CRASHER,
            ObfuscationTransformer.INVOKE_DYNAMIC,
            ObfuscationTransformer.REFERENCE_PROXY,
            ObfuscationTransformer.SHUFFLE_MEMBERS,
            ObfuscationTransformer.INNER_CLASS_REMOVER,
            ObfuscationTransformer.NAME_OBFUSCATION,
            ObfuscationTransformer.HIDE_MEMBERS,
            ObfuscationTransformer.INLINING};

    enum Action
    {
        ALLOW,
        DISALLOW
    }
}
