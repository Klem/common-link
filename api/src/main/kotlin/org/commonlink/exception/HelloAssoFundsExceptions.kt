package org.commonlink.exception

/**
 * Refusals of the HelloAsso funds availability actions (transfer receipt confirmation, curator release), each with a
 * stable `code` property for client-side branching (HTTP 409). Conflicts, so callers handling a conflict keep doing so.
 */
sealed class HelloAssoFundsConflictException(message: String, val code: String) : ConflictException(message)

/**
 * "Transfer received" refused: at least one CommonLink donation of the payout is not paid out (`CashedOut`) in this
 * payout when re-read at HelloAsso. Nothing was changed.
 */
class CashOutNotCashedOutException(idCashOut: Long) :
    HelloAssoFundsConflictException("HelloAsso payout $idCashOut is not paid out for every donation", "CASH_OUT_NOT_CASHED_OUT")

/** Curator release refused: a donation is already available or withdrawn (refunded / contested). Nothing was changed. */
class FundsReleaseNotAllowedException(message: String) :
    HelloAssoFundsConflictException(message, "FUNDS_RELEASE_NOT_ALLOWED")
