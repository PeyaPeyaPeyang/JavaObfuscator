/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 * Copyright (c) ${YEAR}      Peyang
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
import tokyo.peya.obfuscator.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class UniqueNameProvider
{
    /**
     * By ItzSomebody
     */
    private static final char[] DICT_SPACES = {
            '\u2000', '\u2001', '\u2002', '\u2003', '\u2004', '\u2005', '\u2006', '\u2007', '\u2008', '\u2009', '\u200A', '\u200B', '\u200C', '\u200D', '\u200E', '\u200F'
    };

    private static final Random RANDOM = new Random();

    private static List<String> classNames = new ArrayList<>();
    private static List<String> names = new ArrayList<>();
    private final String generativeChars;
    private final boolean usingCustomDictionary;
    private final HashMap<String, Integer> packageMap;
    private final Map<String, HashMap<String, Integer>> usedMethods;
    private final Map<String, Integer> usedFields;

    private int localVars;
    private int methods;
    private int fields;

    public UniqueNameProvider(JObfSettings settings)
    {
        this.generativeChars = settings.getGeneratorChars().get();
        this.usingCustomDictionary = settings.getUseCustomDictionary().get();
        this.usedFields = new HashMap<>();
        this.usedMethods = new HashMap<>();
        this.packageMap = new HashMap<>();

        try
        {
            if (this.usingCustomDictionary)
            {
                classNames = Files.readLines(new File(settings.getClassNameDictionary().get()), StandardCharsets.UTF_8);
                names = Files.readLines(new File(settings.getNameDictionary().get()), StandardCharsets.UTF_8);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to load names: " + e.getLocalizedMessage(), e);
        }
    }

    private static void normalizeSettings(JObfSettings settings)
    {
        if (settings.getGeneratorChars().get().isEmpty())
        {
            settings.getGeneratorChars().setValue("Il");
            throw new IllegalStateException("The generator chars are empty. Changing them to 'Il'");
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static int randInt(int min, int max)
    {
        return RANDOM.nextInt(max - min) + min;
    }

    public static String generateSpaceString(int length)
    {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < length; i++)
            stringBuilder.append(" ");
        return stringBuilder.toString();
    }

    /**
     * @param len Length of the string to generate.
     * @return a built {@link String} consisting of DICT_SPACES.
     * @author ItzSomebody
     * Generates a {@link String} consisting only of DICT_SPACES.
     * Stole this idea from NeonObf and Smoke.
     */
    public static String crazyString(int len)
    {
        char[] buildString = new char[len];
        for (int i = 0; i < len; i++)
            buildString[i] = DICT_SPACES[RANDOM.nextInt(DICT_SPACES.length)];
        return new String(buildString);
    }

    public static String unicodeString(int length)
    {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < length; i++)
            stringBuilder.append((char) randInt(128, 250));
        return stringBuilder.toString();
    }

    public static String getPackage(String in)
    {
        int lin = in.lastIndexOf('/');

        if (lin == 0)
            throw new IllegalArgumentException("Illegal class name");

        return lin == -1 ? "": in.substring(0, lin);
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

        return getName(classNames, id);
//        return ClassNameGenerator.className(Utils.random(2, 5));
    }

    private String getName(List<String> dictionary, int id)
    {
        if (this.usingCustomDictionary && id < dictionary.size())
            return dictionary.get(id);

        return Utils.convertToBase(id, this.generativeChars);
    }

    public String generateMethodName(final String className, String desc)
    {
        return getName(names, this.methods++);
    }

    public String generateMethodName(final ClassNode classNode, String desc)
    {
        String nameBase = generateMethodName(classNode.name, desc);
        String name = nameBase;
        int tryna = 0;
        while (true)
        {
            String name$ = name;
            if (classNode.methods.stream().anyMatch(methodNode -> methodNode.name.equals(name$)))
                name = nameBase + "$" + ++tryna;
            else
                break;
        }

        return name;
    }

    public String generateFieldName(final String className)
    {
        return getName(names, this.fields++);
    }

    public String generateFieldName(final ClassNode classNode)
    {
        return generateFieldName(classNode.name);
    }

    public String generateLocalVariableName(final String className, final String methodName)
    {
        return generateLocalVariableName();
    }

    public String generateLocalVariableName()
    {
        if (this.localVars == 0)
            this.localVars = Short.MAX_VALUE;
        return Utils.convertToBase(this.localVars--, this.generativeChars);
    }

    public void mapClass(String old, String newName)
    {
        if (this.usedMethods.containsKey(old))
            this.usedMethods.put(newName, this.usedMethods.get(old));
        if (this.usedFields.containsKey(old))
            this.usedFields.put(newName, this.usedFields.get(old));
    }
}
