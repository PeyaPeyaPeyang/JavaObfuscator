package tokyo.peya.obfuscator.state;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ObfuscationState
{
    NONE(null),

    READING_CLASS_PATH(ClasspathReadingContext.class),
    READING_CLASSES(ClassReadingContext.class),

    PROCESSING_CLASSES(ProcessingContext.class),
    PROCESSING_CLASS_NAMES(NameProcessingContext.class),

    ENCODING_CLASSES(EncodingContext.class),
    WRITING_CLASSES(ClassesWritingContext.class),
    WRITING_RESOURCES(ResourcesWritingContext.class),

    DONE(null);

    private final Class<? extends StatusContext> contextClass;
}
