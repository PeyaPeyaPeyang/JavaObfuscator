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

package tokyo.peya.obfuscator.utils.values;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
public abstract class Value<T>
{
    private final String owner;
    private final String name;
    private final String description;
    private final DeprecationLevel deprecation;
    @Getter(AccessLevel.NONE)
    @Setter
    private T value;

    public Value(String owner, String name, DeprecationLevel deprecation, T object)
    {
        this(owner, name, "", deprecation, object);
    }

    public Value(String owner, String name, String description, DeprecationLevel deprecation, T object)
    {
        this.owner = owner;
        this.name = name;
        this.description = description;
        this.deprecation = deprecation;
        this.value = object;
    }

    public T get()
    {
        return this.value;
    }

    @Override
    public String toString()
    {
        return String.format("%s::%s = %s", this.owner, this.name, this.value);
    }
}
