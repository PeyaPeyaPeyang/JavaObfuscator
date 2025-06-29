package tokyo.peya.obfuscator.processor.naming.entrypoint;

import tokyo.peya.obfuscator.clazz.ClassReference;

public abstract class AbstractYamlLikeTextDelegate extends AbstractPlainTextDelegate
{
    private final String linePrefix;

    public AbstractYamlLikeTextDelegate(String keyName)
    {
        this.linePrefix = keyName + ": ";
    }

    @Override
    protected ClassReference getMainClassName(String entryName, String text)
    {
        // 指定されたキー名が存在するかどうかをチェック
        if (!text.contains(this.linePrefix))
            return null;

        // キー名の行からメインクラス名を抽出する
        String[] lines = text.split("\n");
        for (String line : lines)
        {
            if (line.startsWith(this.linePrefix))
            {
                String mainClassName = line.substring(this.linePrefix.length()).trim();
                return ClassReference.of(mainClassName);
            }
        }

        // キー名の行が見つからない場合は null を返す
        return null;
    }

    @Override
    protected String renameMainClass(String entryName, ClassReference renamedClassReference, String text)
    {
        // 指定されたキー名が存在するかどうかをチェック
        if (!text.contains(this.linePrefix))
            return text;

        // キー名の行を新しいメインクラス名で置き換える
        String[] lines = text.split("\n");
        StringBuilder renamedText = new StringBuilder();
        for (String line : lines)
        {
            if (line.startsWith(this.linePrefix))
                line = this.linePrefix + renamedClassReference.getFullQualifiedDotName();  // 差し替え

            renamedText.append(line).append("\n");
        }
        return renamedText.toString().trim();
    }
}
