package com.tkisor.nekojs.network.dto;

public record ErrorSummaryDTO(
        String id,
        String path,
        int line,
        int count,
        String message,
        String fullDetails
) {}