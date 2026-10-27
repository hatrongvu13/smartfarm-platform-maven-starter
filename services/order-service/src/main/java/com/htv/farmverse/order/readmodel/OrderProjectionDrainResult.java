package com.htv.farmverse.order.readmodel;

public record OrderProjectionDrainResult(
        long previousVersion,
        long currentVersion,
        int appliedEvents,
        boolean gapRemaining
) { }
