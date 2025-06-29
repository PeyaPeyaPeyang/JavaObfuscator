package tokyo.peya.obfuscator.processor.naming.entrypoint;

import org.objectweb.asm.tree.ClassNode;
import tokyo.peya.obfuscator.clazz.ClassReference;

public interface EntrypointDelegate
{
    boolean canProvideMainClass(String entryName);

    ClassReference getEntrypointClassReference(String entryName, byte[] data);
    byte[] renameMainClass(String entryName, ClassReference renamedClassReference, byte[] data);
}
