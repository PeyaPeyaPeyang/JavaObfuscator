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

import lombok.Getter;
import tokyo.peya.obfuscator.configuration.DeprecationLevel;
import tokyo.peya.obfuscator.configuration.ValueManager;
import tokyo.peya.obfuscator.configuration.values.BooleanValue;
import tokyo.peya.obfuscator.configuration.values.FilePathValue;
import tokyo.peya.obfuscator.configuration.values.StringValue;

@Getter
public class GeneralSettings
{
    private static final String PROCESSOR_NAME = "general";
    private final StringValue excludedClasses = new StringValue(
            PROCESSOR_NAME,
            "excluded_classes",
            "ui.transformers.general.excluded_classes",
            DeprecationLevel.AVAILABLE,
            "me.name.Class\nme.name.*\nio.netty.**",
            5
    );
    private final StringValue generatorChars = new StringValue(
            PROCESSOR_NAME,
            "generator_characters",
            "ui.transformers.general.generator_char",
            DeprecationLevel.AVAILABLE,
            "Il"
    );
    private final BooleanValue useCustomDictionary = new BooleanValue(
            PROCESSOR_NAME,
            "custom_dictionary",
            "ui.transformers.general.use_custom_dictionary",
            DeprecationLevel.AVAILABLE,
            false
    );
    private final FilePathValue classNamesDictionary = new FilePathValue(
            PROCESSOR_NAME,
            "class_names_dictionary",
            "ui.transformers.general.class_names_dictionary",
            DeprecationLevel.AVAILABLE,
            ""
    );
    private final FilePathValue namesDictionary = new FilePathValue(
            PROCESSOR_NAME,
            "name_dictionary",
            "ui.transformers.general.other_names_dictionary",
            DeprecationLevel.AVAILABLE,
            ""
    );
    private final BooleanValue useStore = new BooleanValue(
            PROCESSOR_NAME,
            "Use_store",
            "ui.transformers.general.use_store",
            DeprecationLevel.AVAILABLE,
            false
    );

    static
    {
        ValueManager.registerOwner(PROCESSOR_NAME, "ui.transformers.general");
        ValueManager.registerClass(GeneralSettings.class);
    }
}

