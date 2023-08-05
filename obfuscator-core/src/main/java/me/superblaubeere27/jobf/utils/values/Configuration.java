/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package me.superblaubeere27.jobf.utils.values;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class Configuration
{
    private String input;
    private String output;
    private String script;
    private final List<String> libraries;

    public Configuration(String input, String output, String script, List<String> libraries)
    {
        this.input = input;
        this.output = output;
        this.script = script;
        this.libraries = libraries;
    }

    static Configuration fromJsonObject(JsonObject obj)
    {
        String input = "";
        String output = "";
        String script = null;
        List<String> libraries = new ArrayList<>();

        if (obj.has("input"))
        {
            input = obj.get("input").getAsString();
        }
        if (obj.has("output"))
        {
            output = obj.get("output").getAsString();
        }
        if (obj.has("script"))
        {
            script = obj.get("script").getAsString();
        }
        if (obj.has("libraries"))
        {
            JsonArray jsonArray = obj.getAsJsonArray("libraries");

            for (JsonElement jsonElement : jsonArray)
            {
                libraries.add(jsonElement.getAsString());
            }
        }

        return new Configuration(input, output, script, libraries);
    }

    void addToJsonObject(JsonObject jsonObject)
    {
        jsonObject.addProperty("input", this.input);
        jsonObject.addProperty("output", this.output);
        jsonObject.addProperty("script", this.script);

        JsonArray array = new JsonArray();

        for (String library : this.libraries)
        {
            array.add(new JsonPrimitive(library));
        }

        jsonObject.add("libraries", array);
    }

    public void setInput(String input)
    {
        this.input = input;
    }

    public void setOutput(String output)
    {
        this.output = output;
    }

    public void setScript(String script)
    {
        this.script = script;
    }

}
