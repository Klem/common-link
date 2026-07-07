package org.commonlink.dto

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import kotlin.reflect.KClass

/**
 * Request body for creating a new payee under the authenticated association.
 *
 * [payeeType] controls which fields are required:
 * - COMPANY: [identifier1] is mandatory (9-digit SIREN).
 * - PERSON: [identifier1] must be absent; [name] holds "Prénom Nom" composed on the frontend.
 *
 * Server-side validation mirrors the frontend to ensure every click is replayable as a raw API call.
 */
@ValidPayeeRequest
data class CreatePayeeRequest(
    @field:NotBlank
    val name: String,

    /** COMPANY or PERSON. Defaults to COMPANY for backward compatibility. */
    val payeeType: String = "COMPANY",

    @field:Pattern(regexp = "^[0-9]{9}$", message = "identifier1 must be exactly 9 digits")
    val identifier1: String? = null,

    @field:Pattern(regexp = "^[0-9]{14}$", message = "identifier2 must be exactly 14 digits")
    val identifier2: String? = null,

    @field:Size(max = 10)
    val activityCode: String? = null,

    @field:Size(max = 100)
    val category: String? = null,

    @field:Size(max = 255)
    val city: String? = null,

    @field:Size(max = 10)
    val postalCode: String? = null,

    val active: Boolean? = true
)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [PayeeRequestValidator::class])
annotation class ValidPayeeRequest(
    val message: String = "COMPANY payees require a valid identifier1 (SIREN); PERSON payees must not provide one",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class PayeeRequestValidator : ConstraintValidator<ValidPayeeRequest, CreatePayeeRequest> {
    override fun isValid(req: CreatePayeeRequest, ctx: ConstraintValidatorContext): Boolean {
        return when (req.payeeType) {
            "COMPANY" -> !req.identifier1.isNullOrBlank()
            "PERSON"  -> req.identifier1 == null
            else      -> false
        }
    }
}
