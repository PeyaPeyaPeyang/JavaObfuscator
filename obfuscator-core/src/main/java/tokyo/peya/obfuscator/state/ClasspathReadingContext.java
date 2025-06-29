package tokyo.peya.obfuscator.state;

import lombok.Getter;

@Getter
public class ClasspathReadingContext implements StatusContext
{
    private final ObfuscationStatus manager;

    private volatile long totalFilesToRead;
    private volatile long totalFilesLoaded;
    private volatile long totalClassesToLoad;
    private volatile long totalClassesLoaded;
    private volatile String loadingClassName;

    public ClasspathReadingContext(ObfuscationStatus manager)
    {
        this.manager = manager;
    }

    public void reset()
    {
        this.totalFilesToRead = 0;
        this.totalFilesLoaded = 0;
        this.totalClassesToLoad = 0;
        this.totalClassesLoaded = 0;
        this.loadingClassName = null;
        this.manager.onAnythingChange();
    }

    public void setTotalFilesToRead(long totalFilesToRead)
    {
        this.totalFilesToRead = totalFilesToRead;
        this.manager.onAnythingChange();
    }

    public void setTotalFilesLoaded(long totalFilesLoaded)
    {
        this.totalFilesLoaded = totalFilesLoaded;
        this.manager.onAnythingChange();
    }

    public void setTotalClassesToLoad(long totalClassesToLoad)
    {
        this.totalClassesToLoad = totalClassesToLoad;
        this.manager.onAnythingChange();
    }

    public void setTotalClassesLoaded(long totalClassesLoaded)
    {
        this.totalClassesLoaded = totalClassesLoaded;
        this.manager.onAnythingChange();
    }

    public void setLoadingClassName(String loadingClassName)
    {
        this.loadingClassName = loadingClassName;
        this.manager.onAnythingChange();
    }

    public int getProgressPercentage()
    {
        long totalProcessed = this.getTotalClassesLoaded() * this.getTotalFilesLoaded();
        long totalToProcess = this.getTotalClassesToLoad() * this.getTotalFilesToRead();
        if (totalToProcess == 0)
            return 0;

        return (int) ((totalProcessed * 100) / totalToProcess);
    }
}
