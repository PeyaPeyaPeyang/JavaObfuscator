# Java Obfuscator (original by [superblaubeere27/obfuscator](https://github.com/superblaubeere27/obfuscator)

*** GUIs are supported fully ***

A Java bytecode obfuscator supporting

* Flow Obfuscation
* Line Number Removal
* Number Obfuscation
* Name Obfuscation (Classes, methods and fields) with custom dictionaries
* Deobfuscator crasher
* String Encryption(Supporting algorithms: AES, XOR, Blowfish, DES)
* Inner Class Removal
* Invoke Dynamic
* Reference Proxy(Accessing all fields with reflection.)
* Member Shuffling & Hiding

## Differences from the original

+ NO HWID Protections
+ NO NumberObfuscation crashing bugs
+ Improved codes(**I rewrote!!**)
+ Advanced NameObfuscation configurations
+ Mapping saving
+ More algorithm for StringEncryption
+ Better log color
+ Rename Main-Class in MANIFEST.MF
+ Advanced string hiding
  * Optimizing strings ledger with reusing string references

## Obfuscated code

Luyten + Procyon

Without

```Java

public class HelloWorld
{
    public HelloWorld()
    {
        super();
    }

    public static void main(final String[] args)
    {
        System.out.println("Hello World");
        for (int i = 0; i < 10; ++i)
        {
            System.out.println(i);
        }
    }
}
```

Obfuscated (short version for full code visit https://pastebin.com/RFHtgPtX)

```Java

public class HelloWorld
{

    public static void main(final String[] array)
    {
        // invokedynamic(1:(Ljava/io/PrintStream;Ljava/lang/String;)V, invokedynamic(0:()Ljava/io/PrintStream;), HelloWorld.llII[HelloWorld.lllI[0]])
        float lllllllIlIllIII = HelloWorld.lllI[0];
        while (llIll((int) lllllllIlIllIII, HelloWorld.lllI[1]))
        {
            // invokedynamic(2:(Ljava/io/PrintStream;I)V, invokedynamic(0:()Ljava/io/PrintStream;), lllllllIlIllIII)
            ++lllllllIlIllIII;
            "".length();
            if (" ".length() == (" ".length() << ("   ".length() << " ".length()) & ~(" ".length() << ("   ".length() << " ".length()))))
            {
                throw null;
            }
        }
    }

}

```

## Usage

`--help` Prints the help page on the screen

`--version` Shows the version of the obfuscator

`--jarIn <input>` Input JAR

`--jarOut <output>` Output JAR

`--config <configFile>` Config File

`--cp <classPath>` Class Path

`--scriptFile <scriptFile>` A JS file to script certain parts of the obfuscation

`--threads` Sets the number of threads the obfuscator should use

`--verbose` Sets logging to verbose mode

### Examples

`java -jar obfuscator.jar --jarIn helloWorld.jar --jarOut helloWorld-obf.jar`

`java -jar obfuscator.jar --jarIn helloWorld.jar --jarOut helloWorld-obf.jar --config obfConfig`

### Example Config

```
{
  "input": "",
  "output": "",
  "script": "function isRemappingEnabledForClass(node) {\n    return true;\n}\nfunction isObfuscatorEnabledForClass(node) {\n    return true;\n}",
  "threads": 28,
  "libraries": [],
  "string_encryption": {
    "enabled": true,
    "algorithm_aes": true,
    "algorithm_xor": true,
    "algorithm_blowfish": true,
    "algorithm_des": true
  },
  "line_number_remover": {
    "enabled": true,
    "rename_local_variables": true,
    "remove_line_numbers": true,
    "add_confusing_local_variables": true
  },
  "number_obfuscation": {
    "enabled": true,
    "extract_to_array": true,
    "obfuscate_zero": true,
    "shift": false,
    "and": true,
    "xor": true,
    "string_length": true,
    "simle_math": true,
    "multiple_instructions": true
  },
  "static_initialisation": {
    "enabled": true
  },
  "hide_strings": {
    "enabled": true,
    "optimise_ledger": true,
    "start_marker": "",
    "delimiter": "",
    "end_marker": ""
  },
  "optimiser": {
    "enabled": false,
    "replace_string_equals": false,
    "replace_string_equals_ignore_case": false,
    "optimise_static_string_calls": false
  },
  "invoke_dynamic": {
    "enabled": false
  },
  "general": {
    "excluded_classes": "me.name.Class\nme.name.*\nio.netty.**",
    "generator_characters": "Il",
    "custom_dictionary": false,
    "class_names_dictionary": "",
    "name_dictionary": "",
    "Use_store": false
  },
  "decompiler_crasher": {
    "enabled": false,
    "invalid_signatures": true,
    "empty_annotation_spam": true
  },
  "name_obfuscation": {
    "enabled": false,
    "excluded_classes": "me.name.Class\nme.name.*\nio.netty.**",
    "excluded_methods": "me.name.Class.method\nme.name.Class**\nme.name.Class.*",
    "excluded_fields": "me.name.Class.field\nme.name.Class.*\nme.name.**",
    "allow_missing_libraries": false,
    "enabled_for_class_names": true,
    "enabled_for_method_names": true,
    "enabled_for_field_names": true,
    "randomise_package_structure": false,
    "new_packages": "",
    "randomise_source_file_names": false,
    "randomise_debug_source_file_names": false,
    "new_source_and_debug_file_names": "",
    "save_mappings": false
  },
  "hide_members": {
    "enabled": true
  },
  "shuffler": {
    "enabled": true,
    "shuffle_class_structure": true,
    "shuffle_method_structure": true,
    "shuffle_field_structure": true,
    "shuffle_annotations": true,
    "shuffle_source_file": true
  },
  "FlowObfuscator": {
    "enabled": true,
    "mangle_comparisons": true,
    "replace_goto": true,
    "replace_if": true,
    "bad_pop": true,
    "bad_concat": true,
    "mangle_switches": false,
    "mangle_return": false,
    "Mangle_local_variables": false
  },
  "inner_class_remover": {
    "enabled": true,
    "relocate_classes": false,
    "remove_metadata": true
  }
}
```

### Excluding Classes

In some situations you need to prevent certain classes from being obfuscated, such as dependencies packaged with your
jar or mixins in a forge mod.

You will need to exclude in two places.

##### Scripting Tab

Here is an example script that will obfuscate and remap all classes except the org.json dependency and mixins.

```javascript
function isRemappingEnabledForClass(node) {
    var flag1 = !node.name.startsWith("org/json");
    var flag2 = !node.name.startsWith("com/client/mixin");
    return flag1 && flag2;
}

function isObfuscatorEnabledForClass(node) {
    var flag1 = !node.name.startsWith("org/json");
    var flag2 = !node.name.startsWith("com/client/mixin");
    return flag1 && flag2;
}
```

##### Name Obfuscation

If you also want to exclude these classes from name obfuscation you will need to go to Transformers -> Name Obfuscation
and add these exclusions there.

To Exclude the same classes as we did above, we would need to add the following to Excluded classes, methods and fields.

```regexp
org.json.**
com.client.mixin.**~~~~
```

If your classes are still being obfuscated after applyinng both of these exclusions please open an issue.

## Credits

- superblaubeere27 (**Original** Obfuscator(Master of this fork))
- MCInjector (FFixer base)
- FFixer (Obfuscator base)
- SmokeObfuscator (Some ideas)
- MarcoMC (Some ideas)
- ItzSomebody (NameUtils.crazyString(), Crasher)

