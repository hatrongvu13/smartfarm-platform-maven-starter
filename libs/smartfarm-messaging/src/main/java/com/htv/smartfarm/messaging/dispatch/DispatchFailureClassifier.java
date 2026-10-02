package com.htv.smartfarm.messaging.dispatch;

/**
 * Strategy for turning a thrown error into a {@link DispatchFailureDecision}. Each service supplies
 * its own instance (or extends {@link DefaultDispatchFailureClassifier}) so that service-specific
 * exceptions map to the right generic category without this library knowing those exception types.
 */
@FunctionalInterface
public interface DispatchFailureClassifier {
    DispatchFailureDecision classify(Throwable error);
}
