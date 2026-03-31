package org.commonlink.entity

/**
 * Defines the two types of users on the platform.
 *
 * The role is embedded in the JWT (`role` claim) and used by Spring Security
 * as a granted authority (`ROLE_DONOR` / `ROLE_ASSOCIATION`) for route-level access control.
 */
enum class UserRole {
    /** A philanthropist who browses campaigns and makes donations. */
    DONOR,
    /** A non-profit organisation that creates and manages fundraising campaigns. */
    ASSOCIATION
}

/**
 * Tracks how the user's account was originally created.
 *
 * Primarily informational, but also drives UI decisions such as showing "set password"
 * prompts for users who registered via [GOOGLE] or [MAGIC_LINK] and have no password hash.
 */
enum class AuthProvider {
    /** Account created with email + password registration. */
    EMAIL,
    /** Account created via Google OAuth sign-up. */
    GOOGLE,
    /** Account created by clicking a one-time magic-link sent to the user's email. */
    MAGIC_LINK
}
