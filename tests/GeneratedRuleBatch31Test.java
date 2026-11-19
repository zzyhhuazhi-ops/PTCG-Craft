import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Generated Energy protection reads actual source, current host and damage recipient. */
public final class GeneratedRuleBatch31Test {
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
        boolean weakness;List<AbilityDefinition> abilities=List.of();
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
        for(String id:List.of("basic","evolved","wrong"))pokemon.put(id,new PokemonProfile(500,3,id.equals("evolved")?"basic":null,c.weakness?EnergyType.COLORLESS:null,c.weakness?2:0,null,0,1,List.of(c.attack),c.abilities,id.equals("evolved")?c.evolvedType:id.equals("basic")?c.type:EnergyType.WATER));
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
    static void swing(ServerMatch m){m.attack(A,"hit");if(!m.hasPendingEffectChoice())return;var p=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,p.resolutionId(),p.step(),p.targets().getFirst().pokemonId());}
    static void suppress(ServerMatch m){m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);}
    static void evolve(ServerMatch m,UUID p,UUID host){m.evolveByEffect(p,host,hand(m,p,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);}
    static AttackDefinition hit(int amount,String extra){return DamageTriggeredAbilityTest.attack(amount,extra+",\"damageType\":\"COLORLESS\"");}
    static AttackDefinition counters(String target){String effect="{\"type\":\"damage_counter\",\"target\":\""+(target.endsWith("BENCH")?"CHOSEN_POKEMON":target)+"\",\"amount\":2}";if(target.endsWith("BENCH"))effect="{\"type\":\"select_pokemon\",\"target\":\""+(target.startsWith("OWN")?"SELECT_OWN_POKEMON":"SELECT_OPPONENT_POKEMON")+"\",\"position\":\"BENCH\",\"effects\":["+effect+"]}";return hit(100,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":["+effect+"]}]");}
    static AbilityDefinition counterAbility(String target){return BundledCombatData.ability(com.google.gson.JsonParser.parseString("{\"id\":\"counter\",\"nameZh\":\"来源检查\",\"kind\":\"ACTIVATED_ONCE_PER_TURN\",\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\""+target+"\",\"amount\":2}]}").getAsJsonObject(),EffectRegistry.standard());}
    public static void main(String[] args){attackEffects();sourceRelations();abilityGuard();weakness();shadow();vGuard();schema();System.out.println("GeneratedRuleBatch31Test passed: attached Energy effect/damage guards, source/owner/type/position, no-Weakness versus Shred, Energy suppression, live evolution and per-host non-stacking V reduction");}
    static EnergyType type(String id){return switch(id){case "me3_en_087"->EnergyType.FIGHTING;case "swsh4_en_165"->EnergyType.WATER;case "xy5_en_144"->EnergyType.FAIRY;case "swsh4_en_163"->EnergyType.METAL;case "xy7_en_083"->EnergyType.LIGHTNING;case "me5_en_083"->EnergyType.DARKNESS;default->EnergyType.COLORLESS;};}
    static void attackEffects(){for(String id:List.of("sv5_en_161","me3_en_087","swsh4_en_165","xy5_en_144"))for(String mode:List.of("normal","wrong","suppressed","abilities-disabled","dual")){
        if(mode.equals("wrong")&&id.equals("xy5_en_144"))continue;var c=new Config(id);c.type=mode.equals("wrong")?EnergyType.GRASS:type(id);if(mode.equals("dual")){c.type=EnergyType.GRASS;c.printedTypes=Set.of(EnergyType.GRASS,type(id));}c.attack=counters("OPPONENT_ACTIVE");var m=game(c);attach(m,id,B,active(m,B));if(mode.equals("suppressed"))suppress(m);if(mode.equals("abilities-disabled"))m.disableAbilityEffectsByEffect(A,B,active(m,B));swing(m);boolean guarded=!mode.equals("suppressed")&&(!mode.equals("wrong")||id.equals("sv5_en_161"));check(m.activePokemonView(B).damage()==(guarded?100:120),"Energy attack effect guard also blocked damage or used wrong host "+id+mode);
    }
        var c=new Config("sv5_en_161");c.attack=counters("OPPONENT_BENCH");var m=game(c);var host=bench(m,B);attach(m,c.energy,B,host);swing(m);check(m.benchPokemonView(B).getFirst().damage()==0,"Mist failed on Bench");
        var existing=game("sv5_en_161");m=existing;m.applySpecialConditionByEffect(A,B,SpecialCondition.CONFUSED);attach(m,"sv5_en_161",B,active(m,B));check(m.activePokemonView(B).conditions().contains(SpecialCondition.CONFUSED),"Effect protection removed an existing condition");
    }
    static void sourceRelations(){var c=new Config("sv5_en_161");c.attack=counters("OWN_BENCH");var m=game(c);attach(m,c.energy,A,bench(m,A));swing(m);check(m.benchPokemonView(A).getFirst().damage()==20,"Mist blocked own attack effects");
        c=new Config("sv5_en_161");c.abilities=List.of(counterAbility("OPPONENT_ACTIVE"));m=game(c);attach(m,c.energy,B,active(m,B));m.useAbility(A,active(m,A),"counter");check(m.activePokemonView(B).damage()==20,"Mist blocked Ability instead of only attack effects");
        var physical=game("sv5_en_161");attach(physical,"sv5_en_161",B,active(physical,B));physical.placeDamageCountersByEffect(A,B,active(physical,B),2);check(physical.activePokemonView(B).damage()==20,"Mist blocked non-attack rule effect");
    }
    static void abilityGuard(){for(String mode:List.of("opponent","own","suppressed","ability-disabled","attack","devolved")){
        var c=new Config("swsh8_en_244");c.family=Set.of(PokemonFamily.FUSION_STRIKE);c.abilities=List.of(counterAbility(mode.equals("own")?"SELF":"OPPONENT_ACTIVE"));c.attack=counters("OPPONENT_ACTIVE");var m=game(c);UUID owner=mode.equals("own")?A:B;var e=attach(m,c.energy,owner,active(m,owner));if(mode.equals("suppressed"))suppress(m);if(mode.equals("ability-disabled"))m.disableAbilityEffectsByEffect(A,B,active(m,B));
        if(mode.equals("devolved")){m.endTurn(A);evolve(m,B,active(m,B));check(m.privateView(B).discard().contains(e),"Fusion Energy ignored changed Battle Style");m.endTurn(B);}
        if(mode.equals("attack")){swing(m);check(m.activePokemonView(B).damage()==120,"Fusion incorrectly blocked attack effects");}else{m.useAbility(A,active(m,A),"counter");check(m.activePokemonView(owner).damage()==(Set.of("opponent","ability-disabled").contains(mode)?0:20),"Fusion source or Energy suppression "+mode);}
    }}
    static void weakness(){for(String id:List.of("sm11_en_213","swsh4_en_163","xy7_en_083"))for(String mode:List.of("normal","shred","wrong","suppressed","mixed","bench","removed")){
        if(mode.equals("wrong")&&id.equals("xy7_en_083"))continue;var c=new Config(id);c.type=mode.equals("wrong")?EnergyType.GRASS:type(id);c.weakness=true;c.attack=hit(100,mode.equals("shred")?",\"ignoresActiveDefenderDamageEffects\":true":mode.equals("mixed")?",\"damageTarget\":\"ALL_OPPONENT_POKEMON\"":mode.equals("bench")?",\"damageTarget\":\"ALL_OPPONENT_BENCH\"":"");var m=game(c);var e=attach(m,id,B,active(m,B));if(mode.equals("suppressed"))suppress(m);if(mode.equals("removed"))m.moveAttachedEnergyByEffect(A,B,active(m,B),B,bench(m,B),List.of(e.instanceId()));swing(m);boolean protectedWeakness=!Set.of("suppressed","removed").contains(mode)&&(!mode.equals("wrong")||id.equals("sm11_en_213"));check(m.activePokemonView(B).damage()==(mode.equals("bench")?0:protectedWeakness?100:200),"No-Weakness boundary "+id+mode);if(mode.equals("bench")||mode.equals("mixed"))check(m.benchPokemonView(B).getFirst().damage()==100,"Bench accidentally used Weakness");
    }}
    static void shadow(){for(String mode:List.of("bench","active","wrong","suppressed","own","counters")){
        var c=new Config("me5_en_083");c.type=mode.equals("wrong")?EnergyType.COLORLESS:EnergyType.DARKNESS;c.attack=mode.equals("counters")?counters("OPPONENT_BENCH"):mode.equals("own")?hit(100,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"bench_damage\",\"target\":\"OWN_BENCH\",\"amount\":100}]}]"):hit(100,mode.equals("active")?"":",\"damageTarget\":\"ALL_OPPONENT_BENCH\"");var m=game(c);UUID owner=mode.equals("own")?A:B;attach(m,c.energy,owner,mode.equals("active")?active(m,owner):bench(m,owner));if(mode.equals("suppressed"))suppress(m);swing(m);int actual=mode.equals("active")?m.activePokemonView(owner).damage():m.benchPokemonView(owner).getFirst().damage();check(actual==(mode.equals("bench")?0:mode.equals("counters")?20:100),"Shadow source/type/position "+mode);
    }}
    static void vGuard(){for(PokemonFamily family:List.of(PokemonFamily.V,PokemonFamily.VMAX,PokemonFamily.VSTAR,PokemonFamily.GX,PokemonFamily.EX,PokemonFamily.EX_LEGACY))for(String mode:List.of("single","two","both-hosts","shred","suppressed")){
        var c=new Config("swsh12_en_169");c.family=family==PokemonFamily.VMAX||family==PokemonFamily.VSTAR?Set.of(PokemonFamily.V,family):Set.of(family);c.attack=hit(100,mode.equals("shred")?",\"ignoresActiveDefenderDamageEffects\":true":mode.equals("both-hosts")?",\"damageTarget\":\"ALL_OPPONENT_POKEMON\"":"");var m=game(c);attach(m,c.energy,B,active(m,B));if(mode.equals("two"))attach(m,c.energy,B,active(m,B));if(mode.equals("both-hosts"))attach(m,c.energy,B,bench(m,B));if(mode.equals("suppressed"))suppress(m);swing(m);boolean reduced=Set.of(PokemonFamily.V,PokemonFamily.VMAX,PokemonFamily.VSTAR).contains(family)&&!Set.of("shred","suppressed").contains(mode);check(m.activePokemonView(B).damage()==(reduced?70:100),"V guard family/non-stack "+family+mode);if(mode.equals("both-hosts"))check(m.benchPokemonView(B).getFirst().damage()==(reduced?70:100),"V group incorrectly shared across hosts");
    }
        var c=new Config("swsh12_en_169");c.family=Set.of(PokemonFamily.V);c.attack=hit(100,",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"bench_damage\",\"target\":\"OWN_BENCH\",\"amount\":100}]}]");var m=game(c);attach(m,c.energy,A,bench(m,A));swing(m);check(m.benchPokemonView(A).getFirst().damage()==100,"V guard blocked own attack");
        var a=new PassiveAttackGuard.Values(false,20,Map.of("a",30));var b=new PassiveAttackGuard.Values(false,10,Map.of("a",30,"b",10));check(a.plus(b).reduction()==70,"Nonstacking reductions did not preserve separate groups/additive effects");
    }
    static void schema(){for(String extra:List.of("\"non_stacking_group\":\"\"","\"non_stacking_group\":\"bad group\"","\"non_stacking_group\":\"x\",\"mode\":\"PREVENT\""))reject(()->SpecialEnergyTemplateTest.energy("{\"energyUnits\":[[\"COLORLESS\"]],\"effects\":[{\"type\":\"passive_attack_guard\",\"mode\":\"REDUCE\",\"amount\":30,"+extra+"}]}"));
        for(String effect:List.of("{\"type\":\"no_weakness\",\"amount\":1}","{\"type\":\"no_weakness\",\"target\":\"ALL_OWN_POKEMON\"}","{\"type\":\"no_weakness\",\"conditions\":[{\"type\":\"HAS_ABILITIES\"}]}","{\"type\":\"passive_effect_guard\",\"source\":\"ATTACK\",\"from\":\"OPPONENT\",\"scope\":\"EFFECTS_ONLY\",\"conditions\":[{\"type\":\"ENERGY_COUNT\",\"value\":1}]}"))reject(()->SpecialEnergyTemplateTest.energy("{\"energyUnits\":[[\"COLORLESS\"]],\"effects\":["+effect+"]}"));
    }
}
