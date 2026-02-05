package health.zaed.identity.model.enums;

/**
 * Context for OTP verification - determines what actions are allowed after verification.
 */
public enum OtpContext {

    /**
     * OTP for donation submission - allows image upload.
     */
    DONATION,

    /**
     * OTP for medicine request submission.
     */
    REQUEST;

    /**
     * Gets the permissions granted after OTP verification in this context.
     */
    public Permission[] getGrantedPermissions() {
        return switch (this) {
            case DONATION -> new Permission[]{
                Permission.DONATION_UPLOAD_IMAGE,
                Permission.DONATION_VIEW_OWN
            };
            case REQUEST -> new Permission[]{
                Permission.REQUEST_VIEW_OWN
            };
        };
    }
}
