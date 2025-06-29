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

package tokyo.peya.obfuscator.configuration;

import tokyo.peya.obfuscator.Localisation;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ValueManager
{
    private static final List<Value<?>> values = new ArrayList<>();
    private static final Map<String, String> ownerLocalisationMap = new HashMap<>();

    private static void registerField(Field field, Object object)
    {

        try
        {
            field.setAccessible(true);

            Object obj = field.get(object);

            if (obj instanceof Value<?> value)
            {
                if (!ownerLocalisationMap.containsKey(value.getOwner()))
                    throw new IllegalArgumentException("Owner " + value.getOwner() + " not recognised.");
                values.add(value);
            }
        }
        catch (IllegalAccessException | NullPointerException ignored)
        {
        }
    }

    public static void registerClass(Object obj)
    {
        registerClass(obj.getClass(), obj);
    }

    public static void registerClass(Class<?> clazz)
    {
        registerClass(clazz, null);
    }

    private static void registerClass(Class<?> clazz, Object obj)
    {
        for (Field field : clazz.getDeclaredFields())
            registerField(field, obj);
    }

    public static void registerOwner(String owner, String localisationKey)
    {
        ownerLocalisationMap.put(owner, localisationKey);
    }

    public static String getLocalisedOwnerName(String owner)
    {
        String localisationKey = ownerLocalisationMap.get(owner);
        if (localisationKey == null)
            return owner;

        return Localisation.get(localisationKey);
    }

    public static List<Value<?>> getValues()
    {
        return Collections.unmodifiableList(values);
    }
}
