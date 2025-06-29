package tokyo.peya.obfuscator.state;

import lombok.Getter;

@Getter
public class ResourcesWritingContext implements StatusContext
{
    private final ObfuscationStatus manager;

    private volatile long totalResourcesToWrite;
    private volatile long totalResourcesWritten;
    private volatile String writingResourceName;

    public ResourcesWritingContext(ObfuscationStatus manager)
    {
        this.manager = manager;
    }

    public void setTotalResourcesToWrite(long totalResourcesToWrite)
    {
        this.totalResourcesToWrite = totalResourcesToWrite;
        this.manager.onAnythingChange();
    }

    public void setTotalResourcesWritten(long totalResourcesWritten)
    {
        this.totalResourcesWritten = totalResourcesWritten;
        this.manager.onAnythingChange();
    }

    public void setWritingResourceName(String writingResourceName)
    {
        this.writingResourceName = writingResourceName;
        this.manager.onAnythingChange();
    }
}
