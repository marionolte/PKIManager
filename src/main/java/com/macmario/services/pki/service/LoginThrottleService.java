package com.macmario.services.pki.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * In-memory, per-IP login throttle.
 * <ul>
 *   <li>From the 3rd failed attempt the IP is blocked for a random 10–60 seconds.</li>
 *   <li>At the 10th failed attempt the IP is blocked for 10 minutes.</li>
 *   <li>A successful login clears the IP's counter; a long idle period rolls it back.</li>
 * </ul>
 * State is in-memory (single Tomcat instance). The IP is {@code request.getRemoteAddr()};
 * behind a reverse proxy, configure {@code RemoteIpValve} so that is the real client.
 */
public class LoginThrottleService {

    private static final int SOFT_THRESHOLD = 3;         // block starts at the 3rd failure
    private static final int HARD_THRESHOLD = 10;        // 10 minutes at the 10th failure
    private static final int SOFT_MIN_SECONDS = 10;
    private static final int SOFT_MAX_SECONDS = 60;
    private static final long HARD_BLOCK_MS = 30 * 60 * 1000L;
    private static final long RESET_WINDOW_MS = 10 * 60 * 1000L; // idle period after which the counter resets
    private static final int MAX_ENTRIES = 50_000;              // purge guard against unbounded growth

    private static final class Attempt {
        int failures;
        long blockedUntil;
        long lastFailureAt;
    }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** Seconds the IP must still wait, or 0 if it may attempt a login now. */
    public long blockedSeconds(String ip) {
        Attempt a = attempts.get(ip);
        if (a == null) return 0;
        long now = System.currentTimeMillis();
        synchronized (a) {
            return a.blockedUntil > now ? (a.blockedUntil - now + 999) / 1000 : 0;
        }
    }

    /**
     * Record a failed login for the IP and apply the escalating block.
     * @return seconds the IP is now blocked for (0 if below the threshold)
     */
    public long recordFailure(String ip) {
        if (ip == null) ip = "unknown";
        long now = System.currentTimeMillis();
        if (attempts.size() > MAX_ENTRIES) purge(now);
        Attempt a = attempts.computeIfAbsent(ip, k -> new Attempt());
        synchronized (a) {
            // Roll the counter back after a long quiet period (and when not currently blocked).
            if (a.lastFailureAt > 0 && now - a.lastFailureAt > RESET_WINDOW_MS && now >= a.blockedUntil) {
                a.failures = 0;
            }
            a.failures++;
            a.lastFailureAt = now;

            long blockMs = 0;
            if (a.failures >= HARD_THRESHOLD) {
                blockMs = HARD_BLOCK_MS;
            } else if (a.failures >= SOFT_THRESHOLD) {
                blockMs = ThreadLocalRandom.current().nextInt(SOFT_MIN_SECONDS, SOFT_MAX_SECONDS + 1) * 1000L;
            }
            if (blockMs > 0) a.blockedUntil = now + blockMs;
            return blockMs > 0 ? (blockMs + 999) / 1000 : 0;
        }
    }

    /** Clear the IP's failure state after a successful login. */
    public void recordSuccess(String ip) {
        if (ip != null) attempts.remove(ip);
    }

    private void purge(long now) {
        attempts.forEachEntry(1, e -> {
            Attempt a = e.getValue();
            synchronized (a) {
                if (now >= a.blockedUntil && now - a.lastFailureAt > RESET_WINDOW_MS) {
                    attempts.remove(e.getKey(), a);
                }
            }
        });
    }
}
