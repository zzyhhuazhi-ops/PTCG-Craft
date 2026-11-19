import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch51Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("sm12_en_194","swsh4_en_152","swsh7_en_155");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static GeneratedRuleBatch28Test.Config config(String id,PokemonFamily family,String name){
        var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();
        c.ownFamilies=family==PokemonFamily.VMAX?Set.of(PokemonFamily.V,PokemonFamily.VMAX):Set.of(family);c.defenderFamilies=c.ownFamilies;c.ownName=name;c.names=Map.of("defender",name);
        c.ownPrizes=family==PokemonFamily.VMAX?3:2;c.defenderPrizes=c.ownPrizes;return c;
    }
    static ServerMatch aiGame(String id){return GeneratedRuleBatch28Test.fixture(config(id,id.equals(IDS.get(0))?PokemonFamily.GX:id.equals(IDS.get(1))?PokemonFamily.VMAX:PokemonFamily.V,"Sylveon VMAX"));}
    static CardInstance card(ServerMatch m,UUID owner){return NonPokemonBatch37Test.card(m,owner,"trainer");}
    static void damage(ServerMatch m,UUID actor,UUID owner,UUID host,int counters){while(counters>0){int n=Math.min(60,counters);m.placeDamageCountersByEffect(actor,owner,host,n);counters-=n;}}
    static UUID host(ServerMatch m,UUID owner,boolean bench){return bench?m.benchPokemonView(owner).getFirst().pokemonId():m.activePokemonView(owner).pokemonId();}
    static int hp(ServerMatch m,UUID owner,boolean bench){return bench?m.benchPokemonView(owner).getFirst().hitPoints():m.activePokemonView(owner).hitPoints();}
    static int prizes(ServerMatch m,UUID owner){return m.privateView(owner).prizes().size();}
    public static void main(String[] args){families();causes();medalRestriction();immediateKo();stack();System.out.println("NonPokemonBatch51Test passed: exact GX/legacy-EX/VMAX/Sylveon-V filters, HP restoration, both owners and Bench attack KOs, counters/checkup/self-damage/effect exclusions, hand-only Medal restriction including disabled Tools, effect attachment exception, immediate HP KO and stacked reductions");}
    static void families(){for(String id:IDS)for(PokemonFamily family:List.of(PokemonFamily.GX,PokemonFamily.EX_LEGACY,PokemonFamily.EX,PokemonFamily.V,PokemonFamily.VMAX))for(boolean named:List.of(false,true)){
        var c=config(id,family,named?"Sylveon VMAX":"Other VMAX");var m=GeneratedRuleBatch28Test.fixture(c);UUID target=host(m,A,false);var source=card(m,A);m.playTrainer(A,source.instanceId(),target);
        boolean reduces=id.equals(IDS.get(0))?(family==PokemonFamily.GX||family==PokemonFamily.EX_LEGACY):id.equals(IDS.get(1))&&family==PokemonFamily.VMAX;
        check(hp(m,A,false)==(reduces?900:1000),"Wrong family HP modifier");m.discardToolByEffect(A,A,target,source.instanceId());check(hp(m,A,false)==1000,"HP loss persisted after Tool departure");
        var n=GeneratedRuleBatch28Test.fixture(c);target=host(n,A,false);n.playTrainer(A,card(n,A).instanceId(),target);damage(n,A,A,target,(hp(n,A,false)-100)/10);n.endTurn(A);int before=prizes(n,B);n.attack(B,"hit");NonPokemonBatch2Test.settle(n);
        boolean matches=id.equals(IDS.get(2))?named&&(family==PokemonFamily.V||family==PokemonFamily.VMAX):reduces;
        check(before-prizes(n,B)==c.ownPrizes-(matches?1:0),"Wrong family/name Prize reduction: "+id+family+named);
    }}
    static void causes(){for(String id:IDS)for(UUID owner:List.of(A,B))for(String cause:List.of("attack","bench","counters","poison","effect","self","disabled")){
        var family=id.equals(IDS.get(0))?PokemonFamily.GX:id.equals(IDS.get(1))?PokemonFamily.VMAX:PokemonFamily.V;var c=config(id,family,"Sylveon VMAX");boolean bench=cause.equals("bench");
        if(cause.equals("counters"))c.attack=DeckDiscardTriggeredAbilityTest.attack(0,"{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":10}");
        if(cause.equals("self"))c.attack=DeckDiscardTriggeredAbilityTest.attack(0,"{\"type\":\"self_damage\",\"target\":\"SELF\",\"amount\":100}");
        if(bench)c.attack=GeneratedRuleBatch8Test.hit(100,true);c.defenderAttack=c.attack;
        var m=GeneratedRuleBatch28Test.fixture(c);if(owner.equals(B))m.endTurn(A);UUID target=host(m,owner,bench),other=owner.equals(A)?B:A;var source=card(m,owner);m.playTrainer(owner,source.instanceId(),target);
        if(cause.equals("disabled")){m.disableToolEffectsByEffect(owner,owner,target);check(hp(m,owner,bench)==1000,"Disabled Tool still lowered HP");}
        int remaining=cause.equals("poison")?10:100;damage(m,owner,owner,target,(hp(m,owner,bench)-remaining)/10);int before=prizes(m,other);
        if(cause.equals("effect"))m.knockOutPokemonByEffect(owner,owner,target);
        else if(cause.equals("poison")){m.applySpecialConditionByEffect(owner,owner,SpecialCondition.POISONED);m.endTurn(owner);}
        else if(cause.equals("self"))m.attack(owner,"hit");
        else{m.endTurn(owner);m.attack(other,"hit");}
        NonPokemonBatch2Test.settle(m);check(before-prizes(m,other)==c.ownPrizes-(Set.of("attack","bench").contains(cause)?1:0),"Prize reduction applied to wrong KO cause: "+id+owner+cause);
    }}
    static void medalRestriction(){for(boolean disabled:List.of(false,true))for(boolean vmax:List.of(false,true))for(boolean effect:List.of(false,true))for(int remaining:List.of(90,100,110)){
        var c=config(IDS.get(1),vmax?PokemonFamily.VMAX:PokemonFamily.V,"Sylveon VMAX");var m=GeneratedRuleBatch28Test.fixture(c);UUID target=host(m,A,false);damage(m,A,A,target,(1000-remaining)/10);if(disabled)m.disableToolEffectsByEffect(A,A,target);var source=card(m,A);int before=prizes(m,B);
        if(effect){NonPokemonBatch37Test.top(m,A,source);m.attachToolByEffect(A,A,EffectSourceZone.DECK,source.instanceId(),A,target);NonPokemonBatch2Test.settle(m);check(before-prizes(m,B)==(vmax&&!disabled&&remaining<=100?3:0),"Medal effect attachment did not obey printed exception/HP KO");}
        else if(vmax&&remaining<=100){check(!m.canPlayTrainer(A,source.instanceId(),target),"Medal accepted forbidden hand attachment");GeneratedRuleBatch8Test.reject(()->m.playTrainer(A,source.instanceId(),target));check(m.privateView(A).hand().contains(source)&&prizes(m,B)==before,"Rejected Medal consumed source/awarded Prizes");}
        else{m.playTrainer(A,source.instanceId(),target);check(hp(m,A,false)==(vmax&&!disabled?900:1000),"Legal Medal attachment changed wrong HP");}
    }}
    static void immediateKo(){var c=config(IDS.get(0),PokemonFamily.GX,"Test-GX");var m=GeneratedRuleBatch28Test.fixture(c);UUID target=host(m,A,false);damage(m,A,A,target,90);int before=prizes(m,B);m.playTrainer(A,card(m,A).instanceId(),target);NonPokemonBatch2Test.settle(m);check(before-prizes(m,B)==2,"Amulet reduced Prizes for its own HP-loss KO");}
    static void stack(){var c=config(IDS.get(1),PokemonFamily.VMAX,"Sylveon VMAX");c.ownAbility=dev.hua.ptcglive.rules.BundledCombatData.ability(com.google.gson.JsonParser.parseString("{\"id\":\"two-tools\",\"nameZh\":\"双道具\",\"kind\":\"PASSIVE\",\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"tool_capacity\",\"target\":\"SELF\",\"amount\":2}]}").getAsJsonObject(),EffectRegistry.standard());
        var m=GeneratedRuleBatch28Test.fixture(c);UUID target=host(m,A,false);m.playTrainer(A,card(m,A).instanceId(),target);m.playTrainer(A,card(m,A).instanceId(),target);check(hp(m,A,false)==800,"Two Medals did not stack HP loss");damage(m,A,A,target,70);m.endTurn(A);int before=prizes(m,B);m.attack(B,"hit");NonPokemonBatch2Test.settle(m);check(before-prizes(m,B)==1,"Two Medals did not stack Prize reduction");
    }
}
