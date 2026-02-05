package health.zaed.identity.service;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import health.zaed.identity.config.TwoFactorConfig;
import health.zaed.identity.exception.TwoFactorException;
import health.zaed.identity.model.entity.User;
import health.zaed.identity.model.entity.User2FA;
import health.zaed.identity.model.enums.UserRole;
import health.zaed.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TwoFactorService}.
 *
 * <p>Tests TOTP setup, verification, and recovery code management.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TwoFactorService")
class TwoFactorServiceTest {

    @Mock
    private UserRepository userRepository;

    private TwoFactorConfig config;
    private PasswordEncoder passwordEncoder;
    private TwoFactorService twoFactorService;

    // For generating valid TOTP codes in tests
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1);

    @BeforeEach
    void setUp() {
        config = new TwoFactorConfig();
        config.setIssuer("TestApp");
        config.setBackupCodesCount(10);

        passwordEncoder = new BCryptPasswordEncoder();

        twoFactorService = new TwoFactorService(userRepository, passwordEncoder, config);
    }

    @Nested
    @DisplayName("initiate2FASetup")
    class Initiate2FASetup {

        @Test
        @DisplayName("should initiate 2FA setup and return secret with QR code")
        void shouldInitiate2FASetupAndReturnSecretWithQrCode() {
            User user = createAdminUser();
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            var response = twoFactorService.initiate2FASetup(user.getId());

            assertThat(response.secret()).isNotBlank();
            assertThat(response.secret()).hasSize(32); // Default secret length
            assertThat(response.qrCodeImage()).startsWith("data:image/png;base64,");
            assertThat(response.backupCodes()).hasSize(10);
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            UUID userId = UUID.randomUUID();
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> twoFactorService.initiate2FASetup(userId))
                .isInstanceOf(TwoFactorException.class)
                .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("should throw when 2FA already enabled")
        void shouldThrowWhen2FAAlreadyEnabled() {
            User user = createAdminUser();
            User2FA existing2FA = new User2FA();
            existing2FA.setEnabled(true);
            existing2FA.setSecret("existing-secret");
            user.setTwoFactorAuth(existing2FA);

            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> twoFactorService.initiate2FASetup(user.getId()))
                .isInstanceOf(TwoFactorException.class)
                .hasMessageContaining("already enabled");
        }

        @Test
        @DisplayName("should store hashed recovery codes")
        void shouldStoreHashedRecoveryCodes() {
            User user = createAdminUser();
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            when(userRepository.save(userCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

            var response = twoFactorService.initiate2FASetup(user.getId());

            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getTwoFactorAuth()).isNotNull();
            assertThat(savedUser.getTwoFactorAuth().getRecoveryCodes()).hasSize(10);

            // Verify codes are hashed (BCrypt hashes start with $2a$)
            savedUser.getTwoFactorAuth().getRecoveryCodes().forEach(code ->
                assertThat(code).startsWith("$2a$")
            );

            // Verify recovery codes in response are plaintext
            response.backupCodes().forEach(code ->
                assertThat(code).doesNotStartWith("$2a$")
            );
        }
    }

    @Nested
    @DisplayName("confirm2FASetup")
    class Confirm2FASetup {

        @Test
        @DisplayName("should enable 2FA when valid code provided")
        void shouldEnable2FAWhenValidCodeProvided() throws Exception {
            String secret = secretGenerator.generate();
            User user = createAdminUserWith2FASetup(secret);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            // Generate valid TOTP code
            String validCode = codeGenerator.generate(secret, Math.floorDiv(System.currentTimeMillis(), 30000));

            twoFactorService.confirm2FASetup(user.getId(), validCode);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getTwoFactorAuth().isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should throw when 2FA not initiated")
        void shouldThrowWhen2FANotInitiated() {
            User user = createAdminUser();
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> twoFactorService.confirm2FASetup(user.getId(), "123456"))
                .isInstanceOf(TwoFactorException.class)
                .hasMessageContaining("not been initiated");
        }

        @Test
        @DisplayName("should throw when invalid code provided")
        void shouldThrowWhenInvalidCodeProvided() {
            String secret = secretGenerator.generate();
            User user = createAdminUserWith2FASetup(secret);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> twoFactorService.confirm2FASetup(user.getId(), "000000"))
                .isInstanceOf(TwoFactorException.class)
                .hasMessageContaining("Invalid verification code");
        }
    }

    @Nested
    @DisplayName("verifyCode")
    class VerifyCode {

        @Test
        @DisplayName("should return true for valid TOTP code")
        void shouldReturnTrueForValidTotpCode() throws Exception {
            String secret = secretGenerator.generate();
            User user = createAdminUserWithEnabled2FA(secret);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            String validCode = codeGenerator.generate(secret, Math.floorDiv(System.currentTimeMillis(), 30000));

            boolean result = twoFactorService.verifyCode(user.getId(), validCode);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false for invalid TOTP code")
        void shouldReturnFalseForInvalidTotpCode() {
            String secret = secretGenerator.generate();
            User user = createAdminUserWithEnabled2FA(secret);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            boolean result = twoFactorService.verifyCode(user.getId(), "000000");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should throw when 2FA not enabled")
        void shouldThrowWhen2FANotEnabled() {
            User user = createAdminUser();
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> twoFactorService.verifyCode(user.getId(), "123456"))
                .isInstanceOf(TwoFactorException.class)
                .hasMessageContaining("not enabled");
        }
    }

    @Nested
    @DisplayName("verifyRecoveryCode")
    class VerifyRecoveryCode {

        @Test
        @DisplayName("should return true and consume valid recovery code")
        void shouldReturnTrueAndConsumeValidRecoveryCode() {
            String secret = secretGenerator.generate();
            String recoveryCode = "ABCD-EFGH-1234";
            User user = createAdminUserWithEnabled2FAAndRecoveryCode(secret, recoveryCode);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            boolean result = twoFactorService.verifyRecoveryCode(user.getId(), recoveryCode);

            assertThat(result).isTrue();

            // Verify recovery code was removed
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getTwoFactorAuth().getRecoveryCodes()).isEmpty();
        }

        @Test
        @DisplayName("should return false for invalid recovery code")
        void shouldReturnFalseForInvalidRecoveryCode() {
            String secret = secretGenerator.generate();
            User user = createAdminUserWithEnabled2FA(secret);
            // Add a hashed recovery code
            user.getTwoFactorAuth().setRecoveryCodes(
                new ArrayList<>(List.of(passwordEncoder.encode("VALID-CODE")))
            );
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            boolean result = twoFactorService.verifyRecoveryCode(user.getId(), "WRONG-CODE");

            assertThat(result).isFalse();
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("disable2FA")
    class Disable2FA {

        @Test
        @DisplayName("should disable 2FA when valid code provided")
        void shouldDisable2FAWhenValidCodeProvided() throws Exception {
            String secret = secretGenerator.generate();
            User user = createAdminUserWithEnabled2FA(secret);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            String validCode = codeGenerator.generate(secret, Math.floorDiv(System.currentTimeMillis(), 30000));

            twoFactorService.disable2FA(user.getId(), validCode);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());

            User2FA twoFA = userCaptor.getValue().getTwoFactorAuth();
            assertThat(twoFA.isEnabled()).isFalse();
            assertThat(twoFA.getSecret()).isNull();
            assertThat(twoFA.getRecoveryCodes()).isEmpty();
        }

        @Test
        @DisplayName("should throw when invalid code provided")
        void shouldThrowWhenInvalidCodeProvided() {
            String secret = secretGenerator.generate();
            User user = createAdminUserWithEnabled2FA(secret);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> twoFactorService.disable2FA(user.getId(), "000000"))
                .isInstanceOf(TwoFactorException.class)
                .hasMessageContaining("Invalid verification code");
        }
    }

    @Nested
    @DisplayName("is2FAEnabled")
    class Is2FAEnabled {

        @Test
        @DisplayName("should return true when 2FA is enabled")
        void shouldReturnTrueWhen2FAIsEnabled() {
            User user = createAdminUserWithEnabled2FA("secret");
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            boolean result = twoFactorService.is2FAEnabled(user.getId());

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when 2FA is not enabled")
        void shouldReturnFalseWhen2FAIsNotEnabled() {
            User user = createAdminUser();
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            boolean result = twoFactorService.is2FAEnabled(user.getId());

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when user not found")
        void shouldReturnFalseWhenUserNotFound() {
            UUID userId = UUID.randomUUID();
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            boolean result = twoFactorService.is2FAEnabled(userId);

            assertThat(result).isFalse();
        }
    }

    // Helper methods

    private User createAdminUser() {
        return User.builder()
            .id(UUID.randomUUID())
            .email("admin@zaed.org")
            .name("Admin User")
            .role(UserRole.ADMIN)
            .active(true)
            .build();
    }

    private User createAdminUserWith2FASetup(String secret) {
        User user = createAdminUser();
        User2FA twoFA = new User2FA();
        twoFA.setUser(user);
        twoFA.setSecret(secret);
        twoFA.setEnabled(false);
        twoFA.setRecoveryCodes(new ArrayList<>());
        user.setTwoFactorAuth(twoFA);
        return user;
    }

    private User createAdminUserWithEnabled2FA(String secret) {
        User user = createAdminUser();
        User2FA twoFA = new User2FA();
        twoFA.setUser(user);
        twoFA.setSecret(secret);
        twoFA.setEnabled(true);
        twoFA.setRecoveryCodes(new ArrayList<>());
        user.setTwoFactorAuth(twoFA);
        return user;
    }

    private User createAdminUserWithEnabled2FAAndRecoveryCode(String secret, String recoveryCode) {
        User user = createAdminUserWithEnabled2FA(secret);
        user.getTwoFactorAuth().setRecoveryCodes(
            new ArrayList<>(List.of(passwordEncoder.encode(recoveryCode)))
        );
        return user;
    }
}
