package dev.pppp.model;

import java.util.List;

/**
 * What one player sees of the link cable: the lobby (everyone ready, themselves included), what they are ready
 * for, the pending challenge to and from them, and their running session. Times are epoch milliseconds; now is
 * the server's clock, so a browser can count down without trusting its own. links: every running session, one entry
 * per pair (who is linked is public, like the lobby).
 */
public record LinkState(List<Ready> lobby, String ready, Challenge incoming, Challenge outgoing, Session session,
                        List<Pair> links, long now) {
    /** kind: TRADE or BATTLE. */
    public record Ready(String userId, String kind) {}

    /** Also the LINK_CHALLENGE payload. status: PENDING, ACCEPTED, DECLINED, EXPIRED or CANCELLED. */
    public record Challenge(String from, String to, String kind, long expiresAt, String status) {}

    public record Session(String peer, String kind, long endsAt) {}
    public record Pair(String a, String b, String kind, long endsAt) {}

    /** LINK_SESSION payload. status: STARTED or ENDED; reason (ENDED only): UNPLUGGED, LEFT or TIME_UP. */
    public record SessionEvent(String a, String b, String kind, long endsAt, String status, String reason) {}
}
