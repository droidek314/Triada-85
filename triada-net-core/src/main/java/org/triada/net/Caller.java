package org.triada.net;

/**
 * Who is calling, from the HELLO line (NET-SPEC §2, M17): the machine model,
 * its screen in characters, and the client's version. Boards use the screen
 * size to lay out their pages (27 × 18 on a TRIADA-85).
 */
public record Caller(String model, int columns, int rows, String client) {
}
