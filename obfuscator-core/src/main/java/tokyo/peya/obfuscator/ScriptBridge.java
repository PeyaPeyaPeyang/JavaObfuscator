/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 * Copyright (c) 2023-2025 Peyang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package tokyo.peya.obfuscator;

import lombok.extern.slf4j.Slf4j;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.objectweb.asm.tree.ClassNode;

@Slf4j(topic = "ScriptEngine")
public class ScriptBridge
{

    private final Scriptable scope;

    public ScriptBridge(String script)
    {
        try
        {
            Context context = Context.enter();
            this.scope = context.initStandardObjects();

            context.evaluateString(this.scope, script, "bridge_script", 1, null);
        }
        catch (Exception e)
        {
            log.error("Failed to initialize ScriptBridge", e);
            throw new IllegalStateException("Failed to initialize ScriptBridge", e);
        }
        finally
        {
            Context.exit(); // enterしたらexit必須
        }
    }

    public boolean remapClass(ClassNode node)
    {
        return invokeBooleanFunction("isRemappingEnabledForClass", node);
    }

    public boolean isObfuscatorEnabled(ClassNode node)
    {
        return invokeBooleanFunction("isObfuscatorEnabledForClass", node);
    }

    private boolean invokeBooleanFunction(String functionName, ClassNode node)
    {
        Context cx = Context.enter();
        try
        {
            Object fObj = this.scope.get(functionName, this.scope);
            if (!(fObj instanceof Function fn))
                return true;

            // nodeをJavaオブジェクトとして渡す
            Object result = fn.call(cx, this.scope, this.scope, new Object[]{node});
            return Context.toBoolean(result);

        } catch (Exception e)
        {
            log.error("Error invoking function {}: {}", functionName, e.getMessage(), e);
            return true;
        }
        finally
        {
            Context.exit();
        }
    }
}
