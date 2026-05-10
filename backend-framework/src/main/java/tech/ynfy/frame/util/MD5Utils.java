//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package tech.ynfy.frame.util;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5Utils {
    public MD5Utils() {
    }

    public static String stringToMD5(String plainText, int count) {
        String md = plainText;
        if (count > 0) {
            for(int i = 0; i < count; ++i) {
                md = stringToMD5(md);
            }
        }

        return md;
    }

    public static String stringToMD5(String plainText, String salt) {
        return stringToMD5(plainText + "&" + salt);
    }

    public static String stringToMD5(String plainText) {

        byte[] secretBytes;
        try {
            secretBytes = MessageDigest.getInstance("md5").digest(plainText.getBytes());
        } catch (NoSuchAlgorithmException var5) {
            throw new RuntimeException("没有这个md5算法！");
        }

        String md5code = (new BigInteger(1, secretBytes)).toString(16);
        int md5Length = md5code.length();

        for(int i = 0; i < 32 - md5Length; ++i) {
            md5code = "0" + md5code;
        }

        return md5code;
    }
}
