package org.triada.net;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashing for TRIADA-NET accounts (NET-SPEC §5, M18), with the
 * JDK's PBKDF2 and no extra library. A password is never stored: only a
 * random salt and the result of running it through PBKDF2-HMAC-SHA256 many
 * thousand times, so a stolen database is slow to attack. Checking compares
 * in constant time, so the time taken doesn't reveal how much matched.
 *
 * <pre>
 *   pbkdf2-sha256$120000$&lt;salt, base64&gt;$&lt;hash, base64&gt;
 * </pre>
 */
public final class Passwords {

    public static final int DEFAULT_ITERATIONS = 120_000;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private final int iterations;
    private final SecureRandom random = new SecureRandom();
    /** Checked against when a handle doesn't exist, so that takes as long as a real check. */
    private final String dummy;

    /** Hashing with the default strength. */
    public Passwords() {
        this(DEFAULT_ITERATIONS);
    }

    /** Hashing with {@code iterations} rounds (tests use few, to stay fast). */
    public Passwords(int iterations) {
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations must be positive");
        }
        this.iterations = iterations;
        this.dummy = hash("no such caller");
    }

    /** A new salted hash of {@code password}, to store. */
    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        Base64.Encoder b64 = Base64.getEncoder();
        return "pbkdf2-sha256$" + iterations + "$" + b64.encodeToString(salt) + "$"
                + b64.encodeToString(pbkdf2(password, salt, iterations));
    }

    /** True if {@code password} matches {@code stored} (from {@link #hash}); a broken {@code stored} never matches. */
    public boolean verify(String password, String stored) {
        String[] p = stored == null ? new String[0] : stored.split("\\$");
        if (p.length != 4 || !p[0].equals("pbkdf2-sha256")) {
            return false;
        }
        try {
            int rounds = Integer.parseInt(p[1]);
            byte[] salt = Base64.getDecoder().decode(p[2]);
            byte[] expected = Base64.getDecoder().decode(p[3]);
            return MessageDigest.isEqual(expected, pbkdf2(password, salt, rounds));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Spends the time of a real check, for a handle that doesn't exist; always false. */
    public boolean verifyNobody(String password) {
        verify(password, dummy);
        return false;
    }

    private static byte[] pbkdf2(String password, byte[] salt, int rounds) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, rounds, HASH_BITS);
            try {
                return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("the JDK has no " + ALGORITHM, e);
        }
    }

}
