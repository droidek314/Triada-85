package org.triada.net;

import java.time.Duration;

/**
 * The limits of a TRIADA-NET server (NET-SPEC §5, M18): how long, how big and
 * how often. The server reads them from its configuration; {@link #DEFAULT}
 * is what it uses otherwise.
 *
 * @param lineLength      longest line a caller can type (message lines, answers)
 * @param subjectLength   longest subject of a message or mail
 * @param messageLines    most lines in a message or mail
 * @param messageChars    most characters in a message or mail
 * @param oneLinerLength  longest one-liner
 * @param fileWords       largest upload, in words
 * @param storageWords    words a caller may have uploaded in all (queued and public)
 * @param minutesPerDay   time per caller, per board, per day (guests: per address)
 * @param idle            a caller who types nothing this long is hung up
 * @param loginTries      wrong passwords per call before the board hangs up
 * @param lockoutFailures wrong passwords in a row before the account locks
 * @param lockout         how long a locked account stays locked
 * @param failuresPerIp   wrong passwords from one address per {@code failureWindow} before it is refused
 * @param failureWindow   the window for {@code failuresPerIp}
 * @param newUsersPerIp   accounts one address may create per day
 * @param postsPer10Min   messages, mail and one-liners a caller may write in 10 minutes
 * @param uploadsPerHour  uploads a caller may start in an hour
 * @param floodPerSecond  characters a caller may send in one second (outside transfers)
 * @param transferTimeout how long TRIMODEM waits for an answer before trying again
 * @param passwordIterations PBKDF2 rounds
 */
public record Limits(int lineLength, int subjectLength, int messageLines, int messageChars, int oneLinerLength,
        int fileWords, long storageWords, int minutesPerDay, Duration idle, int loginTries, int lockoutFailures,
        Duration lockout, int failuresPerIp, Duration failureWindow, int newUsersPerIp, int postsPer10Min,
        int uploadsPerHour, int floodPerSecond, Duration transferTimeout, int passwordIterations) {

    public static final Limits DEFAULT = new Limits(80, 24, 40, 2_000, 24, 19_683, 59_049, 30,
            Duration.ofMinutes(5), 3, 5, Duration.ofMinutes(15), 10, Duration.ofMinutes(10), 3, 5, 3, 1_000,
            Duration.ofSeconds(10), Passwords.DEFAULT_ITERATIONS);

    /** The same limits with fewer PBKDF2 rounds: for tests, which create many accounts. */
    public Limits withPasswordIterations(int n) {
        return new Limits(lineLength, subjectLength, messageLines, messageChars, oneLinerLength, fileWords,
                storageWords, minutesPerDay, idle, loginTries, lockoutFailures, lockout, failuresPerIp, failureWindow,
                newUsersPerIp, postsPer10Min, uploadsPerHour, floodPerSecond, transferTimeout, n);
    }

    /** The same limits with a different number of accounts per address per day (the load test needs many). */
    public Limits withNewUsersPerIp(int n) {
        return new Limits(lineLength, subjectLength, messageLines, messageChars, oneLinerLength, fileWords,
                storageWords, minutesPerDay, idle, loginTries, lockoutFailures, lockout, failuresPerIp, failureWindow,
                n, postsPer10Min, uploadsPerHour, floodPerSecond, transferTimeout, passwordIterations);
    }
}
