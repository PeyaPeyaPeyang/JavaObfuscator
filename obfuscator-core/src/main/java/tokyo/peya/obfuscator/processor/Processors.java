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

package tokyo.peya.obfuscator.processor;

import lombok.SneakyThrows;
import tokyo.peya.obfuscator.IClassTransformer;
import tokyo.peya.obfuscator.Obfuscator;
import tokyo.peya.obfuscator.processor.flows.FlowObfuscator;
import tokyo.peya.obfuscator.processor.naming.INameObfuscationProcessor;
import tokyo.peya.obfuscator.processor.naming.InnerClassRemover;
import tokyo.peya.obfuscator.processor.naming.NameObfuscation;
import tokyo.peya.obfuscator.processor.number.NumberObfuscationTransformer;
import tokyo.peya.obfuscator.processor.optimiser.Optimiser;
import tokyo.peya.obfuscator.processor.strings.HideStringsTransformer;
import tokyo.peya.obfuscator.processor.strings.StringEncryptionTransformer;

import java.util.ArrayList;
import java.util.List;

public class Processors
{
    private static final Class<?>[] PROCESSORS = {
            StaticInitialisationTransformer.class,
            Optimiser.class,
            // InlineTransformer.class,
            InvokeDynamic.class,
            StringEncryptionTransformer.class,
            NumberObfuscationTransformer.class,
            FlowObfuscator.class,
            HideMembers.class,
            LineNumberRemover.class,
            ShuffleTransformer.class,
            NameObfuscation.class,
            HideStringsTransformer.class,
            InnerClassRemover.class,
            DecompilerCrasher.class,
            Packager.class,
            // ReferenceProxy.class
    };

    public static Class<?>[] getProcessors()
    {
        return Processors.PROCESSORS;
    }

    @SneakyThrows
    public static void loadProcessors()
    {
        for (Class<?> clazz : Processors.PROCESSORS)
        {
            Class.forName(clazz.getName());
        }
    }

    public static List<IClassTransformer> createProcessors(Obfuscator instance)
    {
        List<IClassTransformer> processors = new ArrayList<>();
        processors.add(new StaticInitialisationTransformer(instance));

        processors.add(new Optimiser());
        processors.add(new InlineTransformer(instance));


        processors.add(new FlowObfuscator(instance));
        processors.add(new HideMembers(instance));
        processors.add(new ShuffleTransformer(instance));
        processors.add(new StringEncryptionTransformer(instance));
        processors.add(new HideStringsTransformer(instance));  // StringEncryptionTransformer, LineNumberRemover, ShuffleTransformer のあと
        processors.add(new NumberObfuscationTransformer(instance));  // HideStringsTransformer のあと

        processors.add(new LineNumberRemover(instance));
        processors.add(new DecompilerCrasher());
        //processors.add(new ReferenceProxy(instance));

        return processors;
    }

    public static List<INameObfuscationProcessor> createNameProcessors(Obfuscator instance)
    {
        List<INameObfuscationProcessor> processors = new ArrayList<>();
        processors.add(new NameObfuscation(instance));
        processors.add(new InnerClassRemover(instance));
        return processors;
    }
}
