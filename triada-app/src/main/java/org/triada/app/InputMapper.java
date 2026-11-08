package org.triada.app;

import com.badlogic.gdx.Input;
import org.triada.core.io.InputDevice;

/**
 * Translates PC keyboard events into the TRIADA's input registers
 * (CLAUDE.md M7, SPEC §5):
 * <ul>
 * <li>typed characters go into the KEY queue (DECISIONS.md D-024 for Enter, Backspace …);</li>
 * <li>the arrow keys are the joystick: JOY_X = right − left, JOY_Y = down − up;</li>
 * <li>Z, X and C are the fire buttons A, B and C.</li>
 * </ul>
 * A letter key like Z does both: its character goes to the KEY queue and it
 * presses button A. Programs use whichever they need.
 *
 * <p>This class has no libGDX window or listener of its own; the app forwards
 * events to it. That keeps it testable without opening a window.
 */
public final class InputMapper {

    private final InputDevice input;
    private boolean left;
    private boolean right;
    private boolean up;
    private boolean down;

    public InputMapper(InputDevice input) {
        this.input = input;
    }

    /** A key went down; returns true if it is one of ours. */
    public boolean keyDown(int keycode) {
        return setKey(keycode, true);
    }

    /** A key went up; returns true if it is one of ours. */
    public boolean keyUp(int keycode) {
        return setKey(keycode, false);
    }

    private boolean setKey(int keycode, boolean pressed) {
        switch (keycode) {
            case Input.Keys.LEFT:
                left = pressed;
                break;
            case Input.Keys.RIGHT:
                right = pressed;
                break;
            case Input.Keys.UP:
                up = pressed;
                break;
            case Input.Keys.DOWN:
                down = pressed;
                break;
            case Input.Keys.Z:
                input.setButtonA(pressed);
                return true;
            case Input.Keys.X:
                input.setButtonB(pressed);
                return true;
            case Input.Keys.C:
                input.setButtonC(pressed);
                return true;
            case Input.Keys.ESCAPE:
                // Neither libGDX backend turns Esc into a typed character, so it
                // goes into the KEY queue here (code 27, D-024): TRIBASIC's BREAK.
                if (pressed) {
                    input.pushKey(27);
                }
                return true;
            default:
                return false;
        }
        // Both directions held at once cancel out, like a real three-position stick.
        input.setJoyX((right ? 1 : 0) - (left ? 1 : 0));
        input.setJoyY((down ? 1 : 0) - (up ? 1 : 0));
        return true;
    }

    /** A character was typed; it goes into the KEY queue if it has a TRIADA key code. */
    public boolean keyTyped(char c) {
        int code = keyCode(c);
        if (code == 0) {
            return false;
        }
        input.pushKey(code);   // a full queue drops the key (D-007)
        return true;
    }

    /** The TRIADA key code for a typed character, or 0 if it has none (D-024). */
    static int keyCode(char c) {
        if (c >= 32 && c <= 126) {
            return c;                       // printable ASCII as itself
        }
        switch (c) {
            case '\r':
            case '\n':
                return 10;                  // Enter: newline, as DEBUG_CHAR prints it
            case '\b':
                return 8;                   // Backspace
            case '\t':
                return 9;                   // Tab
            case 27:
                return 27;                  // Escape
            default:
                return 0;                   // anything else (Delete, accented letters …) is ignored
        }
    }

    /** Lets go of everything, e.g. when the window loses focus and key-up events would be missed. */
    public void releaseAll() {
        left = false;
        right = false;
        up = false;
        down = false;
        input.setJoyX(0);
        input.setJoyY(0);
        input.setButtonA(false);
        input.setButtonB(false);
        input.setButtonC(false);
    }
}
