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

package tokyo.peya.obfuscator.configuration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
public class Configuration
{
    private final List<String> libraries;
    private String input;
    private String output;
    private String script;
    private int nThreads;
    private String mapping;

    public void addToJsonObject(JsonObject jsonObject)
    {
        jsonObject.addProperty("input", this.input);
        jsonObject.addProperty("output", this.output);
        jsonObject.addProperty("script", this.script);
        jsonObject.addProperty("threads", this.nThreads);

        JsonArray array = new JsonArray();

        for (String library : this.libraries)
            array.add(new JsonPrimitive(library));

        jsonObject.add("libraries", array);
    }

    public static Configuration fromJsonObject(JsonObject obj)
    {
        String input = "";
        String output = "";
        String script = null;
        int nThreads = -1;
        List<String> libraries = new ArrayList<>();
        String mapping = null;

        if (obj.has("input"))
            input = obj.get("input").getAsString();
        if (obj.has("output"))
            output = obj.get("output").getAsString();
        if (obj.has("script"))
            script = obj.get("script").getAsString();
        if (obj.has("threads"))
            nThreads = obj.get("threads").getAsInt();
        if (obj.has("libraries"))
        {
            JsonArray jsonArray = obj.getAsJsonArray("libraries");

            for (JsonElement jsonElement : jsonArray)
                libraries.add(jsonElement.getAsString());
        }
        if (obj.has("mapping"))
            mapping = obj.get("mapping").getAsString();

        return new Configuration(libraries, input, output, script, nThreads, mapping);
    }
}
