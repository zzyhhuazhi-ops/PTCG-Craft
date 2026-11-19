package dev.hua.ptcglive.engine.field;

/** Small pure stages shared by authoritative Active damage and conservative previews. */
public final class DamageArithmetic {
    private DamageArithmetic() {}
    public static int weakness(int damage,int multiplier){return damage*multiplier;}
    public static int resistance(int damage,int reduction){return damage-reduction;}
}
