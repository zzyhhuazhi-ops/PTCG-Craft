import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual printed cards: preflight must admit their server-owned choice sequence. */
public final class ModernOperationEntryRepairTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        for(String id:List.of("sv2_en_084","sv4-5_en_147")){
            var m=AiOperationFixtures.generic(id,"balloon-therapy",ActionType.ABILITY,5);var actor=m.currentPlayer();
            int before=(int)m.privateView(actor).hand().stream().filter(c->c.definitionId().equals("sv2_en_193")).count();
            check(before>0&&m.canUseAbility(actor,m.activePokemonView(actor).pokemonId(),"balloon-therapy"),"Therapeutic Energy did not enable Balloon Therapy: "+id);
            UUID host=m.activePokemonView(actor).pokemonId();
            var energy=GeneratedRuleBatch28Test.hand(m,actor,"sv2_en_193");
            m.useAbility(actor,host,"balloon-therapy");
            if(m.hasPendingPokemonSelection()){var p=m.effectPokemonPrompt(actor);m.chooseEffectPokemon(actor,p.resolutionId(),p.step(),host);}
            var selection=m.effectCardPrompt(actor);
            check(selection.candidates().stream().allMatch(c->c.definitionId().equals("sv2_en_193")),"Balloon Therapy offered a different printed card");
            m.submitEffectCards(actor,selection.resolutionId(),selection.step(),List.of(energy.instanceId()));
            if(m.hasPendingAttachmentTarget()){var p=m.effectAttachmentPrompt(actor);m.chooseEffectAttachmentTarget(actor,p.resolutionId(),p.step(),host);}
            AiCardOperationTest.settle(AiOperationFixtures.session(m),AiDifficulty.NORMAL);
            check(m.privateView(actor).hand().stream().filter(c->c.definitionId().equals("sv2_en_193")).count()==before-1,"Balloon Therapy did not attach its actual Energy");
            check(m.activePokemonView(actor).attachedEnergyIds().contains(energy.instanceId()),"Balloon Therapy did not retain the selected physical Energy");
            check(!m.energyAttached(actor),"Ability consumed manual attachment quota");
            for(var level:AiDifficulty.values())balloonAi(id,level);
        }
        for(boolean second:List.of(false,true))for(boolean swap:List.of(false,true))prize(second,swap,false);
        prize(false,false,true);
        System.out.println("ModernOperationEntryRepairTest passed: both Balloon Therapy printings, actual Energy identity/manual quota and all four AI tiers attaching when needed; Bother-Bot both owners, accept/decline, empty hand, server RNG, public face-up prize, authority and stale-command rejection");
    }
    static void balloonAi(String id,AiDifficulty level){
        var c=new GeneratedRuleBatch28Test.Config();
        c.ownAbility=AiOperationFixtures.DATA.pokemonProfiles().get(id).abilities().stream().filter(a->a.id().equals("balloon-therapy")).findFirst().orElseThrow();
        // A free attack has no energy need: declining optional attachment there is valid resource conservation.
        c.attack=new AttackDefinition("needs-energy","Needs Energy",Map.of(EnergyType.COLORLESS,1),10,EnergyType.COLORLESS,EffectSupport.SUPPORTED,null);
        var m=AiOperationFixtures.resourceBoard(GeneratedRuleBatch28Test.fixture(c),c,List.of(Map.of("definition_ids","sv2_en_193")),List.of());
        UUID actor=m.currentPlayer(),host=m.activePokemonView(actor).pokemonId();
        var candidates=m.privateView(actor).hand().stream().filter(x->x.definitionId().equals("sv2_en_193")).map(CardInstance::instanceId).toList();
        check(!candidates.isEmpty(),"AI fixture lacks Therapeutic Energy");
        m.useAbility(actor,host,"balloon-therapy");AiCardOperationTest.settle(AiOperationFixtures.session(m),level);
        var mons=new ArrayList<>(m.benchPokemonView(actor));mons.add(m.activePokemonView(actor));
        check(mons.stream().flatMap(x->x.attachedEnergyIds().stream()).filter(candidates::contains).count()==1,"AI failed to attach needed Therapeutic Energy: "+id+" "+level);
        check(!m.energyAttached(actor),"AI ability attachment consumed manual quota");
    }
    static void prize(boolean second,boolean swap,boolean emptyHand){
        var c=new GeneratedRuleBatch28Test.Config();c.trainer=AiOperationFixtures.DATA.trainerProfiles().get("sv10_en_172");
        var m=GeneratedRuleBatch28Test.fixture(c);if(second)m.endTurn(m.currentPlayer());UUID actor=m.currentPlayer();
        UUID opponent=m.clientView(null).players().stream().map(MatchClientView.Player::id).filter(p->!p.equals(actor)).findFirst().orElseThrow();
        if(emptyHand)m.discardHandCardsByEffect(actor,opponent,m.privateView(opponent).hand().stream().map(CardInstance::instanceId).toList());
        var oldHand=List.copyOf(m.privateView(opponent).hand());var oldPrizes=List.copyOf(PairedTrainerPlayTest.zone(m,opponent,"prizes"));var rng=m.randomTranscript();
        m.playTrainer(actor,GeneratedRuleBatch28Test.hand(m,actor,"trainer").instanceId(),null);
        check(m.randomTranscript().equals(rng),"Declaration consumed random selection before prize was chosen");
        var prompt=m.prizeRandomSwapPrompt(actor);check(prompt.faceDownSlots().size()==6&&!prompt.decisionReady(),"Private prize selection missing");
        BeforeDamageAttackTest.reject(()->m.prizeRandomSwapPrompt(opponent));
        int slot=prompt.faceDownSlots().getFirst();m.choosePrizeRandomSwapSlot(actor,prompt.resolutionId(),prompt.step(),slot);
        var reveal=m.clientView(actor).effectReveal();check(reveal!=null&&reveal.equals(m.clientView(opponent).effectReveal()),"Chosen prize/random hand card must be publicly revealed");
        check(m.randomTranscript().size()==rng.size()+(emptyHand?0:1),"Random selection must use exactly one server draw for a nonempty hand");
        m.acknowledgeEffectReveal(reveal.confirmingPlayer(),reveal.id());
        if(!emptyHand){var decision=m.prizeRandomSwapPrompt(actor);check(decision.decisionReady(),"Swap decision missing after reveal");m.decidePrizeRandomHandSwap(actor,decision.resolutionId(),decision.step(),swap);BeforeDamageAttackTest.reject(()->m.decidePrizeRandomHandSwap(actor,decision.resolutionId(),decision.step(),swap));}
        check(!m.hasPendingEffectChoice()&&m.phase()==MatchPhase.TURN,"Prize swap failed to return control");
        var prizes=PairedTrainerPlayTest.zone(m,opponent,"prizes");var face=m.clientView(actor).players().stream().filter(p->p.id().equals(opponent)).findFirst().orElseThrow().faceUpPrizes();
        check(face.size()==1&&face.getFirst().slot()==slot&&face.getFirst().cardId().equals(prizes.get(slot).definitionId()),"Prize must remain face up after resolution");
        check(prizes.size()==oldPrizes.size()&&m.privateView(opponent).hand().size()==oldHand.size(),"Swap changed zone sizes");
        if(swap){check(oldHand.contains(prizes.get(slot))&&m.privateView(opponent).hand().contains(oldPrizes.get(slot)),"Swap did not move physical cards between original zones");}
        else check(prizes.equals(oldPrizes)&&m.privateView(opponent).hand().equals(oldHand),"Decline/empty hand changed cards");
    }
}
