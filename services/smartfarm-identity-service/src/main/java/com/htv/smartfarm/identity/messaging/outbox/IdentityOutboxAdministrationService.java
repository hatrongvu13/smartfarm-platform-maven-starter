package com.htv.smartfarm.identity.messaging.outbox;

import org.springframework.stereotype.Service;

@Service
public class IdentityOutboxAdministrationService {

    private final IdentityOutboxTransactionService transactions;

    public IdentityOutboxAdministrationService(IdentityOutboxTransactionService transactions) {
        this.transactions = transactions;
    }

    public boolean retryDeadEvent(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        return transactions.retryDead(eventId.trim());
    }
}
