package health.zaed.identity.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import health.zaed.identity.model.dto.OtpSendRequest;
import health.zaed.identity.model.dto.OtpSendResponse;
import health.zaed.identity.model.dto.OtpVerifyRequest;
import health.zaed.identity.model.dto.OtpVerifyResponse;
import health.zaed.identity.model.enums.OtpContext;
import health.zaed.identity.service.AuthService;
import health.zaed.identity.service.OtpService;

/**
 * REST controller for OTP-based authentication.
 *
 * <p>Used by public users (donors/requesters) for phone verification.
 */
@RestController
@RequestMapping("/api/{version}/auth/otp")
public class OtpController {

    private static final Logger log = LoggerFactory.getLogger(OtpController.class);

    private final OtpService otpService;
    private final AuthService authService;

    public OtpController(OtpService otpService, AuthService authService) {
        this.otpService = otpService;
        this.authService = authService;
    }

    @PostMapping(path = "/send", version = "1")
    public ResponseEntity<OtpSendResponse> sendOtp(
        @Valid @RequestBody OtpSendRequest request,
        HttpServletRequest httpRequest
    ) {
        log.debug("OTP send request for context: {}, referenceId: {}",
            request.context(), request.referenceId());

        OtpContext context = OtpContext.valueOf(request.context().toUpperCase());
        String channel = request.channel() != null ? request.channel() : "SMS";

        int expiresIn = otpService.sendOtp(
            request.phone(),
            channel,
            context,
            request.referenceId()
        );

        int retryAfter = otpService.getRetryAfter(request.phone());
        if (retryAfter == 0) {
            retryAfter = 60;
        }

        return ResponseEntity.ok(new OtpSendResponse(
            "OTP sent successfully",
            expiresIn,
            retryAfter,
            maskPhone(request.phone())
        ));
    }

    @PostMapping(path = "/verify", version = "1")
    public ResponseEntity<OtpVerifyResponse> verifyOtp(
        @Valid @RequestBody OtpVerifyRequest request,
        HttpServletRequest httpRequest
    ) {
        log.debug("OTP verify request for context: {}, referenceId: {}",
            request.context(), request.referenceId());

        OtpContext context = OtpContext.valueOf(request.context().toUpperCase());

        otpService.verifyOtp(
            request.phone(),
            request.otp(),
            context,
            request.referenceId()
        );

        String tempToken = authService.generateTempToken(
            request.phone(),
            context,
            request.referenceId(),
            request.trackingCode()
        );

        return ResponseEntity.ok(new OtpVerifyResponse(
            true,
            tempToken,
            900,
            "Bearer"
        ));
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 8) return "****";
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 2);
    }
}
