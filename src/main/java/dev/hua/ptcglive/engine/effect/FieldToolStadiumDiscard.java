package dev.hua.ptcglive.engine.effect;

/** Field Blower selects one or two public Tools/Stadium cards in one operation. */
public final class FieldToolStadiumDiscard {
    private FieldToolStadiumDiscard() { }
    public static final String TYPE="discard_field_tools_and_stadium";
    public static void validate(EffectDefinition effect) {
        if(!effect.type().equals(TYPE)||effect.target()!=EffectTarget.SELF||!effect.arguments().isEmpty()
            ||!effect.conditions().isEmpty()||!effect.effects().isEmpty())
            throw new IllegalArgumentException("Field Blower requires the reviewed public Tool/Stadium selection");
    }
}
