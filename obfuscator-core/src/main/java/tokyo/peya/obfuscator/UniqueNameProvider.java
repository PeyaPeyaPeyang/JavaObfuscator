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

import com.google.common.io.Files;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import tokyo.peya.obfuscator.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class UniqueNameProvider
{

    private final String generativeChars;
    private final boolean usingCustomDictionary;
    private final HashMap<String, Integer> packageMap;
    private final Map<String, HashMap<String, Integer>> usedMethods;
    private final Map<String, Integer> usedFields;
    private final List<String> classNames;
    private final List<String> names;

    private int localVars;
    private int methods;
    private int fields;

    public UniqueNameProvider(GeneralSettings settings)
    {
        normalizeSettings(settings);

        this.generativeChars = settings.getGeneratorChars().get();
        this.usingCustomDictionary = settings.getUseCustomDictionary().get();

        this.usedFields = new HashMap<>();
        this.usedMethods = new HashMap<>();
        this.packageMap = new HashMap<>();

        if (!this.usingCustomDictionary)
        {
            this.classNames = new ArrayList<>();
            this.names = new ArrayList<>();
            return;
        }

        try
        {
            this.classNames = Files.readLines(
                    new File(settings.getClassNamesDictionary().get()),
                    StandardCharsets.UTF_8
            );
            this.names = Files.readLines(new File(settings.getNamesDictionary().get()), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to load names: " + e.getLocalizedMessage(), e);
        }
    }

    public String generateClassName()
    {
        return generateClassName("");
    }

    public String generateClassName(String packageName)
    {
        if (!this.packageMap.containsKey(packageName))
            this.packageMap.put(packageName, 0);

        int id = this.packageMap.get(packageName);
        this.packageMap.put(packageName, id + 1);

        return getName(this.classNames, id);
    }

    private String getName(List<String> dictionary, int id)
    {
        if (this.usingCustomDictionary && id < dictionary.size())
            return dictionary.get(id);

        return Utils.convertToBase(id, this.generativeChars);
    }

    public String generateMethodName(final ClassNode classNode, String desc)
    {
        return this.toUniqueMethodName(classNode, getName(this.names, this.methods++), desc);
    }

    public String generateFieldName(final ClassNode classNode)
    {
        return toUniqueFieldName(classNode, getName(this.names, this.fields++));
    }

    public String generateLocalVariableName(MethodNode node)
    {
        if (this.localVars == 0)
            this.localVars = Short.MAX_VALUE;
        return this.toUniqueLocalVariableName(node, getName(this.names, this.localVars--));
    }

    public String toUniqueMethodName(ClassNode method, String nameCandidate, String desc)
    {
        return generateUniqueName(
                nameCandidate,
                s -> method.methods.stream()
                                   .anyMatch(m -> m.name.equals(s) && m.desc.equals(desc))
        );
    }

    public String toUniqueFieldName(ClassNode method, String nameCandidate)
    {
        return generateUniqueName(
                nameCandidate,
                s -> method.fields.stream()
                                  .anyMatch(f -> f.name.equals(s))
        );
    }

    public String toUniqueLocalVariableName(MethodNode method, String nameCandidate)
    {
        return generateUniqueName(
                nameCandidate,
                s -> method.localVariables != null && method.localVariables.stream()
                                              .anyMatch(lv -> lv.name.equals(s))
        );
    }

    public void mapClass(String old, String newName)
    {
        if (this.usedMethods.containsKey(old))
            this.usedMethods.put(newName, this.usedMethods.get(old));
        if (this.usedFields.containsKey(old))
            this.usedFields.put(newName, this.usedFields.get(old));
    }

    private static void normalizeSettings(GeneralSettings settings)
    {
        if (settings.getGeneratorChars().get().isEmpty())
        {
            settings.getGeneratorChars().setValue("Il");
            throw new IllegalStateException("The generator chars are empty. Changing them to 'Il'");
        }
    }

    private static String generateUniqueName(String candidate, Predicate<? super String> check)
    {
        String name = candidate;
        int tryna = 0;
        while (true)
        {
            if (check.test(name))
                name = candidate + "$" + ++tryna;
            else
                break;
        }

        return name;
    }
}
