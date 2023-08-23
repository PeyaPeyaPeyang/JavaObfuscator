package tokyo.peya.obfuscator.processor;

import lombok.SneakyThrows;
import tokyo.peya.obfuscator.IClassTransformer;
import tokyo.peya.obfuscator.Obfuscator;
import tokyo.peya.obfuscator.processor.flows.FlowObfuscator;
import tokyo.peya.obfuscator.processor.naming.INameObfuscationProcessor;
import tokyo.peya.obfuscator.processor.naming.InnerClassRemover;
import tokyo.peya.obfuscator.processor.naming.NameObfuscation;
import tokyo.peya.obfuscator.processor.number.NumberObfuscationTransformer;
import tokyo.peya.obfuscator.processor.optimizer.Optimizer;
import tokyo.peya.obfuscator.processor.strings.HideStringsTransformer;
import tokyo.peya.obfuscator.processor.strings.StringEncryptionTransformer;

import java.util.ArrayList;
import java.util.List;

public class Processors
{
    private static final Class<?>[] PROCESSORS = {
            StaticInitializionTransformer.class,
            Optimizer.class,
            InlineTransformer.class,
            InvokeDynamic.class,
            StringEncryptionTransformer.class,
            NumberObfuscationTransformer.class,
            FlowObfuscator.class,
            HideMembers.class,
            LineNumberRemover.class,
            ShuffleMembersTransformer.class,
            NameObfuscation.class,
            HideStringsTransformer.class,
            InnerClassRemover.class,
            DecompilerCrasher.class,
            ReferenceProxy.class
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
        processors.add(new StaticInitializionTransformer(instance));

        processors.add(new Optimizer());
        processors.add(new InlineTransformer(instance));
        processors.add(new InvokeDynamic());

        processors.add(new StringEncryptionTransformer());

        processors.add(new NumberObfuscationTransformer());
        processors.add(new FlowObfuscator(instance));
        processors.add(new HideMembers(instance));
        processors.add(new LineNumberRemover());
        processors.add(new ShuffleMembersTransformer(instance));
        processors.add(new HideStringsTransformer());  // StringEncryptionTransformer, LineNumberRemover のあと

        processors.add(new DecompilerCrasher());
        processors.add(new ReferenceProxy(instance));

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
