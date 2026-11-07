/**
 * The TRIADA-85 machine itself: ternary math, memory, CPU, video, sound and I/O.
 *
 * <p>Architecture rule: this module is plain Java. It must not depend on libGDX
 * or any other UI library, and it avoids threads, reflection and file I/O so a
 * later web build can compile it. Frontends drive it; it never drives them.
 */
package org.triada.core;
