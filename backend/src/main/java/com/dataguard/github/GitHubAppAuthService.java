package com.dataguard.github;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Base64;
import java.util.Date;

/**
 * Handles GitHub App JWT authentication using the configured App ID
 * and private key.
 */
@Service
public class GitHubAppAuthService {

    private static final Logger log = LoggerFactory.getLogger(GitHubAppAuthService.class);
    private static final long JWT_TTL_MILLIS = 10 * 60 * 1000; // 10 minutes max for GitHub Apps

    private final GitHubProperties properties;
    private PrivateKey privateKey;
    private boolean isConfigured = false;

    public GitHubAppAuthService(GitHubProperties properties) {
        this.properties = properties;
        initializePrivateKey();
    }

    private void initializePrivateKey() {
        if (!StringUtils.hasText(properties.getAppId()) || !StringUtils.hasText(properties.getPrivateKeyPath())) {
            log.info("GitHub App ID or private key path not configured. GitHub integration will be disabled.");
            return;
        }

        try {
            Path keyPath = Paths.get(properties.getPrivateKeyPath());
            if (!Files.exists(keyPath)) {
                log.warn("GitHub private key file not found at: {}. GitHub integration will be disabled.", keyPath.toAbsolutePath());
                return;
            }

            String keyContent = Files.readString(keyPath);
            this.privateKey = parsePemPrivateKey(keyContent);
            this.isConfigured = true;

            log.info("Successfully loaded GitHub App private key for App ID: {}", properties.getAppId());
        } catch (Exception e) {
            log.error("Failed to load GitHub private key from {}. GitHub integration will be disabled. Error: {}",
                    properties.getPrivateKeyPath(), e.getMessage());
        }
    }

    /**
     * Parses a PEM-encoded RSA private key, supporting both PKCS#8
     * ("-----BEGIN PRIVATE KEY-----") and PKCS#1
     * ("-----BEGIN RSA PRIVATE KEY-----") formats.
     */
    private PrivateKey parsePemPrivateKey(String pem) throws Exception {
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] encoded = Base64.getDecoder().decode(base64);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        if (isPkcs8(encoded)) {
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(encoded));
        }
        return keyFactory.generatePrivate(parsePkcs1(encoded));
    }

    /**
     * Distinguishes PKCS#8 (PrivateKeyInfo) from PKCS#1 (RSAPrivateKey).
     * After the version INTEGER, PKCS#8 contains a SEQUENCE
     * (the algorithm identifier) while PKCS#1 contains an INTEGER (the modulus).
     */
    private boolean isPkcs8(byte[] der) {
        try {
            DerParser parser = new DerParser(der);
            parser.readSequence(); // outer SEQUENCE
            parser.readInteger();  // version
            return parser.peekTag() == 0x30; // SEQUENCE tag => PKCS#8
        } catch (Exception e) {
            // Fall back to PKCS#8 handling if structure cannot be inspected.
            return true;
        }
    }

    /**
     * Extracts the RSA components from a PKCS#1 DER-encoded RSAPrivateKey and
     * builds an {@link RSAPrivateCrtKeySpec}.
     */
    private RSAPrivateCrtKeySpec parsePkcs1(byte[] der) throws Exception {
        DerParser parser = new DerParser(der);
        parser.readSequence();        // RSAPrivateKey SEQUENCE
        skipInteger(parser);          // version
        BigInteger modulus = parser.readInteger();
        BigInteger publicExponent = parser.readInteger();
        BigInteger privateExponent = parser.readInteger();
        BigInteger prime1 = parser.readInteger();
        BigInteger prime2 = parser.readInteger();
        BigInteger exponent1 = parser.readInteger();
        BigInteger exponent2 = parser.readInteger();
        BigInteger coefficient = parser.readInteger();
        return new RSAPrivateCrtKeySpec(modulus, publicExponent, privateExponent,
                prime1, prime2, exponent1, exponent2, coefficient);
    }

    private void skipInteger(DerParser parser) throws Exception {
        parser.readInteger();
    }

    /** Minimal DER TLV reader used to extract RSA key components. */
    private static final class DerParser {
        private final byte[] data;
        private int offset;

        DerParser(byte[] data) {
            this.data = data;
        }

        byte peekTag() {
            return data[offset];
        }

        void readSequence() throws Exception {
            if (data[offset] != 0x30) {
                throw new InvalidKeyException("Expected SEQUENCE in DER data");
            }
            offset++;
            readLength();
        }

        BigInteger readInteger() throws Exception {
            if (data[offset] != 0x02) {
                throw new InvalidKeyException("Expected INTEGER in DER data");
            }
            offset++;
            int length = readLength();
            byte[] value = new byte[length];
            System.arraycopy(data, offset, value, 0, length);
            offset += length;
            return new BigInteger(value);
        }

        int readLength() {
            int b = data[offset++] & 0xFF;
            if (b < 128) {
                return b;
            }
            int numBytes = b & 0x7F;
            int length = 0;
            for (int i = 0; i < numBytes; i++) {
                length = (length << 8) | (data[offset++] & 0xFF);
            }
            return length;
        }
    }

    /**
     * Checks if the GitHub App is fully configured and ready for authentication.
     */
    public boolean isConfigured() {
        return isConfigured;
    }

    /**
     * Generates a short-lived JWT used to authenticate as the GitHub App.
     *
     * @return The JWT token, or null if not configured.
     */
    public String generateAppJwt() {
        if (!isConfigured) {
            throw new IllegalStateException("GitHub App is not configured");
        }

        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date exp = new Date(nowMillis + JWT_TTL_MILLIS);

        return Jwts.builder()
                .setIssuer(properties.getAppId())
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }
}