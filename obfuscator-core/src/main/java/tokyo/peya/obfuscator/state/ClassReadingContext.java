package tokyo.peya.obfuscator.state;

import lombok.Getter;

@Getter
public class ClassReadingContext implements StatusContext
{
    private final ObfuscationStatus manager;

    private volatile long totalClassesToRead;
    private volatile long totalClassesRead;
    private volatile String readingClassName;

    public ClassReadingContext(ObfuscationStatus manager)
    {
        this.manager = manager;
    }

    public void setTotalClassesToRead(long totalClassesToRead)
    {
        this.totalClassesToRead = totalClassesToRead;
        this.manager.onAnythingChange();
    }

    public void setTotalClassesRead(long totalClassesRead)
    {
        this.totalClassesRead = totalClassesRead;
        this.manager.onAnythingChange();
    }

    public void setReadingClassName(String readingClassName)
    {
        this.readingClassName = readingClassName;
        this.manager.onAnythingChange();
    }
}
