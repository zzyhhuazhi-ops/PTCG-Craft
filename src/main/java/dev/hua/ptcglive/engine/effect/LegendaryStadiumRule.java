package dev.hua.ptcglive.engine.effect;
import java.util.*;
/** Two physical cards form one Stadium only when played together from hand. */
public final class LegendaryStadiumRule {
 private LegendaryStadiumRule(){}
 public static boolean handles(String type){return type.equals("legendary_stadium");}
 public static void validate(EffectDefinition effect){
  if(!handles(effect.type())||effect.target()!=EffectTarget.SELF||!effect.conditions().isEmpty()||!effect.effects().isEmpty()
   ||!effect.arguments().keySet().equals(Set.of("partner","mode"))
   ||!effect.arguments().get("partner").matches("[a-z0-9_]+")
   ||!Set.of("EVOLUTION_ABILITIES","COLORLESS_PRIZES","DOUBLE_HEALING").contains(effect.arguments().get("mode")))
   throw new IllegalArgumentException("传说竞技场必须完整声明另一半及已审核模式");
 }
 public static EffectDefinition rule(EffectProgram program){
  return program.triggers().stream().flatMap(t->t.effects().stream()).filter(e->handles(e.type())).findFirst().orElse(null);
 }
}
