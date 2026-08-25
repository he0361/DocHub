package com.dochub.workbench.auth.support;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 文枢 DocHub 账号密码哈希器。
 *
 * <p>格式：{@code salt$hex(sha256(salt + password))}，salt 随机生成并随哈希一并存储，
 * 校验时从存储值中取出 salt 重算比对。相比明文存储更安全，且不引入额外依赖。</p>
 */
@Component
public class PasswordHasher {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SALT_BYTES = 8;

    public String hash(String rawPassword) {
        if (rawPassword == null) {
            rawPassword = "";
        }
        byte[] saltBytes = new byte[SALT_BYTES];
        RANDOM.nextBytes(saltBytes);
        String salt = HexFormat.of().formatHex(saltBytes);
        return salt + "$" + sha256Hex(salt + rawPassword);
    }

    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) {
            return false;
        }
        int separator = storedHash.indexOf('$');
        if (separator <= 0) {
            return false;
        }
        String salt = storedHash.substring(0, separator);
        String expected = storedHash.substring(separator + 1);
        return sha256Hex(salt + rawPassword).equals(expected);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
