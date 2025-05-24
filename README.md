# Java Obfuscator（[superblaubeere27/obfuscator](https://github.com/superblaubeere27/obfuscator) 由来のフォーク）

(For English, see [README-en.md](README-en.md).)

**:white_check_mark: GUI完全対応 / 日本語＆英語表示対応**

このツールはJavaバイトコード向けの強力な難読化器で、次のような機能をサポートしています：

## 主な機能

- :white_check_mark: **フロー制御難読化（Flow Obfuscation）**  
  制御構造を改変してリバースエンジニアリングを困難にする
- :white_check_mark: **行番号の削除**  
  ソースコードの行番号情報を削除し、デバッグを妨害
- :white_check_mark: **数値難読化（ビット演算、文字列長利用など）**  
  数値を複雑な式や配列参照に変換し可読性を低下
- :white_check_mark: **名前の難読化（クラス名・メソッド名・フィールド名）＋カスタム辞書対応**  
  意味のある名前を無意味な記号に変換し解析を困難にする
- :white_check_mark: **Decompiler Crasher（動作安定化済み）**  
  一部のJava逆コンパイラをクラッシュさせる構文を安全に挿入
- :white_check_mark: **文字列暗号化（AES / XOR / Blowfish / DES に対応）**  
  ハードコードされた文字列をランタイムで復号化する方式に変更
- :white_check_mark: **インナークラス削除**  
  内部クラスを外部クラスとして展開・統合し構造を単純化
- :white_check_mark: **`invokedynamic` の活用**  
  メソッド呼び出しに `invokedynamic` を使用して可読性と追跡性を低下
- :white_check_mark: **参照プロキシ（全フィールドをリフレクション経由でアクセス）**  
  通常アクセスを避けてリフレクションでメンバーへアクセス
- :white_check_mark: **クラス・メソッド・フィールドの構造シャッフル＆隠蔽**  
  宣言順やアノテーションをランダム化し、見た目と構造を不安定化
- :white_check_mark: **プレビュー機能（設定結果をリアルタイムで確認可能）**  
  GUIから設定した内容を即座に反映・確認できる機能
- :white_check_mark: **GUI / ログの多言語化（現在は日本語・英語に対応）**  
  インターフェースと出力メッセージを多言語に対応
- :white_check_mark: **スクリプトによる除外設定**  
  特定のクラスやパッケージを難読化対象から除外するためのJavaScriptスクリプト機能
- :white_check_mark: **設定ファイル（JSON）による高度なカスタマイズ**  
  難読化の各種設定をJSON形式で柔軟に制御可能

---

## オリジナル版との差分

- :x: HWID制限を撤廃
- :white_check_mark: 数値難読化バグの修正（クラッシュなし）
- :white_check_mark: 全体的なコードの書き直しと改善
- :white_check_mark: 名前難読化設定の高機能化
- :white_check_mark: マッピングファイルの保存に対応
- :white_check_mark: 文字列暗号化アルゴリズムの追加
- :white_check_mark: ログ出力のカラーリング改善
- :white_check_mark: `MANIFEST.MF` 内の `Main-Class` 名のリネーム機能
- :white_check_mark: 文字列隠蔽時の, 文字列再利用による台帳の最適化（同じ文字列は1回だけ保持）

---

## 使用方法

+ [Java 17](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html) 以降が必要です。

```bash
java -jar obfuscator.jar --jarIn helloWorld.jar --jarOut helloWorld-obf.jar
java -jar obfuscator.jar --jarIn helloWorld.jar --jarOut helloWorld-obf.jar --config obfConfig.json
java -jar obfuscator.jar # GUI つき
```

---

## コマンドライン引数

- `--help`：ヘルプ表示
- `--version`：バージョン情報表示
- `--jarIn <入力JAR>`
- `--jarOut <出力JAR>`
- `--config <設定ファイル（JSON）>`
- `--cp <クラスパス>`
- `--scriptFile <スクリプトJSファイル>`
- `--threads <スレッド数>`
- `--verbose`：詳細ログを有効化

---

## 設定ファイル例（抜粋）

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

## 除外設定（特定クラスを難読化対象外にする）

JavaScriptスクリプトで制御する：

```js
function isRemappingEnabledForClass(node) {
    return !node.name.startsWith("org/json") && !node.name.startsWith("com/client/mixin");
}

function isObfuscatorEnabledForClass(node) {
    return !node.name.startsWith("org/json") && !node.name.startsWith("com/client/mixin");
}
```

さらに `name_obfuscation` セクションに除外指定を追記：

```
org.json.**
com.client.mixin.**
```

---

## 難読化結果（例）

元コード：

```java
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello World");
        for (int i = 0; i < 10; ++i) {
            System.out.println(i);
        }
    }
}
```

難読化後（詳細省略）：

```java
public class HelloWorld {
    public static void main(final String[] array) {
        float lllllllIlIllIII = HelloWorld.lllI[0];
        while (llIll((int) lllllllIlIllIII, HelloWorld.lllI[1])) {
            ++lllllllIlIllIII;
            "".length();
        }
    }
}
```

---

## クレジット

- [superblaubeere27](https://github.com/superblaubeere27)（オリジナル作者）
- FFixer, MCInjector, SmokeObfuscator などのアイデアを活用
- MarcoMC, ItzSomebody ほか

---

## ライセンス

本プロジェクトは [MIT ライセンス](LICENSE)に準拠しています。
