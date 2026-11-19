import com.google.gson.*;
import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.CardExclusions;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Whole SV/ME catalogue audit, including unsupported cards and errors suppressed by the tolerant production loader. */
public final class ModernCardRuleAudit {
    static JsonObject read(Path p)throws Exception{return JsonParser.parseString(Files.readString(p,StandardCharsets.UTF_8)).getAsJsonObject();}
    static void reason(List<String> out,Optional<String> why,String label){why.ifPresent(s->out.add(label+": "+s));}
    public static void main(String[] args)throws Exception {
        boolean allEras=args.length>1&&args[1].equals("all");
        var catalog=CardCatalog.load();var db=BundledRules.loadCardDefinitions();var combat=BundledCombatData.load();
        var defs=new HashMap<String,CardDefinition>();db.all().forEach(d->defs.put(d.id(),d));
        var registry=EffectRegistry.standard();var raw=new LinkedHashMap<String,JsonObject>();var rawErrors=new TreeMap<String,String>();
        Path source=Path.of("src/main/resources/data/ptcglive/combat");
        for(var file:read(source.resolve("index.json")).getAsJsonArray("files"))for(var item:read(source.resolve(file.getAsString())).getAsJsonArray("cards")) {
            var c=item.getAsJsonObject();String id=c.get("id").getAsString();if(CardExclusions.contains(id))continue;raw.put(id,c);
            try {switch(c.get("kind").getAsString()) {
                case "POKEMON" -> BundledCombatData.pokemon(c,registry);
                case "TRAINER" -> BundledCombatData.trainer(c,registry);
                case "ENERGY" -> {if(c.has("energyUnits"))BundledCombatData.specialEnergy(c,registry);else EnergyType.valueOf(c.get("energyType").getAsString());}
                default -> throw new IllegalArgumentException("unknown kind");
            }}catch(RuntimeException ex){rawErrors.put(id,file.getAsString()+": "+ex.getMessage());}
        }
        var rows=new ArrayList<Map<String,Object>>();var eraCounts=new TreeMap<String,Map<String,Integer>>();
        int attacks=0,abilities=0;var issuesByCard=new TreeMap<String,List<String>>();
        for(var e:catalog) {
            if(!allEras&&!Set.of("sv","me").contains(e.era()))continue;
            var row=new LinkedHashMap<String,Object>();row.put("id",e.id());row.put("name",e.nameZh());row.put("era",e.era());row.put("set",e.set());
            var issues=new ArrayList<String>();var d=defs.get(e.id());
            if(d==null)issues.add("缺少卡牌规则定义");
            else if(d.effectSupport()!=EffectSupport.SUPPORTED)issues.add("规则未完成: "+d.unsupportedReasonZh());
            if(rawErrors.containsKey(e.id()))issues.add("加载失败: "+rawErrors.get(e.id()));
            var profile=combat.fieldPokemon(e.id()).orElse(null);
            if(d!=null) {
                if(d.category()==CardCategory.POKEMON&&profile==null)issues.add("缺少可加载宝可梦战斗数据");
                if(d.category()==CardCategory.TRAINER&&combat.trainer(e.id()).isEmpty())issues.add("缺少可加载训练家效果");
                if(d.category()==CardCategory.ENERGY&&combat.energyProfile(e.id()).isEmpty())issues.add("缺少可加载能量规则");
            }
            if(profile!=null) {
                attacks+=profile.attacks().size();abilities+=profile.abilities().size();
                row.put("attacks",profile.attacks().stream().map(AttackDefinition::nameZh).toList());row.put("abilities",profile.abilities().stream().map(AbilityDefinition::nameZh).toList());
                for(var a:profile.attacks())reason(issues,AttackProgramRules.unsupportedReason(a),"招式 "+a.nameZh());
                for(var a:profile.abilities()) {
                    if(a.effectSupport()!=EffectSupport.SUPPORTED)issues.add("特性 "+a.nameZh()+": "+a.unsupportedReasonZh());
                    if(a.activated()&&a.composed())reason(issues,ActivatedAbilityRules.unsupportedReason(a),"主动特性 "+a.nameZh());
                    if(a.triggered())reason(issues,TriggeredAbilityRules.unsupportedReason(a),"触发特性 "+a.nameZh());
                }
                var r=raw.get(e.id());if(r!=null) {
                    if(r.has("attacks")&&r.getAsJsonArray("attacks").size()!=profile.attacks().size())issues.add("加载时招式数量变化");
                    if(r.has("abilities")&&r.getAsJsonArray("abilities").size()!=profile.abilities().size())issues.add("加载时特性数量变化");
                }
            }
            combat.trainer(e.id()).filter(TrainerProfile::composed).ifPresent(t->reason(issues,TrainerProgramRules.unsupportedReason(t.effectProgram()),"训练家程序"));
            String status=issues.isEmpty()?"静态检查通过":"待修复或未实现";
            row.put("status",status);row.put("issues",issues);rows.add(row);
            eraCounts.computeIfAbsent(e.era(),k->new TreeMap<>()).merge(status,1,Integer::sum);
            if(!issues.isEmpty())issuesByCard.put(e.id(),issues);
        }
        var report=new LinkedHashMap<String,Object>();report.put("scope",(allEras?"当前项目全世代目录":"当前项目目录中朱紫(sv)、Mega(me)所有印刷")+"；静态覆盖审计不等于每张卡全部组合实战通过");
        report.put("cards",rows.size());report.put("eras",eraCounts);report.put("attacksChecked",attacks);report.put("abilitiesChecked",abilities);
        report.put("invalidCombatEntriesAllEras",rawErrors);report.put("cardsWithIssues",issuesByCard.size());report.put("items",rows);
        Path out=Path.of(args.length==0?"outputs/rule-repair-316":args[0]);Files.createDirectories(out);
        String reportName=allEras?"all-era-rule-audit":"sv-me-rule-audit";
        Files.writeString(out.resolve(reportName+".json"),new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report),StandardCharsets.UTF_8);
        var text=new StringBuilder("世代\t卡牌ID\t卡名\t系列\t检查结果\t问题\n");
        for(var r:rows)text.append(r.get("era")).append('\t').append(r.get("id")).append('\t').append(r.get("name")).append('\t').append(r.get("set")).append('\t').append(r.get("status")).append('\t').append(r.get("issues")).append('\n');
        Files.writeString(out.resolve(reportName+".tsv"),"\uFEFF"+text,StandardCharsets.UTF_8);
        System.out.println("ModernCardRuleAudit: cards="+rows.size()+", attacks="+attacks+", abilities="+abilities+", issues="+issuesByCard.size()+", invalidLoadEntries="+rawErrors.size()+", eras="+eraCounts);
        if(!rawErrors.isEmpty())throw new AssertionError("Combat loader errors: "+rawErrors);
    }
}
