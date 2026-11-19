import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Source facts belong to the actual attacker at damage calculation, not at declaration or after discarding. */
public final class DynamicAttackSourceGuardTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final String GUARD="{\"type\":\"passive_attack_guard\",\"target\":\"ALL_OWN_POKEMON\",\"mode\":\"PREVENT\",\"source_position\":\"BENCH\",\"attacker_energy_max\":2}";
    static final String WALL="{\"type\":\"prevent_damage\",\"target\":\"SELF\",\"source\":\"ATTACK\",\"from\":\"OPPONENT\",\"scope\":\"DAMAGE_ONLY\",\"duration\":\"OPPONENT_TURN_END\",\"attacker_status\":\"BURNED\"}";
    static final String DISCARD="{\"type\":\"discard_energy\",\"target\":\"SELF\",\"all\":true}";
    static final String BENCH="{\"type\":\"bench_damage\",\"target\":\"OPPONENT_BENCH\",\"amount\":90}";
    static void check(boolean b,String s){GeneratedRuleBatch8Test.check(b,s);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static void attach(ServerMatch m,UUID owner,UUID host,String id){var c=hand(m,owner,id);m.attachEnergyByEffect(m.currentPlayer(),owner,EffectSourceZone.HAND,c.instanceId(),owner,host);}
    static AbilityDefinition guard(String text){return new AbilityDefinition("guard","Guard",new AbilityEffect(AbilityEffect.Opcode.COMPOSED,0),EffectSupport.SUPPORTED,null,AbilityDefinition.Kind.PASSIVE,
        new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.PASSIVE,text))));}
    static AttackDefinition hit(String before,String after){return GeneratedRuleBatch8Test.hit(90,false).withEffectProgram(BeforeDamageAttackTest.attack(before,"",after).effectProgram());}
    static ServerMatch game(AttackDefinition attack,EnergyProfile special,AbilityDefinition... abilities){return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),special,attack,List.of(abilities));}
    static ServerMatch game(AttackDefinition attack,AbilityDefinition... abilities){return game(attack,EnergyProfile.fixed(EnergyType.COLORLESS,2),abilities);}
    static ServerMatch positionGame(AttackDefinition attack,AbilityDefinition ability){
        return positionGame(List.of(attack),ability);
    }
    static ServerMatch positionGame(List<AttackDefinition> attacks,AbilityDefinition ability){
        var definitions=new CardDatabase(List.of(CopyAttackTest.card("front",true),CopyAttackTest.card("back",true),CopyAttackTest.card("energy",false)));
        var catalog=new CombatCatalog(Map.of("front",new PokemonProfile(500,0,null,null,0,null,0,1,attacks,List.of(),EnergyType.COLORLESS),
            "back",new PokemonProfile(500,0,null,null,0,null,0,1,attacks,List.of(ability),EnergyType.COLORLESS)),Map.of("energy",EnergyType.COLORLESS));
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,new String[]{"front","back","energy"}[i%3]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,definitions,catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->List.of("front","back").stream().anyMatch(id->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals(id)))))continue;
            for(UUID p:List.of(A,B)){m.chooseOpeningActive(p,hand(m,p,"front").instanceId());m.placeOpeningBench(p,hand(m,p,"back").instanceId());m.startAfterOpening(p);}
            OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,20);m.drawCardsByEffect(A,B,20);m.endTurn(A);m.endTurn(B);return m;
        }throw new AssertionError("No position fixture");
    }
    public static void main(String[] args){unitsAndSource();damageWindow();statusWindow();copiedActor();damageBypass();contracts();
        System.out.println("DynamicAttackSourceGuardTest passed: authoritative supplied units/status, distinct source position, after-before-damage checkpoint, secondary damage through discard, actual actor copy, expiry and malformed proof rejection");}
    static void unitsAndSource(){
        for(String mode:List.of("zero","two-cards","two-units","three","suppressed","active-only","recipient-active","recipient-bench")){
            var m=game(hit("",mode.equals("recipient-bench")?BENCH:""),guard(GUARD));
            if(mode.equals("two-cards"))for(int i=0;i<2;i++)attach(m,A,active(m,A),"energy");
            if(Set.of("two-units","three").contains(mode))attach(m,A,active(m,A),"special");
            if(mode.equals("three"))attach(m,A,active(m,A),"energy");
            if(Set.of("suppressed","active-only").contains(mode))m.disableAbilityEffectsByEffect(A,B,bench(m,B));
            m.attack(A,"hit");int expected=Set.of("three","suppressed","active-only").contains(mode)?90:0;
            check(m.lastAttack().finalDamage()==expected,"Source position / units wrong: "+mode);
            if(mode.equals("recipient-bench"))check(m.benchPokemonView(B).getFirst().damage()==0,"Bench recipient not protected");
        }
        var m=positionGame(hit("{\"type\":\"force_switch\",\"target\":\"SELECT_OPPONENT_POKEMON\"}",""),guard(GUARD));
        // Only the original Bench has a working guard. Moving it Active before damage must disable it.
        m.attack(A,"hit");var p=m.effectPokemonPrompt(B);m.chooseEffectPokemon(B,p.resolutionId(),p.step(),p.targets().getFirst().pokemonId());
        check(m.lastAttack().finalDamage()==90,"Guard source position frozen at declaration");
    }
    static void damageWindow(){
        for(boolean genuineBefore:List.of(false,true)){
            var m=game(hit(genuineBefore?DISCARD:"",(genuineBefore?"":DISCARD+",")+BENCH),guard(GUARD));
            attach(m,A,active(m,A),"special");attach(m,A,active(m,A),"energy");m.attack(A,"hit");
            check(m.lastAttack().finalDamage()==(genuineBefore?0:90)&&m.benchPokemonView(B).getFirst().damage()==(genuineBefore?0:90),"Damage snapshot used declaration or post-discard units");
            check(m.activePokemonView(A).attachedEnergyIds().isEmpty(),"Discard did not resolve");
        }
        var turbo=DynamicPassiveModifierTest.energy("{\"type\":\"attack_damage_modifier\",\"direction\":\"DEALT\",\"amount\":-20}");
        for(boolean genuineBefore:List.of(false,true)){
            var m=game(hit(genuineBefore?DISCARD:"",(genuineBefore?"":DISCARD+",")+BENCH),turbo);attach(m,A,active(m,A),"special");m.attack(A,"hit");
            check(m.lastAttack().finalDamage()==(genuineBefore?90:70)&&m.benchPokemonView(B).getFirst().damage()==(genuineBefore?90:70),"Damage-dealt modifier lost on ordinary discard or retained before damage");
            // A fresh attack must not reuse the previous attack's source snapshot.
            m.endTurn(B);m.attack(A,"hit");check(m.lastAttack().finalDamage()==90&&m.benchPokemonView(B).getFirst().damage()==(genuineBefore?180:160),"Previous attack source snapshot leaked");
        }
    }
    static void statusWindow(){
        for(boolean genuineBefore:List.of(false,true)){
            String clear="{\"type\":\"clear_status\",\"target\":\"SELF\",\"statuses\":\"BURNED\"}";
            var m=game(hit(genuineBefore?clear:"",(genuineBefore?"":clear+",")+BENCH),guard(GUARD.replace("\"attacker_energy_max\":2","\"attacker_status\":\"BURNED\"")));
            m.applySpecialConditionByEffect(A,A,SpecialCondition.BURNED);m.attack(A,"hit");
            check(m.lastAttack().finalDamage()==(genuineBefore?90:0)&&m.benchPokemonView(B).getFirst().damage()==(genuineBefore?90:0),"Secondary damage used post-effect status or declaration status");
        }
        for(String mode:List.of("burned","healthy","cured-before","cured-after","effects")){
            String clear="{\"type\":\"clear_status\",\"target\":\"SELF\",\"statuses\":\"BURNED\"}";
            String counter=mode.equals("effects")?",{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2}":"";
            var a=hit(mode.equals("cured-before")?clear:"",WALL+counter+(mode.equals("cured-after")?","+clear:"") );var m=game(a);
            if(Set.of("cured-before","cured-after").contains(mode))m.applySpecialConditionByEffect(A,A,SpecialCondition.BURNED);
            m.attack(A,"hit");
            if(!mode.equals("healthy"))m.applySpecialConditionByEffect(B,B,SpecialCondition.BURNED);
            int before=m.activePokemonView(A).damage();m.attack(B,"hit");int expected=Set.of("healthy","cured-before").contains(mode)?90:0;
            check(m.lastAttack().finalDamage()==expected,"Burned attacker guard used wrong status window "+mode);
            if(mode.equals("effects"))check(m.activePokemonView(A).damage()==before+20,"Damage-only guard blocked counters");
            // The protection expires at the actual opponent turn's end.
            m.endTurn(A);if(!m.activePokemonView(B).conditions().contains(SpecialCondition.BURNED))m.applySpecialConditionByEffect(B,B,SpecialCondition.BURNED);
            m.attack(B,"hit");check(m.lastAttack().finalDamage()==90,"Lava Wall persisted beyond next opposing turn");
        }
    }
    static void copiedActor(){
        var a=hit("",WALL);var m=CopyAttackTest.fixture(CopyAttackTest.copier(),a,EnergyType.COLORLESS,0);CopyAttackTest.pay(m);var choice=CopyAttackTest.choice(m);CopyAttackTest.copy(m,choice,List.of(),List.of());
        m.applySpecialConditionByEffect(CopyAttackTest.B,CopyAttackTest.B,SpecialCondition.BURNED);m.attack(CopyAttackTest.B,a.id());check(m.lastAttack().finalDamage()==0,"Copied protection installed on provider instead of actor");
        for(String mode:List.of("energy","burned-provider","burned-actor")){
            var copied=hit("","");var g=mode.equals("energy")?GUARD:GUARD.replace("\"attacker_energy_max\":2","\"attacker_status\":\"BURNED\"");
            var match=positionGame(List.of(CopyAttackTest.copier(),copied),guard(g));attach(match,A,active(match,A),"energy");
            for(int i=0;i<3;i++)attach(match,B,active(match,B),"energy");
            if(!mode.equals("energy"))match.applySpecialConditionByEffect(A,mode.equals("burned-provider")?B:A,SpecialCondition.BURNED);
            var c=match.copyAttackOptions(A,"copy").stream().filter(v->v.attackId().equals("hit")).findFirst().orElseThrow();
            match.copyAttackWithEnergyDiscards(A,"copy",c.pokemonId(),c.entrySequence(),c.attackId(),List.of(),List.of());
            check(match.lastAttack().finalDamage()==(mode.equals("burned-provider")?90:0),"Source filter read copied provider facts: "+mode);
        }
    }
    static void damageBypass(){
        var piercing=BundledCombatData.attack(JsonParser.parseString("{\"id\":\"pierce\",\"nameZh\":\"穿透\",\"energyCost\":{},\"baseDamage\":90,\"effectSupport\":\"SUPPORTED\",\"ignoresActiveDefenderDamageEffects\":true}").getAsJsonObject(),EffectRegistry.standard());
        var m=game(piercing,guard(GUARD));m.attack(A,piercing.id());check(m.lastAttack().finalDamage()==90,"Defender damage bypass blocked by dynamic passive guard: "+m.lastAttack().finalDamage());
        var lava=hit("",WALL);var p=positionGame(List.of(lava,piercing),guard(GUARD));
        for(int i=0;i<3;i++)attach(p,A,active(p,A),"energy");p.attack(A,"hit");p.applySpecialConditionByEffect(B,B,SpecialCondition.BURNED);p.attack(B,piercing.id());check(p.lastAttack().finalDamage()==90,"Burned-attacker temporary protection ignored damage bypass");
    }
    static void contracts(){
        var card=BundledRules.loadCardDefinitions().find("me5_en_007").orElseThrow();var profile=new PokemonProfile(140,4,null,null,0,null,0,1,List.of());
        var filter=AttackSourceFilter.from(Map.of("attacker_energy_max","2","attacker_status","BURNED"));
        check(!filter.matches(card,"Heatran",false)&&!filter.matches(card,"Heatran",false,profile),"Missing dynamic proof silently passed");
        check(filter.matches(new AttackSourceFilter.Facts(card,"Heatran",false,profile,2,Set.of(SpecialCondition.BURNED))),"Authoritative conjunction rejected");
        check(!filter.matches(new AttackSourceFilter.Facts(card,"Heatran",false,profile,3,Set.of(SpecialCondition.BURNED)))&&!filter.matches(new AttackSourceFilter.Facts(card,"Heatran",false,profile,2,Set.of())),"Dynamic predicates not conjunctive");
        for(String value:List.of("-1","241","x","2.5"))reject(()->AttackSourceFilter.from(Map.of("attacker_energy_max",value)));
        reject(()->AttackSourceFilter.from(Map.of("attacker_status","FROZEN")));
        for(String bad:List.of(GUARD.replace("BENCH","DECK"),GUARD.replace("source_position","unknown_position"),GUARD.replace("attacker_energy_max","attacker_card_max")))reject(()->guard(bad));
        var program=guard(GUARD).effectProgram();reject(()->PassiveModifierProgram.validate(program,PassiveModifierProgram.Source.STADIUM));
        var context=new EffectConditionContext(){public int number(ConditionType t,EffectTarget p,Map<String,String> a){return 0;}public boolean hasStatus(EffectTarget t,String s){return false;}public boolean flag(ConditionType t,EffectTarget p,Map<String,String> a){return false;}public boolean coinResult(String s){return false;}public boolean customCondition(ConditionDefinition c){return false;}};
        check(!PassiveAttackGuard.evaluate(program,context,f->true,t->true).prevent(),"Legacy caller without source position proved BENCH");
        check(PassiveAttackGuard.evaluate(program,context,f->true,t->true,PokemonSelectionScope.Position.BENCH).prevent(),"Explicit source Bench proof rejected");
        reject(()->ProtectionEffect.from(EffectRegistry.standard().parse(JsonParser.parseString(WALL.replace("ATTACK","ABILITY")).getAsJsonObject())));
    }
}
