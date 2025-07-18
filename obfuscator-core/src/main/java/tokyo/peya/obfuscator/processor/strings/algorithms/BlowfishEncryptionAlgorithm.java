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

package tokyo.peya.obfuscator.processor.strings.algorithms;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import tokyo.peya.obfuscator.processor.strings.IStringEncryptionAlgorithm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public class BlowfishEncryptionAlgorithm implements IStringEncryptionAlgorithm
{
    @Override
    public String getName()
    {
        return "Blowfish";
    }

    @Override
    public String encrypt(String obj, String key)
    {
        try
        {
            SecretKeySpec keySpec = new SecretKeySpec(
                    MessageDigest.getInstance("MD5")
                                 .digest(key.getBytes(StandardCharsets.UTF_8)),
                    "Blowfish"
            );

            Cipher des = Cipher.getInstance("Blowfish");
            des.init(Cipher.ENCRYPT_MODE, keySpec);

            return new String(
                    Base64.getEncoder().encode(des.doFinal(obj.getBytes(StandardCharsets.UTF_8))),
                    StandardCharsets.UTF_8
            );

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

    public static String decrypt(String obj, String key)
    {
        try
        {
            SecretKeySpec keySpec = new SecretKeySpec(
                    MessageDigest.getInstance("MD5")
                                 .digest(key.getBytes(StandardCharsets.UTF_8)),
                    "Blowfish"
            );

            Cipher des = Cipher.getInstance("Blowfish");
            des.init(Cipher.DECRYPT_MODE, keySpec);

            return new String(
                    des.doFinal(Base64.getDecoder().decode(obj.getBytes(StandardCharsets.UTF_8))),
                    StandardCharsets.UTF_8
            );

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }
}
