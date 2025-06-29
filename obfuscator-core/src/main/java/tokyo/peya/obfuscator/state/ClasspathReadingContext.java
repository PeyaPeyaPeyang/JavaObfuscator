/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 * Copyright (c) 2025 Peyang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

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
