package me.superblaubeere27.jobf.processors;

import lombok.SneakyThrows;
import me.superblaubeere27.jobf.IClassTransformer;
import me.superblaubeere27.jobf.JarObfuscator;
import me.superblaubeere27.jobf.processors.flowObfuscation.FlowObfuscator;
import me.superblaubeere27.jobf.processors.name.INameObfuscationProcessor;
import me.superblaubeere27.jobf.processors.name.InnerClassRemover;
import me.superblaubeere27.jobf.processors.name.NameObfuscation;
import me.superblaubeere27.jobf.processors.optimizer.Optimizer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class Processors
{
    private static final Class<?>[] PROCESSORS = {
             StaticInitializionTransformer.class,
                HWIDProtection.class,
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
                InnerClassRemover.class,
                CrasherTransformer.class,
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

    public static List<IClassTransformer> createProcessors(JarObfuscator instance)
    {
        List<IClassTransformer> processors = new ArrayList<>();
        processors.add(new StaticInitializionTransformer(instance));

        processors.add(new HWIDProtection(instance));
        processors.add(new Optimizer());
        processors.add(new InlineTransformer(instance));
        processors.add(new InvokeDynamic());

        processors.add(new StringEncryptionTransformer(instance));
        processors.add(new NumberObfuscationTransformer(instance));
        processors.add(new FlowObfuscator(instance));
        processors.add(new HideMembers(instance));
        processors.add(new LineNumberRemover(instance));
        processors.add(new ShuffleMembersTransformer(instance));

        processors.add(new CrasherTransformer(instance));
        processors.add(new ReferenceProxy(instance));

        return processors;
    }

    public static List<INameObfuscationProcessor> createNameProcessors(JarObfuscator instance) {
        List<INameObfuscationProcessor> processors = new ArrayList<>();
        processors.add(new NameObfuscation(instance));
        processors.add(new InnerClassRemover(instance));
        return processors;
    }
}
