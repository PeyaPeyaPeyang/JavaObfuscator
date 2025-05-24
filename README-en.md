# Java Obfuscator (Fork based on [superblaubeere27/obfuscator](https://github.com/superblaubeere27/obfuscator))

(日本語は [README.md](README.md) を参照してください。)

**✅ Full GUI Support / Japanese & English Language Support**

This is a powerful obfuscation tool for Java bytecode, offering the following features:

---

## Main Features

- ✅ **Flow Obfuscation**  
  Alters control flow structures to hinder reverse engineering.

- ✅ **Line Number Removal**  
  Strips line number information to obstruct debugging efforts.

- ✅ **Number Obfuscation**  
  Converts numeric values into complex expressions (e.g., bitwise operations, string length usage).

- ✅ **Name Obfuscation (class/method/field) + Custom Dictionary Support**  
  Replaces meaningful names with meaningless ones; supports custom dictionaries.

- ✅ **Decompiler Crasher (Stabilized)**  
  Safely inserts constructs that crash certain Java decompilers.

- ✅ **String Encryption (Supports AES / XOR / Blowfish / DES)**  
  Encodes hardcoded strings to be decrypted at runtime.

- ✅ **Inner Class Removal**  
  Flattens internal classes into outer structures for simplification.

- ✅ **Use of `invokedynamic`**  
  Makes method calls harder to read and trace.

- ✅ **Reference Proxy (Access fields via reflection)**  
  Avoids direct field access in favor of reflection-based access.

- ✅ **Class/Method/Field Structure Shuffling & Hiding**  
  Randomizes declaration order and annotations to disrupt structural analysis.

- ✅ **Preview Feature**  
  Real-time preview of current configuration through the GUI.

- ✅ **Multilingual GUI / Log Output (Japanese & English Supported)**  
  Interface and logs support multiple languages.

- ✅ **Script-based Exclusion Rules**  
  JavaScript-based logic to exclude certain classes/packages from obfuscation.

- ✅ **Configurable via JSON**  
  Highly flexible JSON-based configuration format.

---

## Differences from the Original

- ❌ Removed HWID restrictions
- ✅ Fixed numeric obfuscation crash bug
- ✅ Full codebase cleanup and improvement
- ✅ Enhanced name obfuscation capabilities
- ✅ Added support for saving mapping files
- ✅ Added more string encryption algorithms
- ✅ Improved log coloring
- ✅ Added `Main-Class` renaming support in `MANIFEST.MF`
- ✅ Optimized string reuse (identical strings are stored only once)

---

## Usage Examples

*(Insert code block here)*

---

## Command-line Options

- `--help`: Show help
- `--version`: Show version
- `--jarIn <input JAR>`
- `--jarOut <output JAR>`
- `--config <JSON config file>`
- `--cp <classpath>`
- `--scriptFile <script.js>`
- `--threads <number>`
- `--verbose`: Enable verbose logging

---

## Sample Config (Partial)

```json
{
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
  },
  "decompiler_crasher": {
    "enabled": true,
    "invalid_signatures": true,
    "empty_annotation_spam": true
  },
  "preview": {
    "enabled": true
  }
}
```
---

## Exclusion Rules (Skip Obfuscation for Specific Classes)

Controlled by JavaScript logic:

```js
function isRemappingEnabledForClass(node) {
    return !node.name.startsWith("org/json") && !node.name.startsWith("com/client/mixin");
}

function isObfuscatorEnabledForClass(node) {
    return !node.name.startsWith("org/json") && !node.name.startsWith("com/client/mixin");
}
```

Additionally, add patterns to `name_obfuscation` section:
```
org.json.**
com.client.mixin.**
```


---

## Obfuscation Output (Example)

Original Code:

*(Insert code block here)*

Obfuscated Code (simplified):

*(Insert code block here)*

---

## Credits

- [superblaubeere27](https://github.com/superblaubeere27) (Original author)
- Concepts inspired by FFixer, MCInjector, SmokeObfuscator
- Contributions from MarcoMC, ItzSomebody, and others

---

## License

This project is licensed under the [MIT License](LICENSE).
