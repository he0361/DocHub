package com.dochub.workbench.modelconfig.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelConfigPropertiesTest {

    @TempDir
    Path tempDirectory;

    @Test
    void developmentGeneratesAndReusesPersistentEncryptionKey() {
        String previousHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", tempDirectory.toString());

            ModelConfigProperties firstStart = new ModelConfigProperties();
            firstStart.validate(false);
            String firstKey = firstStart.getEncryptionKey();

            ModelConfigProperties secondStart = new ModelConfigProperties();
            secondStart.validate(false);
            String secondKey = secondStart.getEncryptionKey();

            assertNotNull(firstKey);
            assertEquals(32, Base64.getDecoder().decode(firstKey).length);
            assertEquals(firstKey, secondKey);
            assertArrayEquals(
                Base64.getDecoder().decode(firstKey),
                Base64.getDecoder().decode(secondKey)
            );
        } finally {
            if (previousHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", previousHome);
            }
        }
    }

    @Test
    void developmentRegeneratesCorruptPersistentKeyFile() throws Exception {
        String previousHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", tempDirectory.toString());
            Path keyFile = tempDirectory.resolve(".dochub/model-config-encryption.key");
            Files.createDirectories(keyFile.getParent());
            Files.writeString(keyFile, "not-a-valid-32-byte-base64-key");

            ModelConfigProperties properties = new ModelConfigProperties();
            properties.validate(false);

            assertNotNull(properties.getEncryptionKey());
            assertEquals(32, Base64.getDecoder().decode(properties.getEncryptionKey()).length);
            assertEquals(properties.getEncryptionKey(), Files.readString(keyFile).trim());
        } finally {
            if (previousHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", previousHome);
            }
        }
    }
}
