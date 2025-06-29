package tokyo.peya.obfuscator.state;

import lombok.Getter;

@Getter
public class NameProcessingContext implements StatusContext
{
    private final ObfuscationStatus manager;

    private volatile long totalNamesToProcess;
    private volatile long totalNamesProcessed;
    private volatile String processingName;

    public NameProcessingContext(ObfuscationStatus manager)
    {
        this.manager = manager;
    }

    public void setTotalNamesToProcess(long totalNamesToProcess)
    {
        this.totalNamesToProcess = totalNamesToProcess;
        this.manager.onAnythingChange();
    }

    public void setTotalNamesProcessed(long totalNamesProcessed)
    {
        this.totalNamesProcessed = totalNamesProcessed;
        this.manager.onAnythingChange();
    }

    public synchronized void incrementTotalNamesProcessed()
    {
        this.totalNamesProcessed = Math.max(0, this.totalNamesProcessed + 1);
        this.manager.onAnythingChange();
    }

    public void setProcessingName(String processingName)
    {
        this.processingName = processingName;
        this.manager.onAnythingChange();
    }
}
