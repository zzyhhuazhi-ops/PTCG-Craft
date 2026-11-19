package dev.hua.ptcglive.ai;

import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Preflight choice capability, separate from DeckValidator's engine/card legality. */
public final class AiSupport {
    private AiSupport(){}
    public static List<String> problems(Collection<CardInstance> deck,CardDatabase cards,CombatCatalog combat) {
        return problems(deck,cards,combat,true,false);
    }
    /** Human-owned private costs remain human decisions. Only their opponent-response paths constrain the AI. */
    public static List<String> opponentProblems(Collection<CardInstance> deck,CardDatabase cards,CombatCatalog combat) {
        return problems(deck,cards,combat,false,false);
    }
    /** Both private lists are used only by preflight, never supplied to the decision view. */
    public static List<String> matchProblems(Collection<CardInstance> computer,Collection<CardInstance> opponent,CardDatabase cards,CombatCatalog combat) {
        var issues=new LinkedHashSet<>(problems(computer,cards,combat,true,true));
        issues.addAll(problems(opponent,cards,combat,false,true));
        boolean copiesOpponent=computer.stream().map(CardInstance::definitionId).distinct()
            .map(combat::fieldPokemon).flatMap(Optional::stream).flatMap(p->p.attacks().stream())
            .flatMap(a->a.effectProgram().triggers().stream()).flatMap(b->b.effects().stream())
            .anyMatch(AiSupport::containsOpponentCopy);
        if(copiesOpponent)issues.addAll(problems(opponent,cards,combat,true,true));
        return List.copyOf(issues);
    }
    private static boolean opponentActiveCopy(EffectDefinition e) {
        return e.type().contains("copy_attack")&&Set.of(EffectTarget.OPPONENT_ACTIVE,EffectTarget.ALL_OPPONENT_POKEMON).contains(e.target())
            ||e.type().equals("use_opponent_hand_supporter");
    }
    private static boolean containsOpponentCopy(EffectDefinition e) {
        return opponentActiveCopy(e)||e.effects().stream().anyMatch(AiSupport::containsOpponentCopy);
    }
    private static List<String> problems(Collection<CardInstance> deck,CardDatabase cards,CombatCatalog combat,boolean computerDeck,boolean opponentChecked) {
        var issues=new LinkedHashSet<String>();
        for(String id:deck.stream().map(CardInstance::definitionId).distinct().toList()) {
            String name=cards.find(id).map(CardDefinition::nameZh).orElse(id)+" ["+id+"]";var programs=new ArrayList<EffectProgram>();
            combat.fieldPokemon(id).ifPresent(p->{p.attacks().forEach(a->programs.add(a.effectProgram()));p.abilities().forEach(a->programs.add(a.effectProgram()));});
            combat.trainer(id).ifPresent(p->{if(p.pairedPlay()!=null)programs.add(p.pairedPlay().program());programs.add(p.effectProgram());programs.add(p.ongoingProgram());p.automaticEffects().forEach(a->programs.add(a.effectProgram()));
                if(p.activation()!=null)programs.add(p.activation().program());if(p.toolActivation()!=null)programs.add(p.toolActivation().program());
                if(p.inPlayPokemon()!=null){p.inPlayPokemon().attacks().forEach(a->programs.add(a.effectProgram()));p.inPlayPokemon().abilities().forEach(a->programs.add(a.effectProgram()));}});
            combat.energyProfile(id).ifPresent(p->{programs.add(p.passiveProgram());p.automaticEffects().forEach(a->programs.add(a.effectProgram()));});
            for(var p:programs)for(var b:p.triggers())for(var e:b.effects())inspect(name,e,issues,computerDeck,opponentChecked);
        }
        return List.copyOf(issues);
    }
    // Own-deck-top copies can only use a Pokemon from this checked deck. Every candidate's
    // attack program is inspected in the same pass, including own Bench grants.
    private static void inspect(String name,EffectDefinition e,Set<String> issues,boolean chooses,boolean opponentChecked) {
        boolean aiChooses=chooses||"OPPONENT".equals(e.arguments().get("chooser"));
        if(aiChooses){
            if(opponentActiveCopy(e)&&!opponentChecked)issues.add(name+"：借用对手效果需要同时检查对方卡组");
            // Choice constraints are enforced by the same server-issued action list used by humans.
            // Retain schema validation: accepting a selector does not invent an unimplemented effect.
            try{EffectSchemaValidator.validate(e);}catch(IllegalArgumentException invalid){issues.add(name+"：效果组合未支持："+invalid.getMessage());}
        }
        e.effects().forEach(child->inspect(name,child,issues,aiChooses,opponentChecked));
    }
}
