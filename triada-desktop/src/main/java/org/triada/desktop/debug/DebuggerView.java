package org.triada.desktop.debug;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.List;
import java.util.Locale;

/**
 * The debugger's panels, built with libGDX scene2d.ui (CLAUDE.md M9). It only
 * <em>shows</em> the {@link Debugger}'s state and forwards clicks to it; all
 * decisions live in {@code Debugger}, where they are tested.
 *
 * <pre>
 *  +--------------------------+--------------------------+
 *  |  TRIADA screen (2x)      | TRIADA-85 DEBUGGER       |
 *  |                          | status                   |
 *  |                          | [RUN F5] [STEP F11] [OVER F10]
 *  |                          | R0 ... R8, SP, PC, flags |
 *  +--------------------------+ DISASSEMBLY  (click = breakpoint)
 *  | MEMORY [addr] [GO] [-][+]| >* 243 LDI R0, 5          |
 *  |  8 words, dec + ternary  | BREAK [addr] [SET/CLEAR]  |
 *  +--------------------------+--------------------------+
 * </pre>
 *
 * No skin files are needed: the skin is made in code from one white pixel
 * (tinted for every colour) and the TRIADA's own font ({@link TriadaFont}).
 */
public final class DebuggerView implements Disposable {

    /**
     * Width of the right-hand panel, in pixels. 464 leaves exactly 2 x 240 + 2 x 8
     * pixels for the screen in the default 960-pixel window.
     */
    public static final int PANEL_WIDTH = 464;
    /** Height reserved under the screen for the memory viewer. */
    public static final int MEMORY_HEIGHT = 230;
    private static final int DISASM_LINES = 11;

    private static final Color TEXT = new Color(0.5f, 1f, 1f, 1f);          // the TRIADA's light cyan
    private static final Color HEAD = new Color(1f, 1f, 0.5f, 1f);
    private static final Color PC_LINE = Color.WHITE;
    private static final Color BREAK_LINE = new Color(1f, 0.5f, 0.5f, 1f);
    private static final Color PANEL = new Color(0f, 0f, 0.25f, 1f);

    private final Debugger debugger;
    private final BitmapFont font;
    private final Skin skin = new Skin();
    private final Stage stage = new Stage(new ScreenViewport());

    private final Table left = new Table();
    private final Table right = new Table();
    private final Label status;
    private final TextButton runButton;
    private final Label registers;
    private final Label[] disasm = new Label[DISASM_LINES];
    private final int[] disasmAddress = new int[DISASM_LINES];
    private final Label memory;
    private final Label breakpoints;
    private final TextField memoryField;
    private final TextField breakField;
    private int memoryAddress;
    private float screenHeight;

