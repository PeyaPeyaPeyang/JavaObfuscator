package tokyo.peya.obfuscator.state;

import lombok.Getter;

@Getter
public class ClassesWritingContext implements StatusContext
{
    private final ObfuscationStatus manager;

    private volatile long totalClassesToWrite;
    private volatile long totalClassesWritten;
    private volatile String writingClassName;

    public ClassesWritingContext(ObfuscationStatus manager)
    {
        this.manager = manager;
    }

    public void setTotalClassesToWrite(long totalClassesToWrite)
    {
        this.totalClassesToWrite = totalClassesToWrite;
        this.manager.onAnythingChange();
    }

    public void setTotalClassesWritten(long totalClassesWritten)
    {
        this.totalClassesWritten = totalClassesWritten;
        this.manager.onAnythingChange();
    }

    public void setWritingClassName(String writingClassName)
    {
        this.writingClassName = writingClassName;
        this.manager.onAnythingChange();
    }
}
