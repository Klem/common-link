package org.commonlink.service

import org.commonlink.entity.HelloAssoLinkAttempt
import org.commonlink.repository.HelloAssoLinkAttemptRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Journals HelloAsso key checks ([HelloAssoLinkAttempt]) in their own transaction, so a refusal thrown right after
 * does not roll the row back. A separate bean on purpose: `REQUIRES_NEW` is ignored on a self-invoked method.
 */
@Service
class HelloAssoLinkAttemptRecorder(
    private val repository: HelloAssoLinkAttemptRepository,
) {
    /** Saves and commits [attempt]. Failures propagate: the caller decides whether they block. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(attempt: HelloAssoLinkAttempt) {
        repository.save(attempt)
    }
}
