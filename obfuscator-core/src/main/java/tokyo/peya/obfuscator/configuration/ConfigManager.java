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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Slf4j(topic = "Configuration")
public class ConfigManager
{
    private static Gson gson = new Gson();

    public static String generateConfig(Configuration config, boolean prettyPrint)
    {
        if (prettyPrint)
            gson = new GsonBuilder().setPrettyPrinting().create();
        else
            gson = new GsonBuilder().create();

        final JsonObject jsonObject = new JsonObject();

        config.addToJsonObject(jsonObject);

        Map<String, ArrayList<Value<?>>> ownerValueMap = buildValueMap();
        for (Map.Entry<String, ArrayList<Value<?>>> entry : ownerValueMap.entrySet())
        {
            final JsonObject moduleJson = new JsonObject();

            // noinspection rawtypes
            for (final Value value : entry.getValue())
            {
                if (value.get() instanceof Number)
                    moduleJson.addProperty(value.getName(), (Number) value.get());
                else if (value.get() instanceof Boolean)
                    moduleJson.addProperty(value.getName(), (Boolean) value.get());
                else if (value.get() instanceof String)
                    moduleJson.addProperty(value.getName(), (String) value.get());
            }

            jsonObject.add(entry.getKey(), moduleJson);
        }

        return gson.toJson(jsonObject);
    }

    @SuppressWarnings("unchecked")
    public static Configuration loadConfig(String config)
    {
        final JsonElement jsonElement = gson.fromJson(config, JsonElement.class);

        if (jsonElement instanceof JsonNull)
            throw new IllegalArgumentException("JsonObject isn't valid");

        final JsonObject jsonObject = (JsonObject) jsonElement;

        Configuration configuration = Configuration.fromJsonObject(jsonObject);

        Map<String, ArrayList<Value<?>>> ownerValueMap = buildValueMap();

        for (Map.Entry<String, ArrayList<Value<?>>> entry : ownerValueMap.entrySet())
        {
            if (!jsonObject.has(entry.getKey()))
                continue;

            final JsonElement moduleElement = jsonObject.get(entry.getKey());

            if (moduleElement instanceof JsonNull)
                continue;

            final JsonObject moduleJson = (JsonObject) moduleElement;

            // noinspection rawtypes
            for (final Value value : entry.getValue())
            {
                try
                {
                    if (!moduleJson.has(value.getName()))
                        continue;

                    if (value.get() instanceof Float)
                        value.setValue(moduleJson.get(value.getName()).getAsFloat());
                    else if (value.get() instanceof Double)
                        value.setValue(moduleJson.get(value.getName()).getAsDouble());
                    else if (value.get() instanceof Integer)
                        value.setValue(moduleJson.get(value.getName()).getAsInt());
                    else if (value.get() instanceof Long)
                        value.setValue(moduleJson.get(value.getName()).getAsLong());
                    else if (value.get() instanceof Byte)
                        value.setValue(moduleJson.get(value.getName()).getAsByte());
                    else if (value.get() instanceof Boolean)
                        value.setValue(moduleJson.get(value.getName()).getAsBoolean());
                    else if (value.get() instanceof String)
                        value.setValue(moduleJson.get(value.getName()).getAsString());
                }
                catch (Throwable e)
                {
                    log.error(value.getName(), e);
                }
            }
        }
        return configuration;
    }

    public static Map<String, ArrayList<Value<?>>> buildValueMap()
    {
        HashMap<String, ArrayList<Value<?>>> ownerValueMap = new HashMap<>();
        for (Value<?> value : ValueManager.getValues())
        {
            if (!ownerValueMap.containsKey(value.getOwner()))
                ownerValueMap.put(value.getOwner(), new ArrayList<>());
            ownerValueMap.get(value.getOwner()).add(value);
        }
        return ownerValueMap;
    }
}
