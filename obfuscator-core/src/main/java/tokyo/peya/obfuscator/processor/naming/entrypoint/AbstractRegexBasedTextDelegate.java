package tokyo.peya.obfuscator.processor.naming.entrypoint;

import tokyo.peya.obfuscator.clazz.ClassReference;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractRegexBasedTextDelegate extends AbstractPlainTextDelegate
{
    private final Pattern pattern;
    private final int groupIndex;

    public AbstractRegexBasedTextDelegate(String pattern)
    {
        // 正規表現パターンをコンパイル
        this.pattern = Pattern.compile(pattern);
        this.groupIndex = 1;  // デフォルトでは最初のグループを使用
    }

    public AbstractRegexBasedTextDelegate(Pattern pattern, int groupIndex)
    {
        this.pattern = pattern;
        this.groupIndex = groupIndex;
    }

    @Override
    protected ClassReference getMainClassName(String entryName, String text)
    {
        // 正規表現を使用してメインクラス名を抽出
        Matcher matcher = this.pattern.matcher(text);
        if (matcher.find())
        {
            String mainClassName = matcher.group(1).trim();
            return ClassReference.of(mainClassName);
        }
        return null;  // マッチしなかった場合は null を返す
    }

    @Override
    protected String renameMainClass(String entryName, ClassReference renamedClassReference, String text)
    {
        Matcher matcher = this.pattern.matcher(text);
        if (matcher.find())
        {
            String fullMatch = matcher.group(0);  // 例: "Main-Class: com.example.Main"
            String classPart = matcher.group(1);  // 例: "com.example.Main"

            // classPart を新しい名前に置換した行を作る
            String replacedLine = fullMatch.replace(classPart, renamedClassReference.getFullQualifiedDotName());

            // テキスト全体の中でその行を1箇所だけ置き換える
            return matcher.replaceFirst(Matcher.quoteReplacement(replacedLine));
        }
        return text;
    }
}