    public DebuggerView(Debugger debugger) {
        this.debugger = debugger;
        this.memoryAddress = debugger.machine().profile().programStart();   // the memory view starts at 243
        this.font = TriadaFont.create();
        buildSkin();

        Table root = new Table();
        root.setFillParent(true);
        root.top().left();
        stage.addActor(root);
        root.add(left).top().left().expandY().fillY();
        root.add(right).top().left().width(PANEL_WIDTH).expandY().fillY();
        right.setBackground(skin.newDrawable("white", PANEL));
        right.top().left().pad(8);

        // ---- right panel
        right.add(label(debugger.machine().profile().name() + " DEBUGGER   F1", HEAD)).left().row();
        status = label("", TEXT);
        status.setWrap(true);
        right.add(status).left().width(PANEL_WIDTH - 16).padBottom(4).row();
        Table buttons = new Table();
        runButton = button("PAUSE F5", debugger::togglePause);
        buttons.add(runButton).padRight(6);
        buttons.add(button("STEP F11", debugger::step)).padRight(6);
        buttons.add(button("OVER F10", debugger::stepOver));
        right.add(buttons).left().padBottom(6).row();
        registers = label("", TEXT);
        right.add(registers).left().padBottom(6).row();
        right.add(label("DISASSEMBLY (CLICK = BREAK)", HEAD)).left().row();
        for (int i = 0; i < DISASM_LINES; i++) {
            final int line = i;
            disasm[i] = label("", TEXT);
            disasm[i].addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    debugger.toggleBreakpoint(disasmAddress[line]);
                }
            });
            right.add(disasm[i]).left().row();
        }
        Table breakRow = new Table();
        breakRow.add(label("BREAK", HEAD)).padRight(8);
        breakField = field(() -> toggleBreakFromField());
        breakRow.add(breakField).width(150).padRight(8);
        breakRow.add(button("SET/CLR", this::toggleBreakFromField));
        right.add(breakRow).left().padTop(6).row();
        breakpoints = label("", BREAK_LINE);
        breakpoints.setWrap(true);
        right.add(breakpoints).left().width(PANEL_WIDTH - 16).row();

        // ---- left: (space for the screen, set in resize) then the memory viewer
        left.top().left().pad(8);
        Table memRow = new Table();
        memRow.add(label("MEMORY", HEAD)).padRight(8);
        memoryField = field(() -> goToMemoryField());
        memRow.add(memoryField).width(150).padRight(8);
        memRow.add(button("GO", this::goToMemoryField)).padRight(8);
        memRow.add(button("-", () -> memoryAddress = memoryAddress - 8)).padRight(4);
        memRow.add(button("+", () -> memoryAddress = memoryAddress + 8));
        memory = label("", TEXT);
        left.add().height(0).row();               // the screen spacer: row 0, sized in resize()
        left.add(memRow).left().row();
        left.add(memory).left().padTop(4).row();
    }

    private void buildSkin() {
        Pixmap white = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        white.setColor(Color.WHITE);
        white.fill();
        skin.add("white", new Texture(white));
        white.dispose();
        skin.add("default", font);

        skin.add("default", new Label.LabelStyle(font, TEXT));

        TextButton.TextButtonStyle b = new TextButton.TextButtonStyle();
        b.font = font;
        b.fontColor = Color.WHITE;
        b.up = skin.newDrawable("white", new Color(0.15f, 0.15f, 0.55f, 1f));
        b.over = skin.newDrawable("white", new Color(0.25f, 0.25f, 0.75f, 1f));
        b.down = skin.newDrawable("white", new Color(0.5f, 0.5f, 1f, 1f));
        skin.add("default", b);

        TextField.TextFieldStyle t = new TextField.TextFieldStyle();
        t.font = font;
        t.fontColor = Color.WHITE;
        t.background = skin.newDrawable("white", new Color(0.1f, 0.1f, 0.2f, 1f));
        t.focusedBackground = skin.newDrawable("white", new Color(0.2f, 0.2f, 0.4f, 1f));
        t.cursor = skin.newDrawable("white", Color.WHITE);
        t.cursor.setMinWidth(2);
        t.selection = skin.newDrawable("white", new Color(0.3f, 0.3f, 0.9f, 1f));
        skin.add("default", t);
    }

    private Label label(String text, Color color) {
        Label l = new Label(text, skin);
        l.setColor(color);
        l.setAlignment(Align.left);
        return l;
    }

    private TextButton button(String text, Runnable action) {
        TextButton b = new TextButton(text, skin);
        b.pad(4, 6, 4, 6);
        b.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                action.run();
            }
        });
        return b;
    }

    /** A text field that runs {@code onEnter} when Enter is pressed, then gives the keyboard back. */
    private TextField field(Runnable onEnter) {
        TextField f = new TextField("", skin);
        f.setTextFieldListener((field, c) -> {
            if (c == '\r' || c == '\n') {
                onEnter.run();
                stage.setKeyboardFocus(null);
            }
        });
        return f;
    }

    private void goToMemoryField() {
        Integer a = Debugger.parseAddress(memoryField.getText());
        if (a != null) {
            memoryAddress = a;
        }
    }

    private void toggleBreakFromField() {
        Integer a = Debugger.parseAddress(breakField.getText());
        if (a != null) {
            debugger.toggleBreakpoint(a);
        }
    }

    // ================================================================ per frame

    public Stage stage() {
        return stage;
    }

    /** Window size changed; {@code screenHeight} = room to leave above the memory viewer. */
    public void resize(int width, int height, float screenHeightOnLeft) {
        stage.getViewport().update(width, height, true);
        this.screenHeight = screenHeightOnLeft;
        left.getCells().get(0).height(screenHeight);
        left.getCells().get(0).width(Math.max(0, width - PANEL_WIDTH - 16));
        left.invalidateHierarchy();
    }

    /** Copies the debugger's state into the widgets. Called every rendered frame while visible. */
    public void refresh() {
        String s = debugger.status();
        status.setText(s);
        status.setColor(s.contains("FAULT") ? BREAK_LINE : debugger.isPaused() ? HEAD : TEXT);
        runButton.setText(debugger.isPaused() ? "RUN   F5" : "PAUSE F5");
        registers.setText(String.join("\n", debugger.registers()));

        List<Debugger.DisasmLine> lines = debugger.disassembly(4, DISASM_LINES - 5);
        for (int i = 0; i < DISASM_LINES; i++) {
            if (i < lines.size()) {
                Debugger.DisasmLine l = lines.get(i);
                disasmAddress[i] = l.address();
                disasm[i].setText((l.isPc() ? ">" : " ") + (l.isBreakpoint() ? "*" : " ")
                        + String.format(Locale.ROOT, "%7d ", l.address()) + l.text());
                disasm[i].setColor(l.isPc() ? PC_LINE : l.isBreakpoint() ? BREAK_LINE : TEXT);
            } else {
                disasm[i].setText("");
            }
        }
        List<Integer> bps = debugger.breakpoints();
        breakpoints.setText(bps.isEmpty() ? "NO BREAKPOINTS" : "AT " + bps.toString().replaceAll("[\\[\\]]", ""));

        StringBuilder mem = new StringBuilder();
        for (Debugger.MemoryLine l : debugger.memory(memoryAddress, 8)) {
            mem.append(l.format()).append('\n');
        }
        memory.setText(mem.toString().stripTrailing());
    }

    public void draw(float delta) {
        stage.act(delta);
        stage.draw();
    }

    /** True while one of the text fields has the keyboard (typed keys belong to it, not the machine). */
    public boolean typing() {
        return stage.getKeyboardFocus() instanceof TextField;
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();     // also disposes the font and the white texture it owns
    }
}
