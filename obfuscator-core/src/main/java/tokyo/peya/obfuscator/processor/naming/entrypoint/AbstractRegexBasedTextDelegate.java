/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 * Copyright (c) 2025 Peyang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

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
    protected boolean canProvideMainClass(String entryName, String content)
    {
        // 正規表現を使用してメインクラス名が存在するかをチェック
        Matcher matcher = this.pattern.matcher(content);
        return matcher.find();  // マッチした場合は true を返す
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
