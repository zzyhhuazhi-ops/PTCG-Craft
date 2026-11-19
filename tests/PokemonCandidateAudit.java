import com.google.gson.*;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.*;
import java.nio.file.*;
import java.util.*;

/** Diagnostic only: parse/preflight acceptance never authorizes an unreviewed card import. */
public final class PokemonCandidateAudit {
    public static void main(String[] args)throws Exception {
        var parser=BundledRules.class.getDeclaredMethod("card",JsonObject.class);parser.setAccessible(true);
        var registry=EffectRegistry.standard();var rows=new ArrayList<Map<String,Object>>();var counts=new TreeMap<String,Integer>();
        for(String line:Files.readAllLines(Path.of(args.length>0?args[0]:"outputs/pokemon-rule-candidates.jsonl"))){
            var source=JsonParser.parseString(line).getAsJsonObject();
            if(!Set.of("AUTO","TEMPLATE").contains(source.get("candidateStatus").getAsString()))continue;
            var row=new LinkedHashMap<String,Object>();String id=source.get("id").getAsString();row.put("id",id);
            var reasons=new ArrayList<String>();
            try {
                var conversion=source.getAsJsonObject("conversion");
                var definition=(CardDefinition)parser.invoke(null,conversion.getAsJsonObject("cardsEntry"));
                var profile=BundledCombatData.pokemon(conversion.getAsJsonObject("combatEntry"),registry);
                profile.attacks().forEach(a->AttackProgramRules.unsupportedReason(a).ifPresent(reason->reasons.add(a.id()+": "+reason)));
                profile.abilities().forEach(a->{if(a.activated())ActivatedAbilityRules.unsupportedReason(a).ifPresent(reason->reasons.add(a.id()+": "+reason));
                    if(a.triggered())TriggeredAbilityRules.unsupportedReason(a).ifPresent(reason->reasons.add(a.id()+": "+reason));});
                var cards=new CardDatabase(List.of(definition));var combat=new CombatCatalog(Map.of(id,profile),Map.of());
                var deck=List.of(new CardInstance(new UUID(0,1),new UUID(0,2),id));
                reasons.addAll(AiSupport.matchProblems(deck,deck,cards,combat));
                row.put("evolutionSource",profile.evolvesFromDefinitionId());
                row.put("attackCount",profile.attacks().size());row.put("abilityCount",profile.abilities().size());
            } catch(Exception error){reasons.add((error.getCause()==null?error:error.getCause()).toString());}
            String status=reasons.isEmpty()?"STATIC_CANDIDATE_ACCEPTED":"CANDIDATE_REJECTED";
            row.put("status",status);row.put("reasons",reasons);rows.add(row);counts.merge(status,1,Integer::sum);
        }
        var result=new LinkedHashMap<String,Object>();result.put("scope","Read-only candidate parse and AI preflight; not source review, runtime import or gameplay proof.");result.put("counts",counts);result.put("cards",rows);
        Files.writeString(Path.of(args.length>1?args[1]:"outputs/pokemon-rule-candidate-validation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
        System.out.println("PokemonCandidateAudit: "+counts);
    }
}
