import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/** A printed end-turn ability completes every choice/reveal before normal turn-end and Checkup. */
public final class StarterDeck100TurnEndTest {
    static final UUID A=StarterDeck100PokemonTest.A,B=StarterDeck100PokemonTest.B;
    static void check(boolean b,String s){StarterDeck100PokemonTest.check(b,s);}
    static void ability(ServerMatch m){StarterDeck100PokemonTest.ability(m,m.activePokemonView(A).pokemonId());}
    public static void main(String[] args){greeningCells();dreamGift();regalStance();revengeBlastPrizes();revengeBlastAgainstPsyray();cardFaceAbilityAndRetreat();azurePulse();derisiveRoasting();leafBlade();System.out.println("StarterDeck100TurnEndTest passed: six printed cards, ability completion before turn end, empty searches, reveal, Checkup, Revenge Blast 0/1/2 prizes, Psyray confusion and card-face actions");}
    static void cardFaceAbilityAndRetreat() {
        for(boolean retreat:List.of(false,true)) {
            var m=StarterDeck100PokemonTest.game(313);StarterDeck100PokemonTest.charge(m,0);
            var old=m.activePokemonView(A);var session=MatchSession.multiplayer(m,A,B);var view=session.view(A,A);
            var options=dev.hua.ptcglive.view.MatchActionMatcher.cardFaceActions(view.options(),"field:"+old.pokemonId(),true);
            check(options.stream().anyMatch(o->o.labelZh().startsWith("攻击：")),"Card-face attack missing");
            check(options.stream().anyMatch(o->o.labelZh().startsWith("使用特性：")),"Card-face Ability missing");
            check(options.stream().anyMatch(o->o.labelZh().startsWith("撤退 → ")),"Card-face retreat missing");
            var action=options.stream().filter(o->o.labelZh().startsWith(retreat?"撤退 → ":"使用特性：")).findFirst().orElseThrow();
            session.submit(A,A,view.revision(),action.index());
            TopDeckLookTest.rejects(()->session.submit(A,A,view.revision(),action.index()));
            if(retreat) {
                RetreatEnergyChoiceTest.confirmSuggested(session,A);
                check(!m.activePokemonView(A).pokemonId().equals(old.pokemonId()),"Retreat button must switch Active");
                var former=m.benchPokemonView(A).stream().filter(p->p.pokemonId().equals(old.pokemonId())).findFirst().orElseThrow();
                check(former.attachedEnergyIds().size()==old.attachedEnergyIds().size()-2,"Retreat button must pay real cost");
                check(session.view(A,A).options().stream().noneMatch(o->o.labelZh().startsWith("撤退 → ")),"Second retreat in same turn must be unavailable");
            } else check(m.privateView(A).hand().size()==5&&m.currentPlayer().equals(B),"Ability button must retain printed discard/draw/end-turn behavior");
        }
    }
    static void revengeBlastPrizes() {
        for(int number:List.of(313,417)) for(int prizes:List.of(0,1,2)) {
            var m=StarterDeck100PokemonTest.game(number,420,StarterDeck100PokemonTest.id(2));
            if(prizes>0) {
                UUID victim;
                if(prizes==1) {
                    var basic=StarterDeck100PokemonTest.hand(m,StarterDeck100PokemonTest.id(2));
                    m.playBasicPokemonToBench(A,basic.instanceId());
                    victim=m.benchPokemonView(A).stream().filter(p->p.topCardId().equals(basic.definitionId())).findFirst().orElseThrow().pokemonId();
                } else victim=m.benchPokemonView(A).stream().filter(p->p.topCardId().equals(StarterDeck100PokemonTest.id(number))).findFirst().orElseThrow().pokemonId();
                m.endTurn(A);m.knockOutPokemonByEffect(B,A,victim);
                StarterDeck100CombatTest.settleKnockouts(m);m.endTurn(B);
            }
            check(m.privateView(B).prizes().size()==6-prizes,"Prize fixture must match actual cards taken: card="+number+", expected="+(6-prizes)+", actual="+m.privateView(B).prizes().size());
            StarterDeck100PokemonTest.attack(m,0);
            check(m.lastAttack().finalDamage()==120+30*prizes,"Revenge Blast must ADD prize bonus to 120");
            check(m.activePokemonView(B).damage()==120+30*prizes,"Articuno HP must reflect actual Revenge Blast damage");
        }
    }
    static void revengeBlastAgainstPsyray() {
        var outcomes=new HashSet<Integer>();
        for(int seed=0;seed<80&&outcomes.size()<2;seed++) {
            var m=StarterDeck100PokemonTest.gameSeed(417,420,seed);
            StarterDeck100PokemonTest.charge(m,0);m.endTurn(A);
            for(String energy:List.of("psychic","grass","grass"))
                StarterDeck100EnergyTest.attach(m,B,B,"sd100_cn_"+energy+"_energy",m.activePokemonView(B).pokemonId());
            m.attack(B,"psyray");
            check(m.activePokemonView(A).conditions().contains(SpecialCondition.CONFUSED)&&m.activePokemonView(A).damage()==110,"Psyray must deal 110 and confuse Zamazenta");
            var session=MatchSession.multiplayer(m,A,B);
            var before=session.view(A,A);
            var attack=before.options().stream().filter(o->o.labelZh().contains("复仇爆破")).findFirst().orElseThrow();
            session.submit(A,A,before.revision(),attack.index());
            int damage=m.lastAttack().finalDamage();outcomes.add(damage);
            check(damage==0||damage==120,"Confusion may stop attack, but cannot replace its base damage");
            check(m.activePokemonView(B).damage()==damage,"Defender HP differs from server attack result");
            check(m.activePokemonView(A).damage()==(damage==0?140:110),"Only failed confusion adds three self damage counters");
            check(m.privateView(B).prizes().size()==6,"Opponent has taken zero prizes in both coin outcomes");
            var cues=dev.hua.ptcglive.view.MatchPresentation.changes(before,session.view(A,A));
            if(damage==0) check(cues.stream().anyMatch(c->c.text().contains("混乱")&&c.text().contains("攻击失败")),"Confusion failure must be explained, not reported as a completed attack");
            else check(cues.stream().anyMatch(c->c.kind()==dev.hua.ptcglive.view.MatchPresentation.Kind.DAMAGE&&c.amount()==120)
                &&cues.stream().noneMatch(c->c.text().contains("攻击失败")),"Successful confusion must not display attack failure");
            check(cues.stream().anyMatch(c->c.kind()==dev.hua.ptcglive.view.MatchPresentation.Kind.COIN&&c.text().equals("硬币·混乱："+(damage==0?"反面":"正面"))),"Show the actual server confusion coin, including heads");
        }
        check(outcomes.equals(Set.of(0,120)),"Both confusion coin outcomes must be covered");
    }
    static void greeningCells(){for(int selected:List.of(0,1)){var m=StarterDeck100PokemonTest.game(17);m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);ability(m);check(m.currentPlayer().equals(A)&&m.activePokemonView(A).damage()==0,"Greening Cells ended before Energy choice");var p=m.effectCardPrompt(A);check(p.candidates().stream().allMatch(c->c.definitionId().equals("sd100_cn_grass_energy")),"Greening Cells search not Basic Grass");StarterDeck100PokemonTest.select(m,selected);if(selected==1){check(m.currentPlayer().equals(A),"Greening Cells ended before target choice");var dest=m.effectAttachmentPrompt(A);m.chooseEffectAttachmentTarget(A,dest.resolutionId(),dest.step(),m.activePokemonView(A).pokemonId());}check(m.currentPlayer().equals(B)&&m.activePokemonView(A).damage()==10&&m.activePokemonView(A).attachedEnergyIds().size()==selected,"Greening Cells must end and Checkup even when search fails");}}
    static void dreamGift(){var m=StarterDeck100PokemonTest.game(191,StarterDeck100PokemonTest.id(373));ability(m);var p=m.effectCardPrompt(A);var found=p.candidates().getFirst();m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(found.instanceId()));check(m.currentPlayer().equals(A)&&m.effectCardReveal()!=null,"Dream Gift ended before reveal acknowledgment");TopDeckLookTest.rejects(()->m.endTurn(A));m.acknowledgeEffectReveal(B,m.effectCardReveal().id());check(m.currentPlayer().equals(B)&&m.privateView(A).hand().contains(found),"Dream Gift failed revealed recovery / end turn");}
    static void regalStance(){for(int n:List.of(313,417)){var zero=StarterDeck100PokemonTest.game(n);StarterDeck100PokemonTest.attack(zero,0);check(zero.lastAttack().finalDamage()==120,"Revenge Blast must preserve 120 base damage when opponent has taken zero prizes: "+zero.lastAttack().finalDamage());var m=StarterDeck100PokemonTest.game(n);var before=m.privateView(A).hand();ability(m);check(m.privateView(A).discard().containsAll(before)&&m.privateView(A).hand().size()==5&&m.currentPlayer().equals(B)&&m.lastAttack()==null,"Regal Stance failed hand replacement or incorrectly recorded an attack");
        m=StarterDeck100PokemonTest.game(n);m.endTurn(A);m.knockOutPokemonByEffect(B,A,m.benchPokemonView(A).getFirst().pokemonId());StarterDeck100CombatTest.settleKnockouts(m);m.endTurn(B);StarterDeck100PokemonTest.attack(m,0);check(m.lastAttack().finalDamage()==180,"Revenge Blast must read prizes actually taken by opponent");}}
    static void azurePulse(){var m=StarterDeck100PokemonTest.game(316);var before=m.privateView(A).hand();ability(m);check(m.currentPlayer().equals(A)&&m.privateView(A).hand().size()==3&&m.privateView(A).discard().containsAll(before),"Azure Pulse wrongly ends turn or failed discard");var tested=m;TopDeckLookTest.rejects(()->ability(tested));
        for(int branch:List.of(0,1)){m=StarterDeck100PokemonTest.game(316);StarterDeck100PokemonTest.charge(m,0);String energy=branch==0?"sd100_cn_fire_energy":"sd100_cn_lightning_energy";for(int i=0;i<2;i++)StarterDeck100EnergyTest.attach(m,A,A,energy,m.activePokemonView(A).pokemonId());m.attack(A,"max-burst");var choice=m.effectChoicePrompt(A);m.chooseEffectOption(A,choice.resolutionId(),choice.step(),branch);check(m.effectCardPrompt(A).candidates().size()==3&&m.effectCardPrompt(A).candidates().stream().allMatch(c->c.definitionId().equals(energy)),"Max Burst mixed Energy types or inherited V two-card cap");StarterDeck100PokemonTest.select(m,3);check(m.lastAttack().finalDamage()==260&&m.activePokemonView(A).attachedEnergyIds().size()==1,"Max Burst did not use actual three-card discard");}}
    static void derisiveRoasting(){for(int statuses:List.of(0,1,3)){var m=StarterDeck100PokemonTest.game(69);if(statuses>0)m.applySpecialConditionByEffect(A,B,SpecialCondition.POISONED);if(statuses==3){m.applySpecialConditionByEffect(A,B,SpecialCondition.BURNED);m.applySpecialConditionByEffect(A,B,SpecialCondition.CONFUSED);}StarterDeck100PokemonTest.attack(m,1);check(m.lastAttack().finalDamage()==180*statuses,"Derisive Roasting counted base damage or missed statuses");}}
    static void leafBlade(){var seen=new HashSet<Integer>();for(int seed=0;seed<80&&seen.size()<2;seed++){var m=StarterDeck100PokemonTest.gameSeed(17,1,seed);StarterDeck100PokemonTest.attack(m,0);int damage=m.lastAttack().finalDamage();check(damage==90||damage==150,"Leaf Blade coin damage");seen.add(damage);}check(seen.size()==2,"Leaf Blade coin coverage");}
}
