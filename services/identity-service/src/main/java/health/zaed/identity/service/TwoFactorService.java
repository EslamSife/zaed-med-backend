package health.zaed.identity.service;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.recovery.RecoveryCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import health.zaed.identity.config.TwoFactorConfig;
import health.zaed.identity.exception.TwoFactorException;
import health.zaed.identity.model.dto.TwoFactorSetupResponse;
import health.zaed.identity.model.entity.User;
import health.zaed.identity.model.entity.User2FA;
import health.zaed.identity.repository.UserRepository;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Service for TOTP-based two-factor authentication.
 *
 * <p>Used by admin accounts to add an extra layer of security.
 */
@Service
public class TwoFactorService {

    private static final Logger log = LoggerFactory.getLogger(TwoFactorService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TwoFactorConfig config;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1);
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, new SystemTimeProvider());
    private final RecoveryCodeGenerator recoveryCodeGenerator = new RecoveryCodeGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();

    public TwoFactorService(UserRepository userRepository, PasswordEncoder passwordEncoder, TwoFactorConfig config) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.config = config;
    }

    @Transactional
    public TwoFactorSetupResponse initiate2FASetup(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new TwoFactorException("User not found"));

        if (user.getTwoFactorAuth() != null && user.getTwoFactorAuth().isEnabled()) {
            throw new TwoFactorException("2FA_ALREADY_ENABLED", "Two-factor authentication is already enabled");
        }

        String secret = secretGenerator.generate();

        String[] recoveryCodes = recoveryCodeGenerator.generateCodes(config.getRecoveryCodeCount());
        List<String> hashedRecoveryCodes = Arrays.stream(recoveryCodes).map(passwordEncoder::encode).toList();

        User2FA twoFA = user.getTwoFactorAuth();
        if (twoFA == null) {
            twoFA = new User2FA();
            twoFA.setUser(user);
            user.setTwoFactorAuth(twoFA);
        }
        twoFA.setSecret(secret);
        twoFA.setRecoveryCodes(hashedRecoveryCodes);
        twoFA.setEnabled(false);

        userRepository.save(user);

        String qrCodeDataUri = generateQrCodeDataUri(user.getEmail(), secret);

        log.info("2FA setup initiated for user: {}", userId);
        return new TwoFactorSetupResponse(secret, qrCodeDataUri, Arrays.asList(recoveryCodes));
    }

    @Transactional
    public void confirm2FASetup(UUID userId, String code) {
        User user = userRepository.findById(userId).orElseThrow(() -> new TwoFactorException("User not found"));

        User2FA twoFA = user.getTwoFactorAuth();
        if (twoFA == null || twoFA.getSecret() == null) {
            throw new TwoFactorException("2FA_NOT_INITIATED", "Two-factor setup has not been initiated");
        }

        if (twoFA.isEnabled()) {
            throw new TwoFactorException("2FA_ALREADY_ENABLED", "Two-factor authentication is already enabled");
        }

        if (!codeVerifier.isValidCode(twoFA.getSecret(), code)) {
            throw new TwoFactorException("INVALID_CODE", "Invalid verification code");
        }

        twoFA.setEnabled(true);
        userRepository.save(user);

        log.info("2FA enabled for user: {}", userId);
    }

    public boolean verifyCode(UUID userId, String code) {
        User user = userRepository.findById(userId).orElseThrow(() -> new TwoFactorException("User not found"));

        User2FA twoFA = user.getTwoFactorAuth();
        if (twoFA == null || !twoFA.isEnabled()) {
            throw new TwoFactorException("2FA_NOT_ENABLED", "Two-factor authentication is not enabled");
        }

        return codeVerifier.isValidCode(twoFA.getSecret(), code);
    }

    @Transactional
    public boolean verifyRecoveryCode(UUID userId, String recoveryCode) {
        User user = userRepository.findById(userId).orElseThrow(() -> new TwoFactorException("User not found"));

        User2FA twoFA = user.getTwoFactorAuth();
        if (twoFA == null || !twoFA.isEnabled()) {
            throw new TwoFactorException("2FA_NOT_ENABLED", "Two-factor authentication is not enabled");
        }

        List<String> hashedCodes = twoFA.getRecoveryCodes();
        for (int i = 0; i < hashedCodes.size(); i++) {
            if (passwordEncoder.matches(recoveryCode, hashedCodes.get(i))) {
                hashedCodes.remove(i);
                twoFA.setRecoveryCodes(hashedCodes);
                userRepository.save(user);
                log.info("Recovery code used for user: {}, {} codes remaining", userId, hashedCodes.size());
                return true;
            }
        }
        return false;
    }

    @Transactional
    public void disable2FA(UUID userId, String code) {
        User user = userRepository.findById(userId).orElseThrow(() -> new TwoFactorException("User not found"));

        User2FA twoFA = user.getTwoFactorAuth();
        if (twoFA == null || !twoFA.isEnabled()) {
            throw new TwoFactorException("2FA_NOT_ENABLED", "Two-factor authentication is not enabled");
        }

        if (!codeVerifier.isValidCode(twoFA.getSecret(), code)) {
            throw new TwoFactorException("INVALID_CODE", "Invalid verification code");
        }

        twoFA.setEnabled(false);
        twoFA.setSecret(null);
        twoFA.setRecoveryCodes(null);
        userRepository.save(user);

        log.info("2FA disabled for user: {}", userId);
    }

    @Transactional
    public List<String> regenerateRecoveryCodes(UUID userId, String code) {
        User user = userRepository.findById(userId).orElseThrow(() -> new TwoFactorException("User not found"));

        User2FA twoFA = user.getTwoFactorAuth();
        if (twoFA == null || !twoFA.isEnabled()) {
            throw new TwoFactorException("2FA_NOT_ENABLED", "Two-factor authentication is not enabled");
        }

        if (!codeVerifier.isValidCode(twoFA.getSecret(), code)) {
            throw new TwoFactorException("INVALID_CODE", "Invalid verification code");
        }

        String[] newCodes = recoveryCodeGenerator.generateCodes(config.getRecoveryCodeCount());
        List<String> hashedCodes = Arrays.stream(newCodes).map(passwordEncoder::encode).toList();

        twoFA.setRecoveryCodes(hashedCodes);
        userRepository.save(user);

        log.info("Recovery codes regenerated for user: {}", userId);
        return Arrays.asList(newCodes);
    }

    public boolean is2FAEnabled(UUID userId) {
        return userRepository.findById(userId).map(user -> user.getTwoFactorAuth() != null && user.getTwoFactorAuth().isEnabled()).orElse(false);
    }

    private String generateQrCodeDataUri(String email, String secret) {
        try {
            QrData data = new QrData.Builder().label(email).secret(secret).issuer(config.getIssuer()).algorithm(HashingAlgorithm.SHA1).digits(6).period(30).build();

            byte[] imageData = qrGenerator.generate(data);
            String mimeType = qrGenerator.getImageMimeType();
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageData);

        } catch (Exception e) {
            log.error("Failed to generate QR code: {}", e.getMessage());
            throw new TwoFactorException("Failed to generate QR code");
        }
    }
}
