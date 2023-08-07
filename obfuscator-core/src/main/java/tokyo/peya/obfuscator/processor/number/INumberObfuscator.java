package tokyo.peya.obfuscator.processor.number;

import org.objectweb.asm.tree.InsnList;

/**
 * Obfuscates number.
 */
public interface INumberObfuscator
{
    /**
     * Obfuscates number.
     * @param value Number to obfuscate.
     * @param insns InsnList to add.
     */
    void obfuscate(int value, InsnList insns);

    /**
     * Checks if this obfuscator can apply to this number.
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
