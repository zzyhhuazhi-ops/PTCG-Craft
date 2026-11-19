package dev.hua.ptcglive.engine.effect;

import java.util.UUID;

/** Reusable relation between a listener and the Pokémon that caused the event. */
public enum EventPokemonScope {
    SELF, OWN_FIELD, OPPONENT_FIELD, ANY_FIELD;
    public boolean matches(UUID owner,UUID source,UUID subjectOwner,UUID subject) {
        return switch(this) {
            case SELF -> owner.equals(subjectOwner)&&source.equals(subject);
            case OWN_FIELD -> owner.equals(subjectOwner);
            case OPPONENT_FIELD -> !owner.equals(subjectOwner);
            case ANY_FIELD -> true;
        };
    }
}
