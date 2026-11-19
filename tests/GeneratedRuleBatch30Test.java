import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual generated Special Energy programs, with live hosts and authoritative events. */
public final class GeneratedRuleBatch30Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final CombatCatalog CATALOG=BundledCombatData.load();
    static final CardDatabase CARDS=BundledRules.loadCardDefinitions();
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void reject(Runnable action){GeneratedRuleBatch8Test.reject(action);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch16Test.card(m,p,id);}
    static EnergyProfile energy(String id){return CATALOG.energyProfile(id).orElseThrow();}
    static CardInstance attach(ServerMatch m,String id,UUID owner,UUID host){var e=hand(m,owner,id);m.attachEnergyByEffect(A,owner,EffectSourceZone.HAND,e.instanceId(),owner,host);return e;}
    static class Config {
        String energy;EnergyType type=EnergyType.COLORLESS,evolvedType=EnergyType.COLORLESS;
        Set<PokemonFamily> family=Set.of(),evolvedFamily=Set.of();Set<EnergyType> printedTypes;boolean unplayedTypedEnergy;
        AttackDefinition attack=GeneratedRuleBatch8Test.hit(100,false);
        TrainerProfile stadium=DynamicEnergyProvisionTest.stadium();
        Config(String id){energy=id;}
    }
    static ServerMatch game(String id){return game(new Config(id));}
    static ServerMatch game(Config c){
        String[] ids={"basic","basic","evolved",c.energy,"grass","stadium","wrong"};
        var defs=new ArrayList<CardDefinition>();
        for(String id:Arrays.stream(ids).distinct().toList()){
            if(id.equals(c.energy)){defs.add(CARDS.find(id).orElseThrow());continue;}
            boolean pokemon=Set.of("basic","evolved","wrong").contains(id);
            defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:id.equals("grass")?CardCategory.ENERGY:CardCategory.TRAINER,id.equals("stadium")?TrainerCategory.STADIUM:TrainerCategory.NONE,"H",Set.of("international"),id.equals("basic")||id.equals("wrong"),id.equals("grass"),60,Map.of(),EffectSupport.SUPPORTED,null,pokemon?(id.equals("evolved")?c.evolvedFamily:id.equals("basic")?c.family:Set.of()):Set.of(),pokemon?(id.equals("basic")&&c.printedTypes!=null?c.printedTypes:Set.of(id.equals("evolved")?c.evolvedType:id.equals("basic")?c.type:EnergyType.WATER)):Set.of()));
        }
        var pokemon=new HashMap<String,PokemonProfile>();
        for(String id:List.of("basic","evolved","wrong"))pokemon.put(id,new PokemonProfile(500,3,id.equals("evolved")?"basic":null,null,0,null,0,1,List.of(c.attack),List.of(),id.equals("evolved")?c.evolvedType:id.equals("basic")?c.type:EnergyType.WATER));
        var energies=new HashMap<>(Map.of(c.energy,energy(c.energy)));if(c.unplayedTypedEnergy)energies.put("unplayed-typed-energy",energy("xy4_en_112"));
        var catalog=new CombatCatalog(pokemon,Map.of("grass",EnergyType.GRASS),Map.of("stadium",c.stadium),energies);
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(x->x.definitionId().equals("basic")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,28);m.drawCardsByEffect(A,B,28);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->Arrays.stream(ids).allMatch(id->m.privateView(p).hand().stream().anyMatch(x->x.definitionId().equals(id)))))return m;
        }
        throw new AssertionError("Special Energy fixture");
    }
    static boolean count(ServerMatch m,UUID owner,UUID host,int value){return m.matchesEffectCondition(owner,host,new ConditionDefinition(ConditionType.ENERGY_COUNT,Map.of("value",""+value,"comparison","=="),List.of()));}
    static void evolve(ServerMatch m,UUID p,UUID host){m.evolveByEffect(p,host,hand(m,p,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);}
    static void suppress(ServerMatch m){m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);}
    public static void main(String[] args){endTurn();provision();restrictions();modifiers();opponentScope();drawHeal();search();warp();reactions();gift();schema();System.out.println("GeneratedRuleBatch30Test passed: generated Special Energy supply, live hosts/prizes, card versus units, restrictions, suppression, opponent damage scope, turn-end discard, own attachment events, damage/KO reactions, draw/heal and private search");}
    static void endTurn(){for(String id:List.of("me2_en_124","sm10_en_190"))for(String mode:List.of("active","bench","opponent","suppressed","moved","attack")){
        var m=game(id);UUID host=mode.equals("bench")?bench(m,A):active(m,A);boolean triple=id.equals("sm10_en_190");if(triple&&mode.equals("opponent")){m.endTurn(A);evolve(m,B,active(m,B));m.endTurn(B);}if(triple)evolve(m,A,host);var e=attach(m,id,A,host);
        check(count(m,A,host,triple?3:1),"Initial conditional supply "+id);if(!triple){evolve(m,A,host);check(count(m,A,host,3),"Ignition did not follow evolution");}
        if(mode.equals("moved")){m.moveAttachedEnergyByEffect(A,A,host,A,bench(m,A),List.of(e.instanceId()));check(m.privateView(A).discard().contains(e)==triple,"Continuous host restriction after movement");}
        if(mode.equals("opponent")){var other=hand(m,B,id);m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,other.instanceId(),B,active(m,B));m.endTurn(A);DamageTriggeredAbilityTest.finishTriggers(m);check(m.privateView(B).discard().contains(other)==triple,"Turn owner boundary "+id);if(!triple){check(m.activePokemonView(B).attachedEnergyIds().contains(other.instanceId()),"Ignition discarded on opponent turn");m.endTurn(B);check(m.privateView(B).discard().contains(other),"Ignition did not discard on owner turn");}}
        else {if(mode.equals("suppressed"))suppress(m);if(mode.equals("attack"))m.attack(A,"hit");else m.endTurn(A);check(m.privateView(A).discard().contains(e)!=mode.equals("suppressed"),"Turn discard/override "+mode);}
    }}
    static void provision(){for(String id:List.of("sm4_en_100","sv2_en_192"))for(PokemonFamily family:List.of(PokemonFamily.EX,PokemonFamily.EX_LEGACY,PokemonFamily.GX,PokemonFamily.V,PokemonFamily.RULE_BOX)){
        var c=new Config(id);c.family=family==PokemonFamily.RULE_BOX?Set.of(family):Set.of(family,PokemonFamily.RULE_BOX);c.evolvedFamily=c.family;var m=game(c);if(id.equals("sv2_en_192"))evolve(m,A,active(m,A));attach(m,id,A,active(m,A));check(count(m,A,active(m,A),1),"Prize tie activated special supply");
        m.knockOutPokemonByEffect(A,A,bench(m,A));check(UnrestrictedAttackPrizeTest.take(m,B)==1,"Prize setup");boolean applies=id.equals("sm4_en_100")?family!=PokemonFamily.EX_LEGACY&&family!=PokemonFamily.GX:family!=PokemonFamily.RULE_BOX&&family!=PokemonFamily.EX&&family!=PokemonFamily.EX_LEGACY&&family!=PokemonFamily.GX&&family!=PokemonFamily.V;
        check(count(m,A,active(m,A),applies?2:1),"Counter modern/legacy/GX family boundary "+id+family);
    }
        var c=new Config("sv2_en_192");var m=game(c);attach(m,c.energy,A,active(m,A));m.knockOutPokemonByEffect(A,A,bench(m,A));UnrestrictedAttackPrizeTest.take(m,B);check(count(m,A,active(m,A),1),"Reversal enabled on Basic");evolve(m,A,active(m,A));check(count(m,A,active(m,A),3),"Reversal not enabled on ordinary Evolution");suppress(m);check(count(m,A,active(m,A),1),"Override did not replace Reversal");m.discardStadiumByEffect(A);check(count(m,A,active(m,A),3),"Reversal failed to restore");
        m.knockOutPokemonByEffect(A,B,bench(m,B));UnrestrictedAttackPrizeTest.take(m,A);check(count(m,A,active(m,A),1),"Reversal cached old prize lead");
        var rapid=energy("swsh5_en_140");check(EnergyProfile.canPay(List.of(rapid),Map.of(EnergyType.WATER,1,EnergyType.FIGHTING,1)),"Rapid mixed units");check(!EnergyProfile.canPay(List.of(rapid),Map.of(EnergyType.WATER,2,EnergyType.FIGHTING,1)),"Rapid duplicated a unit");var single=energy("swsh5_en_141");check(!EnergyProfile.canPay(List.of(single),Map.of(EnergyType.FIGHTING,1,EnergyType.DARKNESS,1)),"Single Strike counted two units");
    }
    static void restrictions(){for(String mode:List.of("dual","printed","fallback","printed-only")){
        var c=new Config("xy4_en_112");c.type=mode.equals("dual")?EnergyType.FIRE:mode.equals("printed-only")?null:EnergyType.PSYCHIC;c.printedTypes=mode.equals("dual")?Set.of(EnergyType.FIRE,EnergyType.PSYCHIC):mode.equals("printed")?Set.of(EnergyType.WATER):mode.equals("printed-only")?Set.of(EnergyType.PSYCHIC):Set.of();var m=game(c);var e=hand(m,A,c.energy);if(mode.equals("printed"))reject(()->m.attachEnergy(A,e.instanceId(),active(m,A)));else{m.attachEnergy(A,e.instanceId(),active(m,A));check(m.activeRetreatCost(A)==1,"Dual-type or missing-tag Energy attachment "+mode);}
    }for(String id:List.of("swsh5_en_140","swsh5_en_141","xy6_en_097","xy4_en_112","xy3_en_103","xy5_en_143","xy10_en_115")){
        var c=new Config(id);c.family=id.equals("swsh5_en_140")?Set.of(PokemonFamily.RAPID_STRIKE):id.equals("swsh5_en_141")?Set.of(PokemonFamily.SINGLE_STRIKE):Set.of();c.type=switch(id){case "xy6_en_097"->EnergyType.DRAGON;case "xy4_en_112"->EnergyType.PSYCHIC;case "xy3_en_103"->EnergyType.GRASS;case "xy5_en_143"->EnergyType.METAL;default->EnergyType.FIGHTING;};var m=game(c);var e=hand(m,A,id);var wrong=hand(m,A,"wrong");m.playBasicPokemonToBench(A,wrong.instanceId());reject(()->m.attachEnergy(A,e.instanceId(),wrong.instanceId()));check(m.privateView(A).hand().contains(e),"Illegal attachment mutated hand");m.attachEnergy(A,e.instanceId(),active(m,A));check(m.activePokemonView(A).attachedEnergyIds().contains(e.instanceId()),"Legal host rejected");evolve(m,A,active(m,A));check(m.privateView(A).discard().contains(e),"Off-type/style Evolution retained restricted Energy "+id);
    }
        var unknown=new Config("swsh9_en_151");unknown.type=null;unknown.printedTypes=Set.of();unknown.unplayedTypedEnergy=true;check(game(unknown).phase()==MatchPhase.TURN,"Unplayed catalog Energy blocked unrelated match");var required=new Config("xy4_en_112");required.type=null;required.printedTypes=Set.of();reject(()->game(required));
        var m=game("sm10_en_190");var e=hand(m,A,"sm10_en_190");reject(()->m.attachEnergy(A,e.instanceId(),active(m,A)));check(m.privateView(A).hand().contains(e),"Triple attached illegally");
        var c=new Config("xy6_en_097");c.type=EnergyType.DRAGON;c.evolvedType=EnergyType.DRAGON;var n=game(c);var paid=attach(n,c.energy,A,active(n,A));check(count(n,A,active(n,A),2),"Double Dragon units");n.attachEnergyByEffect(A,A,EffectSourceZone.HAND,hand(n,A,"grass").instanceId(),A,active(n,A));n.retreat(A,bench(n,A),List.of(paid.instanceId(),n.activePokemonView(A).attachedEnergyIds().stream().filter(x->!x.equals(paid.instanceId())).findFirst().orElseThrow()));check(n.privateView(A).discard().contains(paid),"Retreat physical Special Energy");
    }
    static void modifiers(){for(String id:List.of("me5_en_084","swsh3_en_176","xy10_en_115","swsh5_en_141","swsh9_en_151"))for(boolean benchDamage:List.of(false,true)){
        var c=new Config(id);c.type=id.equals("me5_en_084")?EnergyType.LIGHTNING:id.equals("xy10_en_115")?EnergyType.FIGHTING:EnergyType.COLORLESS;c.family=Set.of(PokemonFamily.SINGLE_STRIKE);c.attack=GeneratedRuleBatch8Test.hit(100,benchDamage);var m=game(c);attach(m,id,A,active(m,A));m.attack(A,"hit");int actual=benchDamage?m.benchPokemonView(B).getFirst().damage():m.lastAttack().finalDamage();check(actual==(id.equals("swsh9_en_151")?80:benchDamage?100:120),"Active-only/whole-field damage "+id);}
        for(String id:List.of("me3_en_086","swsh3_en_174","me4_en_085","swsh3_en_175","swsh4_en_164","xy5_en_143","xy4_en_112"))for(boolean matching:List.of(false,true)){
            boolean restricted=id.startsWith("xy");if(restricted&&!matching)continue;var c=new Config(id);c.type=!matching?EnergyType.COLORLESS:switch(id){case "me3_en_086"->EnergyType.GRASS;case "swsh3_en_174"->EnergyType.FIRE;case "me4_en_085","xy5_en_143"->EnergyType.METAL;case "swsh3_en_175"->EnergyType.DARKNESS;case "xy4_en_112"->EnergyType.PSYCHIC;default->EnergyType.FIGHTING;};var m=game(c);var host=active(m,A);attach(m,id,A,host);
            if(id.equals("me3_en_086")||id.equals("swsh3_en_174")){check(m.activePokemonView(A).hitPoints()==(matching?520:500),"Typed HP");attach(m,id,A,host);check(m.activePokemonView(A).hitPoints()==(matching?540:500),"HP stack");suppress(m);check(m.activePokemonView(A).hitPoints()==500,"HP override");}
            else if(Set.of("me4_en_085","swsh3_en_175","xy4_en_112").contains(id)){check(m.activeRetreatCost(A)==(matching?id.equals("xy4_en_112")?1:0:3),"Retreat modifier");if(!restricted){evolve(m,A,host);check(m.activeRetreatCost(A)==3,"Retreat cached host type");}}
            else{m.endTurn(A);m.attack(B,"hit");check(m.lastAttack().finalDamage()==(matching?id.equals("xy5_en_143")?90:80:100),"Typed reduction");}
        }
        var c=new Config("swsh3_en_176");c.type=EnergyType.COLORLESS;c.attack=GeneratedRuleBatch8Test.hit(0,false);var z=game(c);attach(z,c.energy,A,active(z,A));z.attack(A,"hit");check(z.activePokemonView(B).damage()==0,"Energy bonus manufactured damage");
    }
    static void opponentScope(){for(String id:List.of("swsh9_en_151","swsh4_en_164","xy5_en_143"))for(boolean primary:List.of(false,true)){
        var c=new Config(id);c.type=id.equals("xy5_en_143")?EnergyType.METAL:EnergyType.FIGHTING;
        c.attack=primary?BundledCombatData.attack(com.google.gson.JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"范围回归\",\"energyCost\":{},\"baseDamage\":100,\"effectSupport\":\"SUPPORTED\",\"damageTarget\":\"ALL_POKEMON\"}").getAsJsonObject(),EffectRegistry.standard()):DamageTriggeredAbilityTest.attack(100,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"bench_damage\",\"target\":\"OWN_BENCH\",\"amount\":100},{\"type\":\"bench_damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":100}]}]");
        var m=game(c);if(id.equals("swsh9_en_151"))attach(m,id,A,active(m,A));else{attach(m,id,A,bench(m,A));attach(m,id,B,bench(m,B));}m.attack(A,"hit");check(m.benchPokemonView(A).getFirst().damage()==100,"Opponent-only numeric modifier affected own Bench "+id+primary);check(m.benchPokemonView(B).getFirst().damage()==(id.equals("xy5_en_143")?90:80),"Opponent scope omitted opposing Bench "+id+primary);
    }
        for(String mode:List.of("stack","suppressed","ability-disabled")){var c=new Config("swsh9_en_151");var m=game(c);attach(m,c.energy,A,active(m,A));attach(m,c.energy,A,active(m,A));if(mode.equals("suppressed"))suppress(m);if(mode.equals("ability-disabled"))m.disableAbilityEffectsByEffect(A,A,active(m,A));m.attack(A,"hit");check(m.lastAttack().finalDamage()==(mode.equals("suppressed")?100:60),"Energy modifier source/stack "+mode);}
    }
    static void schema(){for(String args:List.of("\"opponent_only\":false","\"opponent_only\":\"yes\"","\"opponent_only\":true,\"opponent_active_only\":true","\"opponent_only\":true,\"direction\":\"OTHER\""))reject(()->SpecialEnergyTemplateTest.energy("{\"energyUnits\":[[\"COLORLESS\"]],\"effects\":[{\"type\":\"attack_damage_modifier\",\"direction\":\"DEALT\",\"amount\":-20,"+args+"}]}"));reject(()->SpecialEnergyTemplateTest.energy("{\"energyUnits\":[[\"COLORLESS\"]],\"effects\":[{\"type\":\"hit_points_modifier\",\"amount\":20,\"opponent_only\":true}]}"));}
    static void drawHeal(){for(String id:List.of("sv8_en_191","swsh2_en_173","sv4_en_182","xy3_en_103"))for(String mode:List.of("active","bench","wrong","discard","suppressed")){
        boolean heal=id.equals("sv4_en_182")||id.equals("xy3_en_103");var c=new Config(id);c.type=id.equals("swsh2_en_173")?EnergyType.LIGHTNING:id.equals("xy3_en_103")?EnergyType.GRASS:EnergyType.COLORLESS;if(mode.equals("wrong")&&id.equals("xy3_en_103"))continue;if(mode.equals("wrong"))c.type=EnergyType.WATER;var m=game(c);UUID host=mode.equals("bench")?bench(m,A):active(m,A);m.placeDamageCountersByEffect(A,A,host,5);if(mode.equals("suppressed"))suppress(m);var e=hand(m,A,id);int before=m.privateView(A).hand().size();
        if(mode.equals("discard")){m.discardHandCardsByEffect(A,A,List.of(e.instanceId()));before--;m.attachEnergyByEffect(A,A,EffectSourceZone.DISCARD,e.instanceId(),A,host);}else m.attachEnergy(A,e.instanceId(),host);
        boolean applies=!mode.equals("discard")&&!mode.equals("suppressed")&&(!id.equals("swsh2_en_173")||!mode.equals("wrong"));int current=(host.equals(active(m,A))?m.activePokemonView(A):m.benchPokemonView(A).getFirst()).damage();check(current==(heal&&applies?20:50),"Hand heal host/source "+id+mode);check(m.privateView(A).hand().size()==before-(mode.equals("discard")?0:1)+(applies&&!heal?id.equals("sv8_en_191")?4:2:0),"Hand draw origin/type "+id+mode);
        int after=m.privateView(A).hand().size();m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,hand(m,A,"grass").instanceId(),A,host);check(m.privateView(A).hand().size()==after-1,"Other Energy activated listener");
    }}
    static void search(){for(boolean matching:List.of(false,true))for(boolean failFind:List.of(false,true)){
        var c=new Config("me3_en_088");c.type=matching?EnergyType.PSYCHIC:EnergyType.FIRE;var m=game(c);attach(m,c.energy,A,bench(m,A));check(m.hasPendingEffectChoice()==matching,"Telepathic host type");if(matching){var p=m.effectCardPrompt(A);check(p.minimum()==0&&p.maximum()==2&&p.candidates().stream().allMatch(x->x.definitionId().equals("basic")),"Telepathic private Basic Psychic filter");reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of()));m.submitEffectCards(A,p.resolutionId(),p.step(),failFind?List.of():p.candidates().stream().limit(2).map(CardInstance::instanceId).toList());check(m.benchPokemonView(A).size()==(failFind?1:3),"Telepathic placement/zero");}}
    }
    static void warp(){for(String mode:List.of("active","bench","discard","empty")){
        var m=game("sm3-5_en_070");if(mode.equals("empty")){m.knockOutPokemonByEffect(A,A,bench(m,A));UnrestrictedAttackPrizeTest.take(m,B);}var old=active(m,A);var host=mode.equals("bench")?bench(m,A):old;var e=hand(m,A,"sm3-5_en_070");if(mode.equals("discard")){m.discardHandCardsByEffect(A,A,List.of(e.instanceId()));m.attachEnergyByEffect(A,A,EffectSourceZone.DISCARD,e.instanceId(),A,host);}else m.attachEnergy(A,e.instanceId(),host);
        if(mode.equals("active")){var p=m.effectPokemonPrompt(A);reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),old));m.chooseEffectPokemon(A,p.resolutionId(),p.step(),bench(m,A));check(!active(m,A).equals(old)&&m.benchPokemonView(A).stream().anyMatch(x->x.pokemonId().equals(old)&&x.attachedEnergyIds().contains(e.instanceId())),"Warp switched or moved wrong object");reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),old));}else check(active(m,A).equals(old)&&!m.hasPendingEffectChoice(),"Warp false trigger "+mode);
    }}
    static void reactions(){for(String id:List.of("sv9_en_159","swsh2_en_172","swsh6_en_158"))for(String mode:List.of("hit","bench","counters","suppressed","wrong","lethal")){
        var c=new Config(id);c.type=mode.equals("wrong")?EnergyType.WATER:EnergyType.PSYCHIC;c.attack=GeneratedRuleBatch8Test.hit(mode.equals("lethal")?500:100,mode.equals("bench"));var m=game(c);UUID host=mode.equals("bench")?bench(m,A):active(m,A);attach(m,id,A,host);if(mode.equals("suppressed"))suppress(m);int before=m.privateView(A).hand().size();if(mode.equals("counters")){m.placeDamageCountersByEffect(A,A,host,2);check(m.privateView(A).hand().size()==before&&m.activePokemonView(B).damage()==0,"Damage counters triggered Energy");continue;}m.endTurn(A);m.attack(B,"hit");DamageTriggeredAbilityTest.finishTriggers(m);boolean applies=!Set.of("bench","suppressed").contains(mode)&&(!id.equals("swsh2_en_172")||!mode.equals("wrong"));check(m.activePokemonView(B).damage()==(!id.equals("swsh6_en_158")&&applies?20:0),"Damage reaction actual source/type/lethal "+id+mode);int automaticDraw=mode.equals("lethal")?0:1;check(m.privateView(A).hand().size()==before+(id.equals("swsh6_en_158")&&applies?1:0)+automaticDraw,"Lucky draw count "+mode);
    }}
    static void gift(){for(String mode:List.of("active","bench","counters","direct","suppressed")){
        var c=new Config("swsh11_en_171");c.attack=GeneratedRuleBatch8Test.hit(500,mode.equals("bench"));var m=game(c);UUID host=mode.equals("bench")?bench(m,A):active(m,A);attach(m,c.energy,A,host);if(mode.equals("suppressed"))suppress(m);m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().map(CardInstance::instanceId).toList());check(m.privateView(A).hand().isEmpty(),"Gift setup");if(mode.equals("counters"))m.placeDamageCountersByEffect(A,A,host,50);else if(mode.equals("direct"))m.knockOutPokemonByEffect(A,A,host);else{m.endTurn(A);m.attack(B,"hit");DamageTriggeredAbilityTest.finishTriggers(m);}check(m.privateView(A).hand().size()==(Set.of("active","bench").contains(mode)?7:0),"Gift KO cause/Bench/suppression "+mode);
    }}
}

