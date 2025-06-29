package tokyo.peya.obfuscator.processor.naming.entrypoint;

import org.objectweb.asm.tree.ClassNode;
import tokyo.peya.obfuscator.clazz.ClassReference;

import java.util.zip.ZipEntry;

public abstract class AbstractPlainTextDelegate implements EntrypointDelegate
{
    protected abstract ClassReference getMainClassName(String entryName, String text);
    protected abstract String renameMainClass(String entryName, ClassReference renamedClassReference, String text);

    @Override
    public ClassReference getEntrypointClassReference(String entryName, byte[] data)
    {
        String text = new String(data);
        return getMainClassName(entryName, text);
    }

    @Override
    public byte[] renameMainClass(String entryName, ClassReference renamedClassReference, byte[] data)
    {
        String text = new String(data);
        String renamedText = renameMainClass(entryName, renamedClassReference, text);
        return renamedText.getBytes();
    }
}
