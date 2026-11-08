package org.triada.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;
import org.triada.core.Machine;
import org.triada.core.io.IoRegisters;
import org.triada.core.memory.MemoryMap;

/** Keyboard → TRIADA input registers, checked through the bus at the spec's addresses. */
class InputMapperTest {

    private final Machine machine = new Machine();
    private final InputMapper mapper = new InputMapper(machine.input());

    private int reg(int k) {
        return machine.bus().read(MemoryMap.IO_BASE + k);
    }

    @Test
    void arrowsAreTheJoystick() {
        mapper.keyDown(Input.Keys.LEFT);
        assertEquals(-1, reg(IoRegisters.JOY_X));
        mapper.keyDown(Input.Keys.RIGHT);
        assertEquals(0, reg(IoRegisters.JOY_X), "left + right cancel out");
        mapper.keyUp(Input.Keys.LEFT);
        assertEquals(1, reg(IoRegisters.JOY_X));
        mapper.keyDown(Input.Keys.UP);
        assertEquals(-1, reg(IoRegisters.JOY_Y), "up is -1 (SPEC §5)");
        mapper.keyUp(Input.Keys.UP);
        mapper.keyDown(Input.Keys.DOWN);
        assertEquals(1, reg(IoRegisters.JOY_Y));
        mapper.keyUp(Input.Keys.DOWN);
        mapper.keyUp(Input.Keys.RIGHT);
        assertEquals(0, reg(IoRegisters.JOY_X));
        assertEquals(0, reg(IoRegisters.JOY_Y));
    }

    @Test
    void zxcAreTheButtons() {
        mapper.keyDown(Input.Keys.Z);
        mapper.keyDown(Input.Keys.C);
        assertEquals(1, reg(IoRegisters.BTN_A));
        assertEquals(0, reg(IoRegisters.BTN_B));
        assertEquals(1, reg(IoRegisters.BTN_C));
        mapper.keyUp(Input.Keys.Z);
        mapper.keyDown(Input.Keys.X);
        assertEquals(0, reg(IoRegisters.BTN_A));
        assertEquals(1, reg(IoRegisters.BTN_B));
    }

    @Test
    void typedCharactersGoToTheKeyQueueInOrder() {
        for (char c : "Hi!".toCharArray()) {
            assertTrue(mapper.keyTyped(c));
        }
        assertEquals('H', reg(IoRegisters.KEY));
        assertEquals('i', reg(IoRegisters.KEY));
        assertEquals('!', reg(IoRegisters.KEY));
        assertEquals(0, reg(IoRegisters.KEY));
    }

    @Test
    void specialKeysHaveTriadaCodes() {
        assertEquals(10, InputMapper.keyCode('\r'), "Enter (Windows sends \\r)");
        assertEquals(10, InputMapper.keyCode('\n'), "Enter");
        assertEquals(8, InputMapper.keyCode('\b'));
        assertEquals(9, InputMapper.keyCode('\t'));
        assertEquals(27, InputMapper.keyCode((char) 27));
        assertEquals(0, InputMapper.keyCode((char) 127), "Delete is ignored");
        assertEquals(0, InputMapper.keyCode('é'), "no such character in the TRIADA font");
        assertFalse(mapper.keyTyped('é'));
        assertEquals(0, machine.input().queuedKeys());
    }

    @Test
    void otherKeysAreNotOurs() {
        assertFalse(mapper.keyDown(Input.Keys.F1));
        assertFalse(mapper.keyUp(Input.Keys.A), "letters only arrive as typed characters");
    }

    @Test
    void losingFocusReleasesEverything() {
        mapper.keyDown(Input.Keys.RIGHT);
        mapper.keyDown(Input.Keys.UP);
        mapper.keyDown(Input.Keys.X);
        mapper.releaseAll();
        assertEquals(0, reg(IoRegisters.JOY_X));
        assertEquals(0, reg(IoRegisters.JOY_Y));
        assertEquals(0, reg(IoRegisters.BTN_B));
        mapper.keyDown(Input.Keys.LEFT);
        assertEquals(-1, reg(IoRegisters.JOY_X), "the old RIGHT press doesn't linger");
    }
}
