package tokyo.peya.obfuscator.state;

import lombok.Getter;

@Getter
public class EncodingContext implements StatusContext
{
    private final ObfuscationStatus manager;

    private volatile long totalClassesToEncode;
    private volatile long totalClassesEncoded;
    private volatile String encodingClassName;

    public EncodingContext(ObfuscationStatus manager)
    {
        this.manager = manager;
    }

    public void setTotalClassesToEncode(long totalClassesToEncode)
    {
        this.totalClassesToEncode = totalClassesToEncode;
        this.manager.onAnythingChange();
    }

    public void setTotalClassesEncoded(long totalClassesEncoded)
    {
        this.totalClassesEncoded = totalClassesEncoded;
        this.manager.onAnythingChange();
    }

    public void setEncodingClassName(String encodingClassName)
    {
        this.encodingClassName = encodingClassName;
        this.manager.onAnythingChange();
    }
}
