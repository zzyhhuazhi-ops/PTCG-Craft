import dev.hua.ptcglive.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.packs.*;
import dev.hua.ptcglive.economy.*;
import java.util.*;
import java.nio.file.*;
public final class DlrInstalledDataTest {
 public static void main(String[] args)throws Exception {
  var db=BundledRules.loadCardDefinitions();var combat=BundledCombatData.load();var pack=PackCatalog.loadBundled().get("delta_reign");var ids=new HashSet<String>();
  pack.pools().values().forEach(pool->ids.addAll(pool.keySet()));if(ids.size()!=141||pack.cardsPerPack()!=5||EconomyConfig.loadBundled().packPrice()!=20||pack.identity().artVariants().size()!=4)throw new AssertionError("DLR pack registration/economy");
  if(CardCatalog.load().stream().filter(c->c.id().startsWith("me6_en_")).count()!=141)throw new AssertionError("catalog count");
  for(int i=1;i<=141;i++){
   String id="me6_en_"+String.format("%03d",i);var c=db.find(id).orElseThrow();if(c.effectSupport()!=EffectSupport.SUPPORTED||!ids.contains(id))throw new AssertionError("unsupported or unreachable "+id);
   switch(c.category()){
    case POKEMON->{var p=combat.pokemon(id).orElseThrow();for(var a:p.attacks())if(a.effectSupport()!=EffectSupport.SUPPORTED)throw new AssertionError(id+" attack");for(var a:p.abilities())if(a.effectSupport()!=EffectSupport.SUPPORTED)throw new AssertionError(id+" ability");}
    case TRAINER->combat.trainer(id).orElseThrow();case ENERGY->combat.energyProfile(id).orElseThrow();
   }
   for(String resource:List.of("assets/ptcglive/models/item/"+id+".json","assets/ptcglive_mc/textures/item/cards/"+id+".png","assets/ptcglive_mc/card_thumb/"+id+".jpg"))if(DlrInstalledDataTest.class.getClassLoader().getResource(resource)==null)throw new AssertionError(resource);
  }
  System.out.println("DLR installed data passed: 141 supported cards, 141 reachable pack entries, 4 covers, 5 cards/20 coins, item models and thumbnails");
 }
}
