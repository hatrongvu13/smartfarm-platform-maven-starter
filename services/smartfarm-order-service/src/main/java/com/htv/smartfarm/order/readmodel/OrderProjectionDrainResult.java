package com.htv.smartfarm.order.readmodel;

public record OrderProjectionDrainResult(
        long previousVersion,
        long currentVersion,
        int appliedEvents,
        boolean gapRemaining
) { }
