package com.meloxu.cmanwp.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CmaNwpServiceTest {
    @Test
    void convertsIsobaricPressureFromPaToHpa() {
        assertEquals(500.0, CmaNwpService.normalizeLevel(100, 50_000.0));
        assertEquals(850.0, CmaNwpService.normalizeLevel(100, 850.0));
    }

    @Test
    void leavesNonPressureLevelsUnchanged() {
        assertEquals(2.0, CmaNwpService.normalizeLevel(103, 2.0));
    }
}
