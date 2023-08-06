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

package tokyo.peya.obfuscator.processors.name;

import lombok.extern.slf4j.Slf4j;
import tokyo.peya.obfuscator.utils.NameUtils;
import org.objectweb.asm.commons.Remapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j(topic = "obfuscator")
class CustomRemapper extends Remapper
{
    private final Map<String, String> map = new HashMap<>();
    private final Map<String, String> mapReversed = new HashMap<>();
    private final Map<String, String> packageMap = new HashMap<>();
    private final Map<String, String> packageMapReversed = new HashMap<>();
    private final Map<String, Map<String, String>> mapField = new HashMap<>(); //name + desc
    private final Map<String, Map<String, String>> mapFieldReversed = new HashMap<>(); //name + desc
    private final Map<String, Map<String, String>> mapMethod = new HashMap<>(); //name + desc
    private final Map<String, Map<String, String>> mapMethodReversed = new HashMap<>(); //name + desc

    /**
     * Map method name to the new name. Subclasses can override.
     *
     * @param owner owner of the method.
     * @param name  name of the method.
     * @param desc  descriptor of the method.
     * @return new name of the method
     */
    public String mapMethodName(String owner, String name, String desc)
    {
        Map<String, String> map = this.mapMethod.get(map(owner));
        if (map != null)
        {
            String data = map.get(name + mapDesc(desc));

            if (data != null)
            {
                return data;
            }
        }
        return name;
    }

    public boolean mapMethodName(String owner, String oldName, String oldDesc, String newName, boolean force)
    {
        Map<String, String> methods = this.mapMethod.get(map(owner));
        Map<String, String> methodsRev = this.mapMethodReversed.get(map(owner));
        if (methods == null)
        {
            methods = new HashMap<>();
            this.mapMethod.put(map(owner), methods);
        }
        if (methodsRev == null)
        {
            methodsRev = new HashMap<>();
            this.mapMethodReversed.put(map(owner), methodsRev);
        }
        if (!methodsRev.containsKey(newName + mapDesc(oldDesc)) || force)
        {
            methods.put(oldName + mapDesc(oldDesc), newName);
            methodsRev.put(newName + mapDesc(oldDesc), oldName + mapDesc(oldDesc));
            return true;
        }
        return false;
    }

    public boolean methodMappingExists(String owner, String oldName, String oldDesc)
    {
        return this.mapMethod.containsKey(map(owner)) && this.mapMethod.get(map(owner)).containsKey(oldName + mapDesc(oldDesc));
    }

    /**
     * Map invokedynamic method name to the new name. Subclasses can override.
     *
     * @param name name of the invokedynamic.
     * @param desc descriptor of the invokedynamic.
     * @return new invokdynamic name.
     */
    public String mapInvokeDynamicMethodName(String name, String desc)
    {
        return name;
    }

    /**
     * Map field name to the new name. Subclasses can override.
     *
     * @param owner owner of the field.
     * @param name  name of the field
     * @param desc  descriptor of the field
     * @return new name of the field.
     */
    public String mapFieldName(String owner, String name, String desc)
    {
        Map<String, String> map = this.mapField.get(map(owner));
        if (map != null)
        {
            String data = map.get(name + mapDesc(desc));
            if (data != null)
            {
                return data;
            }
        }
        return name;
    }

    public boolean mapFieldName(String owner, String oldName, String oldDesc, String newName, boolean force)
    {
        Map<String, String> fields = this.mapField.get(map(owner));
        Map<String, String> fieldsRev = this.mapFieldReversed.get(map(owner));
        if (fields == null)
        {
            fields = new HashMap<>();
            this.mapField.put(map(owner), fields);
        }
        if (fieldsRev == null)
        {
            fieldsRev = new HashMap<>();
            this.mapFieldReversed.put(map(owner), fieldsRev);
        }
        if (!fieldsRev.containsKey(newName + mapDesc(oldDesc)) || force)
        {
            fields.put(oldName + mapDesc(oldDesc), newName);
            fieldsRev.put(newName + mapDesc(oldDesc), oldName + mapDesc(oldDesc));
            return true;
        }
        return false;
    }

    public boolean fieldMappingExists(String owner, String oldName, String oldDesc)
    {
        return this.mapField.containsKey(map(owner)) && this.mapField.get(map(owner)).containsKey(oldName + mapDesc(oldDesc));
    }

    /**
     * Map type name to the new name. Subclasses can override.
     */
    public String map(String in)
    {
        int lin = in.lastIndexOf('/');
        String className = lin == -1 ? in: in.substring(lin + 1);
        if (lin == -1)
        {
            return this.map.getOrDefault(in, in);
        }
        else
        {
            String newClassName = this.map.getOrDefault(in, className);
            int nlin = newClassName.lastIndexOf('/');
            newClassName = nlin == -1 ? newClassName: newClassName.substring(nlin + 1);
            return mapPackage(in.substring(0, lin)) + "/" + newClassName;
        }
    }

    public String mapPackage(String in)
    {
        int lin = in.lastIndexOf('/');
        if (lin != -1)
        {
            String originalName = in.substring(lin + 1);
            String parentPackage = in.substring(0, lin);
            String newPackageName = this.packageMap.getOrDefault(in, originalName);
//            String newPackageName = "obfuscator";
            int nlin = newPackageName.lastIndexOf('/');
            newPackageName = nlin == -1 ? newPackageName: newPackageName.substring(nlin + 1);
            return mapPackage(parentPackage) + "/" + newPackageName;
        }
        else
        {
            return this.packageMap.getOrDefault(in, in);
//            return "obfuscator";
        }
//        return "classes";
    }

    public boolean mapPackage(String oldPackage, String newPackage)
    {
        if (!this.packageMapReversed.containsKey(newPackage) && !this.packageMap.containsKey(oldPackage))
        {
            this.packageMapReversed.put(newPackage, oldPackage);
            this.packageMap.put(oldPackage, newPackage);
            return true;
        }
        return false;
    }

    public String getClassName(String name)
    {
        return this.map.get(name);
    }

    public boolean map(String old, String newName)
    {
        Objects.requireNonNull(newName);

        if (this.mapReversed.containsKey(newName))
            return false;

        this.map.put(old, newName);
        this.mapReversed.put(newName, old);
        NameUtils.mapClass(old, newName);
        log.info("Mapped " + old + " to " + newName);
//        System.out.println(map(old));
        return true;
    }

    public String unmap(String ref)
    {
        return this.mapReversed.get(ref) == null ? ref: this.mapReversed.get(ref);
    }
}
