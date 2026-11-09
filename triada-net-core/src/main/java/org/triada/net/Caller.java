package org.triada.net;

/**
 * Who is calling, from the HELLO line (NET-SPEC §2, M17): the machine model,
 * its screen in characters, and the client's version. Boards use the screen
 * size to lay out their pages (27 × 18 on a TRIADA-85). Since M18 also the
 * network address the call came from, for per-address limits and bans.
 */
public record Caller(String model, int columns, int rows, String client, String address) {
}
