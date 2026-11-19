package dev.hua.ptcglive.economy;

import java.util.List;
import java.util.UUID;

/** Stable wallet boundary for every current and future PTCG Craft feature. */
public interface PokeCoinWallet {
    long balance(UUID playerId);
    List<PokeCoinLedger.Transaction> history(UUID playerId);
}
