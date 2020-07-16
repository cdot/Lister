package com.cdot.crypto;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class AESUT {

    private byte[] loadTestResource(String name) {
        ClassLoader classLoader = getClass().getClassLoader();
        // load from src/test/resources
        InputStream in = classLoader.getResourceAsStream(name);
        assertNotNull(in);
        ByteArrayOutputStream ouch = new ByteArrayOutputStream();
        int ch;
        try {
            while ((ch = in.read()) != -1)
                ouch.write(ch);
        } catch (IOException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        return ouch.toByteArray();
    }

    private File writeTempFile(String name, byte[] data) {
        try {
            File tmpFile = File.createTempFile(name, "tmp");
            tmpFile.deleteOnExit();
            OutputStream fos = new FileOutputStream(tmpFile);
            fos.write(data);
            fos.close();
            return tmpFile;
        } catch (IOException e) {
            e.printStackTrace();
            fail(e.getMessage());
            return null;
        }
    }

    private byte[] readTempFile(File file) {
        ByteArrayOutputStream ouch = new ByteArrayOutputStream();
        try {
            InputStream in = new FileInputStream(file);
            int ch;
            while ((ch = in.read()) != -1)
                ouch.write(ch);
        } catch (IOException e) {
            e.printStackTrace();
            fail(e.getMessage());
            return null;
        }
        return ouch.toByteArray();
    }

    private void encrypt_decrypt_bytes(Aes enc, Aes dec, String pass) {
        byte[] ab = new byte[256];
        for (int i = 0; i < 256; i++)
            ab[i] = (byte) i;
        byte[] cipher = enc.encrypt(ab, pass, 128);
        byte[] decipher = dec.decrypt(cipher, pass, 128);
        assertEquals(ab.length, decipher.length);
        for (int i = 0; i < ab.length; i++)
            assertEquals(ab[i], decipher[i]);
    }

    private void encrypt_decrypt_string(Aes enc, Aes dec, String pass) {
        String plain = "North △ West ◁ South ▽ East ▷";
        String cipher = enc.encrypt(plain, pass, 128);
        String decipher = dec.decrypt(cipher, pass, 128);
        assertEquals(plain, decipher);
    }

    private void encrypt_decrypt_large(Aes enc, Aes dec, String pass) {
        int KEY_SIZE_BITS = 256;
        byte[] plaintext = loadTestResource("large.json");

        byte[] ciphertext = enc.encrypt(plaintext, pass, KEY_SIZE_BITS);
        File tf = writeTempFile("large", ciphertext);

        ciphertext = readTempFile(tf);
        byte[] nplaintext = dec.decrypt(ciphertext, pass, KEY_SIZE_BITS);

        assertEquals(plaintext.length, nplaintext.length);
        for (int i = 0; i < nplaintext.length; i++)
            assertEquals("At " + i, plaintext[i], nplaintext[i]);
    }

    private void run_tests(Aes enc, Aes dec, String pass) {
        encrypt_decrypt_bytes(enc, dec, pass);
        encrypt_decrypt_string(enc, dec, pass);
        encrypt_decrypt_large(enc, dec, pass);
    }

    @Test
    public void V_V() {
        run_tests(new AES_V(), new AES_V(), "");
    }

    @Test
    public void SYS_SYS() {
        run_tests(new AES_SYS(), new AES_SYS(), "Longer than 16 chars pass to make sure truncation works right");
    }

    @Test
    public void SYS_V() {
        run_tests(new AES_SYS(), new AES_V(), "North △ West ◁ South ▽ East ▷");
    }

    @Test
    public void V_SYS() {
        run_tests(new AES_V(), new AES_SYS(), "$ecret");
    }
}
