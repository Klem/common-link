package org.commonlink.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import org.commonlink.entity.UserRole
import kotlin.reflect.KClass

/**
 * Constrains a [UserRole] field of a public sign-up DTO to a role a caller may grant themselves,
 * i.e. [UserRole.SELF_ASSIGNABLE] — `DONOR` or `ASSOCIATION`.
 *
 * Applied to the three unauthenticated entry points that accept a role in their body:
 * `POST /api/auth/register`, `POST /api/auth/magic-link/request` and `POST /api/auth/signup/google`.
 * Without it, `role: "COMPLIANCE_OFFICER"` in a registration payload yielded a JWT carrying
 * `ROLE_COMPLIANCE_OFFICER` and full access to the compliance back-office
 * (security audit 2026-08-20, C1).
 *
 * A `null` role is accepted: it means "existing account, role read from the database" on the
 * magic-link and Google login flows. `@NotNull` remains the annotation that makes it mandatory
 * where it is.
 *
 * This is the first of two layers. The service-side guard in
 * [org.commonlink.service.AuthService] is the second, and is the one that holds when a role
 * reaches persistence through a path that never sees this DTO (a magic-link token row is written
 * on request and re-read on verification).
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [SelfAssignableRoleValidator::class])
annotation class SelfAssignableRole(
    val message: String = "role must be one of DONOR, ASSOCIATION",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

/**
 * Validator backing [SelfAssignableRole]. Accepts `null` and any member of
 * [UserRole.SELF_ASSIGNABLE]; rejects every back-office role.
 */
class SelfAssignableRoleValidator : ConstraintValidator<SelfAssignableRole, UserRole> {

    /**
     * @param value Role submitted by the caller, or `null` when the flow infers it from an
     *   existing account.
     * @return `true` when the role may be self-assigned.
     */
    override fun isValid(value: UserRole?, context: ConstraintValidatorContext): Boolean =
        value == null || value in UserRole.SELF_ASSIGNABLE
}
