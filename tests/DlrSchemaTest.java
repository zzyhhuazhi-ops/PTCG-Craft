import com.google.gson.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.engine.effect.*;
import java.nio.file.*;
public final class DlrSchemaTest {
 public static void main(String[] args)throws Exception {
  var root=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/ptcglive/combat/dlr.json"))).getAsJsonObject();
  int bad=0,good=0;var registry=EffectRegistry.standard();
  for(var e:root.getAsJsonArray("cards")) {var c=e.getAsJsonObject();
   try {switch(c.get("kind").getAsString()){case "POKEMON"->BundledCombatData.pokemon(c,registry);case "TRAINER"->BundledCombatData.trainer(c,registry);case "ENERGY"->BundledCombatData.specialEnergy(c,registry);default->throw new AssertionError();}good++;}
   catch(Exception x){bad++;System.out.println(c.get("id").getAsString()+": "+x.getMessage());}
  }
  System.out.println("DLR schema valid="+good+" invalid="+bad);if(bad>0)throw new AssertionError("DLR contains invalid combat entries");
 }
}
