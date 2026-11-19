package dev.hua.ptcglive.client;

import dev.hua.ptcglive.engine.MatchSession;
import java.util.UUID;

/** A visible match screen (or a preview's parent) must continue receiving recipient-specific updates. */
public interface MatchDisplay {
    UUID matchId();
    void update(MatchSession.Snapshot next, String message);
}
