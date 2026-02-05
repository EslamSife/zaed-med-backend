package health.zaed.identity.model.enums;

import java.util.Set;

/**
 * Fine-grained permissions for authorization.
 *
 * <p>Permissions are grouped by domain:
 * <ul>
 *   <li>DONATION_* - Donation management</li>
 *   <li>REQUEST_* - Request management</li>
 *   <li>MATCH_* - Match operations</li>
 *   <li>PARTNER_* - Partner management</li>
 *   <li>ADMIN_* - Administrative functions</li>
 * </ul>
 */
public enum Permission {

    // Donation permissions
    DONATION_CREATE,
    DONATION_VIEW_OWN,
    DONATION_UPLOAD_IMAGE,
    DONATION_VIEW_ALL,
    DONATION_VERIFY,
    DONATION_REJECT,

    // Request permissions
    REQUEST_CREATE,
    REQUEST_VIEW_OWN,
    REQUEST_VIEW_ALL,

    // Match permissions
    MATCH_VIEW_ASSIGNED,
    MATCH_VIEW_ALL,
    MATCH_UPDATE_STATUS,
    MATCH_CONFIRM_PICKUP,
    MATCH_CONFIRM_DELIVERY,

    // Partner permissions
    PARTNER_DASHBOARD_VIEW,
    PARTNER_MANAGE,
    PARTNER_VERIFY,

    // Admin permissions
    ADMIN_DASHBOARD_VIEW,
    REPORTS_VIEW,
    SETTINGS_MANAGE,
    USERS_MANAGE;

    /**
     * Gets permissions for a given role.
     *
     * @param role the user role
     * @return set of permissions for the role
     */
    public static Set<Permission> getPermissionsForRole(UserRole role) {
        return switch (role) {
            case DONOR -> Set.of(
                DONATION_CREATE,
                DONATION_UPLOAD_IMAGE,
                DONATION_VIEW_OWN
            );
            case REQUESTER -> Set.of(
                REQUEST_CREATE,
                REQUEST_VIEW_OWN
            );
            case PARTNER_PHARMACY, PARTNER_NGO, PARTNER_VOLUNTEER -> Set.of(
                PARTNER_DASHBOARD_VIEW,
                MATCH_VIEW_ASSIGNED,
                MATCH_UPDATE_STATUS,
                MATCH_CONFIRM_PICKUP,
                MATCH_CONFIRM_DELIVERY
            );
            case ADMIN -> Set.of(Permission.values()); // All permissions
        };
    }
}
