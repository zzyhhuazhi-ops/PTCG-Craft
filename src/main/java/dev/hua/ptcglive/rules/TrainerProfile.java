package dev.hua.ptcglive.rules;

import dev.hua.ptcglive.engine.effect.EffectProgram;
import dev.hua.ptcglive.engine.effect.EffectDefinition;
import dev.hua.ptcglive.engine.effect.EffectTarget;
import dev.hua.ptcglive.engine.effect.ConditionDefinition;
import dev.hua.ptcglive.engine.effect.ConditionType;
import dev.hua.ptcglive.engine.effect.EffectTrigger;
import dev.hua.ptcglive.engine.effect.TriggeredEffects;
import java.util.*;

/** Category/opcode compatibility is checked at load time; unsupported persistent effects fail closed. */
public record TrainerProfile(TrainerCategory category, TrainerEffect effect, EffectProgram effectProgram,
                             List<dev.hua.ptcglive.engine.effect.CardTriggerDefinition> automaticEffects,
                             /** Finish the complete card and its reactions before ending its user's turn. */
                             boolean endsTurn,
                             /** Printed exception allowing this Supporter during the first player's first turn. */
                             boolean mayPlayFirstTurn,
                             /** Only while on the battlefield; this card remains an Item in every other zone. */
                             dev.hua.ptcglive.engine.field.PokemonProfile inPlayPokemon,
                             StadiumActivation activation, ToolActivation toolActivation, PairedTrainerPlay pairedPlay, boolean directionalStadium) {
    public TrainerProfile(TrainerCategory category,TrainerEffect effect,EffectProgram program,
            List<dev.hua.ptcglive.engine.effect.CardTriggerDefinition> automaticEffects,boolean endsTurn,boolean mayPlayFirstTurn,
            dev.hua.ptcglive.engine.field.PokemonProfile inPlayPokemon,StadiumActivation activation,ToolActivation toolActivation,PairedTrainerPlay pairedPlay) {
        this(category,effect,program,automaticEffects,endsTurn,mayPlayFirstTurn,inPlayPokemon,activation,toolActivation,pairedPlay,false);
    }
    public TrainerProfile(TrainerCategory category,TrainerEffect effect,EffectProgram program,
            List<dev.hua.ptcglive.engine.effect.CardTriggerDefinition> automaticEffects,boolean endsTurn,
            dev.hua.ptcglive.engine.field.PokemonProfile inPlayPokemon,StadiumActivation activation,PairedTrainerPlay pairedPlay) {
        this(category,effect,program,automaticEffects,endsTurn,false,inPlayPokemon,activation,null,pairedPlay);
    }
    public TrainerProfile(TrainerCategory category,TrainerEffect effect,EffectProgram program,
            List<dev.hua.ptcglive.engine.effect.CardTriggerDefinition> automaticEffects,boolean endsTurn,
            dev.hua.ptcglive.engine.field.PokemonProfile inPlayPokemon,StadiumActivation activation) {
        this(category,effect,program,automaticEffects,endsTurn,false,inPlayPokemon,activation,null,null);
    }
    public TrainerProfile(TrainerCategory category,TrainerEffect effect,EffectProgram program,
            List<dev.hua.ptcglive.engine.effect.CardTriggerDefinition> automaticEffects,boolean endsTurn,
            dev.hua.ptcglive.engine.field.PokemonProfile inPlayPokemon) {
        this(category,effect,program,automaticEffects,endsTurn,false,inPlayPokemon,null,null,null);
    }
    public TrainerProfile(TrainerCategory category,TrainerEffect effect,EffectProgram program,
            List<dev.hua.ptcglive.engine.effect.CardTriggerDefinition> automaticEffects,boolean endsTurn) {
        this(category,effect,program,automaticEffects,endsTurn,null);
    }
    public TrainerProfile(TrainerCategory category,TrainerEffect effect,EffectProgram program,
            List<dev.hua.ptcglive.engine.effect.CardTriggerDefinition> automaticEffects) {
        this(category,effect,program,automaticEffects,false);
    }
    public TrainerProfile(TrainerCategory category,TrainerEffect effect,EffectProgram program) {this(category,effect,program,List.of());}
    public TrainerProfile(TrainerCategory category, TrainerEffect effect) { this(category, effect, EffectProgram.EMPTY); }
    public TrainerProfile {
        java.util.Objects.requireNonNull(effect);
        if(directionalStadium&&(category!=TrainerCategory.STADIUM||effect.opcode()!=TrainerEffect.Opcode.PASSIVE_STADIUM
            ||endsTurn||mayPlayFirstTurn||inPlayPokemon!=null||activation!=null||toolActivation!=null||pairedPlay!=null||!automaticEffects.isEmpty()))
            throw new IllegalArgumentException("朝向只适用于两侧效果不同的被动场地");
        if((effect.opcode()==TrainerEffect.Opcode.DRAW_ON_TURN_DRAW||effect.opcode()==TrainerEffect.Opcode.PRIZE_COIN_EXTRA_PRIZE||effect.opcode()==TrainerEffect.Opcode.PRIZE_SEARCH_POKEMON||effect.opcode()==TrainerEffect.Opcode.SETUP_FIRST_TICKET)&&(category!=TrainerCategory.ITEM||endsTurn||mayPlayFirstTurn
            ||inPlayPokemon!=null||activation!=null||toolActivation!=null||pairedPlay!=null||!effectProgram.isEmpty()||!automaticEffects.isEmpty()))
            throw new IllegalArgumentException("特殊使用时机只允许无额外程序的专用物品");
        java.util.Objects.requireNonNull(effectProgram);
        automaticEffects=List.copyOf(Objects.requireNonNull(automaticEffects));
        if(pairedPlay!=null && (category!=TrainerCategory.ITEM || effect.opcode()!=TrainerEffect.Opcode.COMPOSED
                || endsTurn || inPlayPokemon!=null || activation!=null || toolActivation!=null))
            throw new IllegalArgumentException("同时使用仅核验不结束回合的组合物品卡");
        if(activation!=null && (category!=TrainerCategory.STADIUM || effect.opcode()!=TrainerEffect.Opcode.PASSIVE_STADIUM))
            throw new IllegalArgumentException("每位玩家回合内的场上主动操作仅属于竞技场");
        if(toolActivation!=null && (category!=TrainerCategory.TOOL || effect.opcode()!=TrainerEffect.Opcode.PASSIVE_TOOL))
            throw new IllegalArgumentException("工具主动力量只属于场上的宝可梦道具");
        if ((effect.opcode()==TrainerEffect.Opcode.PLAY_AS_POKEMON) != (inPlayPokemon!=null))
            throw new IllegalArgumentException("物品宝可梦必须同时声明场上形态与对应使用动作");
        if (inPlayPokemon!=null) {
            if(category!=TrainerCategory.ITEM || endsTurn || !effectProgram.isEmpty() || !automaticEffects.isEmpty())
                throw new IllegalArgumentException("物品宝可梦形态不能混用其他训练家类别或额外使用程序");
            if(!inPlayPokemon.printedRules().isItemPokemon()
                    || inPlayPokemon.isEvolution() || inPlayPokemon.pokemonName()==null
                    || inPlayPokemon.pokemonType()!=dev.hua.ptcglive.engine.field.EnergyType.COLORLESS
                    || inPlayPokemon.weaknessType()!=null || inPlayPokemon.resistanceType()!=null
                    || inPlayPokemon.retreatCost()!=0 || inPlayPokemon.prizeCards()<0 || inPlayPokemon.prizeCards()>1
                    || !inPlayPokemon.attacks().isEmpty()
                    || inPlayPokemon.placementRule()!=dev.hua.ptcglive.engine.field.PokemonPlacementRule.ANY_EFFECT)
                throw new IllegalArgumentException("未审核的物品宝可梦形态；须为有名称的无色基础形态，且无印刷招式、弱点或抵抗力");
            if(inPlayPokemon.printedRules().preventsPrizeTake()
                    &&(inPlayPokemon.hitPoints()!=30 || inPlayPokemon.prizeCards()!=0 || !inPlayPokemon.abilities().isEmpty()
                        || !(inPlayPokemon.printedRules()==dev.hua.ptcglive.engine.field.PokemonPrintedRules.ROBO_SUBSTITUTE_ITEM
                            ? "Robo Substitute Team Flare Gear" : "Lillie's Poké Doll").equals(inPlayPokemon.pokemonName())))
                throw new IllegalArgumentException("不给奖赏的替身形态仅允许已审核卡名、30HP且无特性");
            if(inPlayPokemon.printedRules()==dev.hua.ptcglive.engine.field.PokemonPrintedRules.UNIDENTIFIED_FOSSIL_ITEM
                    &&(!"Unidentified Fossil".equals(inPlayPokemon.pokemonName())
                        ||inPlayPokemon.hitPoints()!=60 || inPlayPokemon.prizeCards()!=1
                        ||!inPlayPokemon.abilities().isEmpty()))
                throw new IllegalArgumentException("谜之化石无异常状态免疫形态只允许60HP、1奖赏且无特性");
        }
        boolean passive=effect.opcode()==TrainerEffect.Opcode.PASSIVE_TOOL||effect.opcode()==TrainerEffect.Opcode.PASSIVE_STADIUM;
        if (passive ? effectProgram.isEmpty()&&automaticEffects.isEmpty()&&activation==null&&toolActivation==null : (effect.opcode() == TrainerEffect.Opcode.COMPOSED) == effectProgram.isEmpty())
            throw new IllegalArgumentException("组合训练家必须仅使用非空 EffectProgram，不能与旧操作码混用");
        boolean valid = switch (category) {
            case ITEM, SUPPORTER -> !effect.persistent();
            case TOOL -> effect.opcode() == TrainerEffect.Opcode.TOOL_RETREAT_REDUCTION || effect.opcode() == TrainerEffect.Opcode.TOOL_RETREAT_REDUCTION_ALLOW_TWO || effect.opcode()==TrainerEffect.Opcode.PASSIVE_TOOL;
            case STADIUM -> effect.opcode() == TrainerEffect.Opcode.STADIUM_BASIC_RETREAT_REDUCTION || effect.opcode()==TrainerEffect.Opcode.PASSIVE_STADIUM;
            default -> false;
        };
        if(endsTurn && (effect.opcode()!=TrainerEffect.Opcode.COMPOSED
                || (category!=TrainerCategory.ITEM && category!=TrainerCategory.SUPPORTER)))
            throw new IllegalArgumentException("使用后结束回合仅用于可续接的物品或支援者程序");
        if(mayPlayFirstTurn && category!=TrainerCategory.SUPPORTER)
            throw new IllegalArgumentException("首回合使用例外只适用于支援者");
        if (!valid) throw new IllegalArgumentException("训练家类别与效果不兼容，或此持续效果尚未支持");
        if(!effect.persistent()&&!automaticEffects.isEmpty())throw new IllegalArgumentException("非持续训练家不能携带场上自动效果");
        if(effect.persistent())automaticEffects=dev.hua.ptcglive.engine.effect.CardTriggerDefinition.validated(automaticEffects,
            category==TrainerCategory.TOOL?dev.hua.ptcglive.engine.effect.PassiveModifierProgram.Source.TOOL:dev.hua.ptcglive.engine.effect.PassiveModifierProgram.Source.STADIUM);
        if(passive)dev.hua.ptcglive.engine.effect.PassiveModifierProgram.validate(effectProgram,
            category==TrainerCategory.TOOL?dev.hua.ptcglive.engine.effect.PassiveModifierProgram.Source.TOOL:dev.hua.ptcglive.engine.effect.PassiveModifierProgram.Source.STADIUM);
    }
    /** Printed side summaries used by the server choices and public field display. */
    public String stadiumSideLabel(boolean first) {
        if(!directionalStadium)throw new IllegalStateException("此场地不选择朝向");
        var effects=effectProgram.forTrigger(EffectTrigger.PASSIVE).stream().flatMap(t->t.effects().stream()).toList();
        if(effects.stream().anyMatch(e->e.type().equals("bench_limit")))return first?"备战上限3只":"草、炎、水招式伤害−20";
        if(effects.stream().anyMatch(e->e.type().equals("special_condition_immunity")))return first?"防止并解除混乱、中毒":"防止并解除睡眠、麻痹";
        return first?"钢宝可梦受到对手招式伤害−10":"恶宝可梦对对手战斗位伤害+10";
    }
    /** Printed attachment/departure rule; Tool suppression only disables the ongoing modifier. */
    public boolean opponentLegacyExTool() {
        return category==TrainerCategory.TOOL&&effectProgram.forTrigger(EffectTrigger.PASSIVE).stream()
            .flatMap(t->t.effects().stream()).anyMatch(e->e.type().equals("flare_gear"));
    }
    public boolean composed() { return effect.opcode() == TrainerEffect.Opcode.COMPOSED; }
    public boolean passive() {return effect.opcode()==TrainerEffect.Opcode.PASSIVE_TOOL||effect.opcode()==TrainerEffect.Opcode.PASSIVE_STADIUM;}
    /** Compatibility data adapter: legacy definitions use the same live-source resolver, not a second rule path. */
    public EffectProgram ongoingProgram() {
        if(passive())return effectProgram;
        if(!effect.persistent())return EffectProgram.EMPTY;
        var modifiers=new ArrayList<EffectDefinition>();
        if(category==TrainerCategory.TOOL) {
            modifiers.add(new EffectDefinition("retreat_cost_modifier",EffectTarget.SELF,Map.of("amount",Integer.toString(-effect.amount())),List.of(),List.of()));
            if(effect.toolCapacityOnAttach()>1)modifiers.add(new EffectDefinition("tool_capacity",EffectTarget.SELF,Map.of("amount",Integer.toString(effect.toolCapacityOnAttach())),List.of(),List.of()));
        } else {
            for(var target:List.of(EffectTarget.ALL_OWN_POKEMON,EffectTarget.ALL_OPPONENT_POKEMON))
                modifiers.add(new EffectDefinition("retreat_cost_modifier",target,Map.of("amount",Integer.toString(-effect.amount())),
                    List.of(new ConditionDefinition(ConditionType.IS_BASIC,Map.of(),List.of())),List.of()));
        }
        return new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.PASSIVE,List.of(),modifiers)));
    }
}
