package tokyo.peya.obfuscator.state;

import lombok.Getter;

@Getter
public class ProcessingContext implements StatusContext
{
    private final ObfuscationStatus manager;
    private volatile long totalClassesToProcess;
    private volatile long totalClassesProcessed;
    private volatile String processingClassName;

    public ProcessingContext(ObfuscationStatus manager)
    {
        this.manager = manager;
    }

    public void setTotalClassesToProcess(long totalClassesToProcess)
    {
        this.totalClassesToProcess = totalClassesToProcess;
        this.manager.onAnythingChange();
    }

    public void setTotalClassesProcessed(long totalClassesProcessed)
    {
        this.totalClassesProcessed = totalClassesProcessed;
        this.manager.onAnythingChange();
    }

    public void setProcessingClassName(String processingClassName)
    {
        this.processingClassName = processingClassName;
        this.manager.onAnythingChange();
    }
}
