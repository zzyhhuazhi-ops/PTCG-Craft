package dev.hua.ptcglive.engine.effect;

import java.util.*;
import dev.hua.ptcglive.rules.*;

/** Reveal for damage calculation, then resolve those exact cards at the start of ordinary attack text. */
public final class AttackDeckWindow {
    private AttackDeckWindow() { }
    public static final String REVEAL="reveal_deck_window",RESOLVE="resolve_deck_window";
    public static final Set<String> REVEAL_FIELDS=Set.of("amount","position","attack_id","bind","card_category","battle_style"),RESOLVE_FIELDS=Set.of("ref","shuffle_category","discard_category","discard_battle_style");
    public static boolean handles(EffectDefinition e){return e.type().equals(REVEAL)||e.type().equals(RESOLVE);}
    public static boolean reveal(EffectDefinition e){return e.type().equals(REVEAL);}
    public static boolean filteredTop(EffectDefinition e){return "TOP".equals(e.arguments().get("position"))||e.arguments().containsKey("discard_category")||e.arguments().containsKey("discard_battle_style");}
    public static boolean matchesFilteredCard(EffectDefinition e,CardDefinition card){
        return e.arguments().containsKey("card_category")||e.arguments().containsKey("discard_category")
            ?card.category()==CardCategory.ENERGY:card.battleStyles().contains(BattleStyle.FUTURE);
    }
    public static String name(EffectDefinition e){validate(e);return e.arguments().get(reveal(e)?"bind":"ref");}
    public static void validate(EffectDefinition e){
        if(!handles(e)||e.target()!=EffectTarget.DECK||!e.effects().isEmpty()||!e.conditions().isEmpty())throw new IllegalArgumentException("公开计伤牌库窗口须为己方牌库的无条件原子步骤");
        var a=e.arguments();
        if(reveal(e)) {
            boolean topEnergy=a.keySet().equals(Set.of("amount","position","card_category","bind"))&&"ENERGY".equals(a.get("card_category"));
            boolean topFuture=a.keySet().equals(Set.of("amount","position","battle_style","bind"))&&"FUTURE".equals(a.get("battle_style"));
            boolean bottom=a.keySet().equals(Set.of("amount","position","attack_id","bind"))&&"BOTTOM".equals(a.get("position"));
            if(!bottom&&!("TOP".equals(a.get("position"))&&(topEnergy||topFuture)))throw new IllegalArgumentException("公开计伤仅核验牌库底印刷招式或牌库顶能量/未来筛选");
            int n=Integer.parseInt(a.get("amount"));if(n<1||n>60)throw new IllegalArgumentException("公开牌数须为1至60");
            if(bottom&&!a.get("attack_id").matches("[a-z][a-z0-9-]{0,127}"))throw new IllegalArgumentException("招式筛选须为稳定印刷招式ID");
            AttackCountBindings.name(a.get("bind"));
        }else {
            boolean bottom=a.keySet().equals(Set.of("ref","shuffle_category"))&&"POKEMON".equals(a.get("shuffle_category"));
            boolean energy=a.keySet().equals(Set.of("ref","discard_category"))&&"ENERGY".equals(a.get("discard_category"));
            boolean future=a.keySet().equals(Set.of("ref","discard_battle_style"))&&"FUTURE".equals(a.get("discard_battle_style"));
            if(!bottom&&!energy&&!future)throw new IllegalArgumentException("公开窗口须按已审核的能量/未来弃置或宝可梦洗回分类");
            AttackCountBindings.name(a.get("ref"));
        }
    }
    private static boolean contains(EffectDefinition e){return handles(e)||e.effects().stream().anyMatch(AttackDeckWindow::contains);}
    public static void validate(EffectProgram program){
        var relevant=program.triggers().stream().flatMap(b->b.effects().stream()).filter(AttackDeckWindow::contains).toList();
        if(relevant.isEmpty())return;
        var before=program.forTrigger(EffectTrigger.BEFORE_ATTACK);var after=program.forTrigger(EffectTrigger.AFTER_ATTACK);
        if(before.size()!=1||after.size()!=1||!before.getFirst().conditions().isEmpty()||!after.getFirst().conditions().isEmpty()||before.getFirst().effects().isEmpty()||after.getFirst().effects().isEmpty())throw new IllegalArgumentException("公开计伤须有伤害前公开和无条件伤害后整理");
        var first=before.getFirst().effects().getLast();var last=after.getFirst().effects().getFirst();
        if(!reveal(first)||!last.type().equals(RESOLVE))throw new IllegalArgumentException("公开须为伤害前最后一步，整理须为伤害后第一步");
        if(!name(first).equals(name(last))||!relevant.equals(List.of(first,last)))throw new IllegalArgumentException("一次攻击仅核验一对顺序明确的公开窗口，不能重复、嵌套、延迟或跨阶段引用");
        if(filteredTop(first)!=filteredTop(last)||filteredTop(first)
            &&(!Objects.equals(first.arguments().get("card_category"),last.arguments().get("discard_category"))
                ||!Objects.equals(first.arguments().get("battle_style"),last.arguments().get("discard_battle_style"))))
            throw new IllegalArgumentException("公开计数与后续整理必须使用相同的卡牌筛选");
    }
}
