package org.triada.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * M0 smoke test. Its real job is to prove that the build compiles a module,
 * finds its tests and runs them with JUnit 5. Real machine tests start in M1.
 */
class MachineInfoTest {

    @Test
    void machineIsCalledTriada85() {
        assertEquals("TRIADA-85", MachineInfo.NAME);
    }

    @Test
    void implementsSpecVersion1_0() {
        assertEquals("1.0", MachineInfo.SPEC_VERSION);
    }
}
