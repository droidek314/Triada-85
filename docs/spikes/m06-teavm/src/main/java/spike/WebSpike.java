package spike;
import org.triada.assembler.Assembler;
import org.triada.assembler.SourceLoader;
import org.triada.core.Machine;

/** Assembles and runs hello, then renders a frame, entirely inside the browser/Node. */
public class WebSpike {
    public static void main(String[] args) throws Exception {
        String src = String.join("\n",
            "        .org 243",
            "start:  LDI R1, IO_BASE",
            "        LDI R2, msg",
            "next:   LD R0, [R2]",
            "        JZ done",
            "        ST R0, [R1+DEBUG_CHAR]",
            "        INC R2",
            "        JMP next",
            "done:   LDI R0, 5",
            "        ST R0, [R1+BORDER]",
            "        WAIT",
            "        LDI R0, 0",
            "        ST R0, [R1+HOST_EXIT]",
            "msg:    .str \"HELLO FROM TERNARY JS\"");
        Machine m = new Machine();
        m.loadProgram(new Assembler(SourceLoader.NONE).assembleSource("hello.tasm", src).image());
        StringBuilder out = new StringBuilder();
        for (int f = 0; f < 10 && !m.debug().exitRequested(); f++) {
            m.runFrame();
            out.append(m.debug().takeOutput());
        }
        System.out.println(out);
        System.out.println("exit=" + m.debug().exitCode() + " border=" + Integer.toHexString(m.video().frame()[0]));
        long t0 = System.currentTimeMillis();
        Machine bench = new Machine();
        bench.loadProgram(new Assembler(SourceLoader.NONE).assembleSource("loop.tasm",
            "loop: ADDI R0, 1\n MIN R1, R0\n ROT R2, 1\n JMP loop").image());
        for (int f = 0; f < 60; f++) bench.runFrame();
        System.out.println("60 frames (3.5M instructions + 60 renders) in " + (System.currentTimeMillis() - t0) + " ms");
    }
}
