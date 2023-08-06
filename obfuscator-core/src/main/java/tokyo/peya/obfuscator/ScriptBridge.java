/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 * Copyright (c) 2023      Peyang 
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package tokyo.peya.obfuscator;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.objectweb.asm.tree.ClassNode;

public class ScriptBridge
{

    private final ScriptEngine jsEngine;

    public ScriptBridge(String script)
    {
        try
        {
            this.jsEngine = new ScriptEngineManager().getEngineByName("nashorn");
            this.jsEngine.eval(script);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Failed to compile Script", e);
        }
    }

    public boolean remapClass(ClassNode node)
    {
        try
        {
            Invocable invocable = (Invocable) this.jsEngine;

            return (boolean) invocable.invokeFunction("isRemappingEnabledForClass", node);
        }
        catch (NoSuchMethodException e)
        {
            return true;
        }
        catch (ScriptException e)
        {
            e.printStackTrace();
            return true;
        }
    }

    public boolean isObfuscatorEnabled(ClassNode node)
    {
        try
        {
            Invocable invocable = (Invocable) this.jsEngine;

            return (boolean) invocable.invokeFunction("isObfuscatorEnabledForClass", node);
        }
        catch (NoSuchMethodException e)
        {
            return true;
        }
        catch (ScriptException e)
        {
            e.printStackTrace();
            return true;
        }
    }

}
