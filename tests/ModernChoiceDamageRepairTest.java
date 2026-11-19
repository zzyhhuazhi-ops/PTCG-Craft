import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernChoiceDamageRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){ModernRuleRepair2Test.check(ok,why);}
    static AttackDefinition attack(String id,int slot){var a=DATA.pokemon(id).orElseThrow().attacks().get(slot);AttackProgramRules.validate(a);return a;}
    public static void main(String[] args){crobat();heatmor();camerupt();tinkatink();malamar();System.out.println("ModernChoiceDamageRepairTest passed: trainer lock/expiry, three-coin tails costs, both discarded Energy before damage, private top-card take/discard/draw and opponent-first read-only inspection");}
    static void tinkatink(){
        var a=attack("sv4_en_082",0);
        for(int size:List.of(0,1,2,8))for(boolean take:List.of(false,true)){
            var m=ModernFieldRuleRepairTest.game(a,List.of(),EnergyType.PSYCHIC);ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"energy",1);
            m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-size);var before=m.privateView(A);var rng=m.randomTranscript();m.attack(A,a.id());
            if(size>0){var q=m.effectCardPrompt(A);check(q.minimum()==0&&q.maximum()==1&&q.candidates().equals(List.of(before.deck().getLast()))&&m.clientView(B).effectSelection()==null,"Tinkatink exposed top or allowed wrong selection");
                CopyAttackTest.reject(()->m.submitEffectCards(B,q.resolutionId(),q.step(),List.of()));m.submitEffectCards(A,q.resolutionId(),q.step(),take?List.of(before.deck().getLast().instanceId()):List.of());CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of()));
                if(take)check(m.privateView(A).hand().contains(before.deck().getLast()),"Tinkatink did not take seen card");
                else {check(m.privateView(A).discard().contains(before.deck().getLast()),"Tinkatink did not discard seen card");if(size>1)check(m.privateView(A).hand().contains(before.deck().get(size-2)),"Tinkatink did not draw next card");}}
            check(m.privateView(A).deck().size()==Math.max(0,size-(take?1:2))&&m.privateView(A).hand().size()==before.hand().size()+((take?size>0:size>1)?1:0)&&m.privateView(A).discard().size()==before.discard().size()+(!take&&size>0?1:0)&&rng.equals(m.randomTranscript())&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Tinkatink short deck/choice duplicated, shuffled or stalled");
        }
    }
    static void malamar(){
        var ability=DATA.pokemon("sv3_en_138").orElseThrow().abilities().getFirst();ActivatedAbilityRules.validate(ability);
        for(int own:List.of(0,1,5))for(int opposing:List.of(0,1,5)){
            var m=ModernFieldRuleRepairTest.game(GeneratedRuleBatch8Test.hit(10,false),List.of(ability),EnergyType.DARKNESS);
            m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-own);m.drawCardsByEffect(A,B,m.privateView(B).deck().size()-opposing);
            var beforeA=m.privateView(A);var beforeB=m.privateView(B);var rng=m.randomTranscript();UUID source=m.activePokemonView(A).pokemonId();
            if(opposing==0){check(!m.canUseAbility(A,source,ability.id()),"Malamar should not inspect own top when opponent deck empty");CopyAttackTest.reject(()->m.useAbility(A,source,ability.id()));continue;}
            m.useAbility(A,source,ability.id());
            for(UUID owner:own==0?List.of(B):List.of(B,A)){var top=(owner.equals(A)?beforeA:beforeB).deck().getLast();var q=m.effectCardPrompt(A);
                check(q.minimum()==1&&q.maximum()==1&&q.candidates().equals(List.of(top))&&m.clientView(B).effectSelection()==null&&m.effectCardReveal()==null,"Malamar order, private scope or exact count wrong");
                CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of()));CopyAttackTest.reject(()->m.submitEffectCards(B,q.resolutionId(),q.step(),List.of(top.instanceId())));
                m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(top.instanceId()));CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(top.instanceId())));}
            check(m.privateView(A).deck().equals(beforeA.deck())&&m.privateView(B).deck().equals(beforeB.deck())&&m.privateView(A).hand().equals(beforeA.hand())&&m.privateView(B).hand().equals(beforeB.hand())&&m.privateView(A).discard().equals(beforeA.discard())&&m.privateView(B).discard().equals(beforeB.discard())&&rng.equals(m.randomTranscript())&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A)&&!m.canUseAbility(A,source,ability.id()),"Malamar moved cards, shuffled, stalled or reused ability");
        }
    }
    static void crobat(){
        var a=attack("sv4_en_112",0);
        for(int choice=0;choice<2;choice++)for(String category:List.of("ITEM","SUPPORTER"))for(boolean switched:List.of(false,true)){
            var trainer=BundledCombatData.trainer(com.google.gson.JsonParser.parseString("{\"trainerCategory\":\""+category+"\",\"effect\":{\"opcode\":\"DRAW\",\"amount\":1}}").getAsJsonObject(),EffectRegistry.standard());
            var m=ModernEnergyRepairTest.game(trainer,List.of(),a,EnergyType.DARKNESS,0);m.drawCardsByEffect(A,B,24);ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"energy",2);
            m.attack(A,a.id());var q=m.effectChoicePrompt(A);check(q.optionsZh().size()==2&&m.activePokemonView(B).damage()==50,"Crobat must choose exactly one lock after damage");
            CopyAttackTest.reject(()->m.chooseEffectOption(B,q.resolutionId(),q.step(),0));CopyAttackTest.reject(()->m.chooseEffectOption(A,q.resolutionId(),q.step(),2));m.chooseEffectOption(A,q.resolutionId(),q.step(),choice);
            if(switched)m.switchActiveByEffect(B,B,m.benchPokemonView(B).getFirst().pokemonId());var card=ModernRuleRepair2Test.hand(m,B,"trainer");boolean blocked=category.equals(choice==0?"ITEM":"SUPPORTER");
            check(m.canPlayTrainer(B,card.instanceId(),null)!=blocked,"Crobat lock affects wrong category or vanished with defender switch");
            if(blocked)CopyAttackTest.reject(()->m.playTrainer(B,card.instanceId(),null));
            m.endTurn(B);m.endTurn(A);check(m.canPlayTrainer(B,card.instanceId(),null)&&!m.hasPendingEffectChoice(),"Crobat lock did not expire");
            CopyAttackTest.reject(()->m.chooseEffectOption(A,q.resolutionId(),q.step(),0));
        }
    }
    static void heatmor(){
        var a=attack("sv5_en_031",1);Set<Integer> seen=new HashSet<>();
        for(int seed=0;seed<48;seed++)for(boolean special:List.of(false,true)){
            var m=ModernEnergyRepairTest.game(DATA.trainer("swsh5_en_124").orElseThrow(),List.of(),a,EnergyType.FIRE,seed);UUID source=m.activePokemonView(A).pokemonId();
            ModernRuleRepair2Test.pay(m,A,source,"energy",special?1:3);if(special)ModernRuleRepair2Test.pay(m,A,source,"special",1);ModernRuleRepair2Test.pay(m,A,m.benchPokemonView(A).getFirst().pokemonId(),"wrong",1);
            var before=m.activePokemonView(A).attachedEnergyIds();int start=m.randomTranscript().size();m.attack(A,a.id());var coins=m.randomTranscript().subList(start,m.randomTranscript().size());int tails=(int)coins.stream().filter(c->c.result()==1).count();seen.add(tails);
            check(coins.size()==3&&m.activePokemonView(B).damage()==130,"Heatmor rolled wrong count or damaged after payment");int count=tails==0?0:special?(tails==3?2:1):tails;
            if(count>0){var q=m.effectCardPrompt(A);check(q.minimum()==1&&q.maximum()>=count&&q.maximum()<=before.size()&&q.candidates().size()==before.size()&&q.candidates().stream().allMatch(c->before.contains(c.instanceId())),"Heatmor Energy-unit selection includes the wrong source or bounds");
                CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of()));
                if(tails>1){var basic=q.candidates().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(basic.instanceId())));}
                var chosen=special&&tails==2?q.candidates().stream().filter(c->c.definitionId().equals("special")).map(CardInstance::instanceId).toList():q.candidates().stream().limit(count).map(CardInstance::instanceId).toList();
                m.submitEffectCards(A,q.resolutionId(),q.step(),chosen);CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),chosen));}
            check(m.activePokemonView(A).attachedEnergyIds().size()==before.size()-count&&m.benchPokemonView(A).getFirst().attachedEnergyIds().size()==1&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Heatmor lost wrong Energy, duplicated payment, or stalled");
        }check(seen.equals(Set.of(0,1,2,3)),"Heatmor did not exercise all tails counts");
    }
    static void camerupt(){
        var a=attack("sv3_en_032",0);
        for(String own:List.of("basic","energy","special","EMPTY"))for(String opposing:List.of("basic","energy","special","EMPTY")){
            ServerMatch found=null;
            for(int seed=0;seed<100;seed++){var m=ModernEnergyRepairTest.game(DATA.trainer("swsh5_en_124").orElseThrow(),List.of(),a,EnergyType.FIRE,seed);
                if(!own.equals("EMPTY")&&m.privateView(A).deck().stream().noneMatch(c->c.definitionId().equals(own))||!opposing.equals("EMPTY")&&m.privateView(B).deck().stream().noneMatch(c->c.definitionId().equals(opposing)))continue;found=m;break;}
            check(found!=null,"Camerupt fixture");var m=found;ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"energy",2);
            for(UUID owner:List.of(A,B)){var deck=m.privateView(owner).deck();String wanted=owner.equals(A)?own:opposing;int keep=0;for(int i=0;i<deck.size();i++)if(deck.get(i).definitionId().equals(wanted))keep=i+1;
                if(deck.size()>keep)m.drawCardsByEffect(A,owner,deck.size()-keep);}
            var beforeA=m.privateView(A);var beforeB=m.privateView(B);int rng=m.randomTranscript().size();m.attack(A,a.id());int expected=50+(Set.of("energy","special").contains(own)?100:0)+(Set.of("energy","special").contains(opposing)?100:0);
            check(m.lastAttack().finalDamage()==expected&&m.activePokemonView(B).damage()==expected,"Camerupt failed both independent actual Energy counts before damage");
            for(UUID owner:List.of(A,B)){var before=owner.equals(A)?beforeA:beforeB;check(m.privateView(owner).discard().size()==before.discard().size()+(before.deck().isEmpty()?0:1),"Camerupt discard count");if(!before.deck().isEmpty())check(m.privateView(owner).discard().contains(before.deck().getLast()),"Camerupt discarded wrong physical card");}
            check(m.randomTranscript().size()==rng&&!m.hasPendingEffectChoice(),"Camerupt shuffled or stalled");
        }
    }
}
