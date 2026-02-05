package health.zaed.identity.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import health.zaed.identity.model.dto.TwoFactorSetupResponse;
import health.zaed.identity.security.AuthPrincipal;
import health.zaed.identity.service.TwoFactorService;

import java.util.List;
import java.util.Map;

/**
 * REST controller for 2FA management.
 *
 * <p>Allows partner/admin users to set up and manage TOTP-based 2FA.
 */
@RestController
@RequestMapping("/api/{version}/auth/2fa")
public class TwoFactorController {

  private static final Logger log = LoggerFactory.getLogger(TwoFactorController.class);

  private final TwoFactorService twoFactorService;

  public TwoFactorController(TwoFactorService twoFactorService) {
    this.twoFactorService = twoFactorService;
  }

  @GetMapping(path = "/status", version = "1")
  public ResponseEntity<Map<String, Object>> getStatus(@AuthenticationPrincipal AuthPrincipal principal) {
    boolean enabled = twoFactorService.is2FAEnabled(principal.getUserId());
    return ResponseEntity.ok(Map.of("enabled", enabled));
  }

  @PostMapping(path = "/setup", version = "1")
  public ResponseEntity<TwoFactorSetupResponse> initiateSetup(@AuthenticationPrincipal AuthPrincipal principal) {
    log.info("2FA setup initiated for user: {}", principal.getUserId());
    TwoFactorSetupResponse response = twoFactorService.initiate2FASetup(principal.getUserId());
    return ResponseEntity.ok(response);
  }

  @PostMapping(path = "/confirm", version = "1")
  public ResponseEntity<Map<String, String>> confirmSetup(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody ConfirmRequest request) {
    twoFactorService.confirm2FASetup(principal.getUserId(), request.code());
    log.info("2FA enabled for user: {}", principal.getUserId());
    return ResponseEntity.ok(Map.of("message", "Two-factor authentication enabled successfully"));
  }

  @DeleteMapping(version = "1")
  public ResponseEntity<Map<String, String>> disable2FA(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody ConfirmRequest request) {
    twoFactorService.disable2FA(principal.getUserId(), request.code());
    log.info("2FA disabled for user: {}", principal.getUserId());
    return ResponseEntity.ok(Map.of("message", "Two-factor authentication disabled successfully"));
  }

  @PostMapping(path = "/recovery-codes/regenerate", version = "1")
  public ResponseEntity<Map<String, List<String>>> regenerateRecoveryCodes(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody ConfirmRequest request) {
    List<String> codes = twoFactorService.regenerateRecoveryCodes(principal.getUserId(), request.code());
    log.info("Recovery codes regenerated for user: {}", principal.getUserId());
    return ResponseEntity.ok(Map.of("recoveryCodes", codes));
  }

  public record ConfirmRequest(
          @NotBlank(message = "Verification code is required") @Pattern(regexp = "^\\d{6}$", message = "Code must be 6 digits") String code) {
  }
}
