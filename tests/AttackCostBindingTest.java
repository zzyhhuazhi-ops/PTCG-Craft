import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import java.util.*;

/**
 * “You may … If you do, this attack does N more damage (for each card you discarded in this way).” and
 * “You may do N more damage. If you do, …”: a pre-damage cost step records how many cards it actually moved under
 * a name (bind), opt_in records acceptance, and later text of the same attack reads it with BOUND_COUNT or
 * damage_per_bound. A declined or skipped step reads as 0.
 */
public final class AttackCostBindingTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    static final String IF_PAID="{\"type\":\"conditional\",\"conditions\":[{\"type\":\"BOUND_COUNT\",\"bind\":\"paid\",\"value\":1}],"
        +"\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":50}]}";
    static final String DISCARD_ALL="{\"type\":\"choice\",\"optional\":true,\"effects\":[{\"type\":\"discard_energy\",\"target\":\"OWN_ACTIVE\",\"all\":true,\"bind\":\"paid\"}]}";
    public static void main(String[] args){
        discardAllForMore(); upToPerCard(); doMoreDamageWithDrawback(); handDiscardForMore(); invalidData();
        System.out.println("AttackCostBindingTest passed: 伤害前可选代价记录实际弃置张数，伤害阶段按“做了没有”／每张追加伤害，不做则不加");
    }
    static void giveEnergy(ServerMatch m,int count){
        UUID active=m.activePokemonView(A).pokemonId();
        for(int i=0;i<count;i++){
            var energy=m.privateView(A).hand().stream().filter(c->Set.of("energy","special").contains(c.definitionId())).findFirst().orElseThrow();
            m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,energy.instanceId(),A,active);
        }
    }
    static int energy(ServerMatch m){return m.activePokemonView(A).attachedEnergyIds().size();}
    static void discardAllForMore(){
        var a=BeforeDamageAttackTest.attack(DISCARD_ALL,IF_PAID,"");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"弃全部能量追加伤害被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        // 接受：弃掉全部能量，(40+50)×2
        var m=BeforeDamageAttackTest.fixture(a);giveEnergy(m,1);
        m.attack(A,a.id());
        var choice=m.effectChoicePrompt(A);
        check(choice.optionsZh().size()==2&&choice.optionsZh().getLast().equals("不发动"),"可选代价没有“不发动”选项");
        check(m.activePokemonView(B).damage()==0,"伤害在选择代价前就已造成");
        m.chooseEffectOption(A,choice.resolutionId(),choice.step(),0);
        check(energy(m)==0&&m.activePokemonView(B).damage()==180,"弃置全部能量后没有追加伤害：能量 "+energy(m)+" 伤害 "+m.activePokemonView(B).damage());
        // 拒绝：不弃能量，不追加
        var d=BeforeDamageAttackTest.fixture(a);giveEnergy(d,1);
        d.attack(A,a.id());
        var no=d.effectChoicePrompt(A);d.chooseEffectOption(A,no.resolutionId(),no.step(),1);
        check(energy(d)==1&&d.activePokemonView(B).damage()==80,"不发动时不应弃能量或追加伤害");
        // 接受但身上没有能量：什么都没弃，等于没做
        var e=BeforeDamageAttackTest.fixture(a);
        e.attack(A,a.id());
        if(e.hasPendingEffectChoice()){var p=e.effectChoicePrompt(A);e.chooseEffectOption(A,p.resolutionId(),p.step(),0);}
        check(e.activePokemonView(B).damage()==80,"没有能量可弃时不应追加伤害："+e.activePokemonView(B).damage());
    }
    static void upToPerCard(){
        var a=BeforeDamageAttackTest.attack("{\"type\":\"discard_energy\",\"target\":\"OWN_ACTIVE\",\"amount\":2,\"minimum\":0,\"bind\":\"paid\"}",
            "{\"type\":\"damage_per_bound\",\"target\":\"OPPONENT_ACTIVE\",\"bind\":\"paid\",\"amount\":30}","");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"每弃一张追加伤害被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        for(int picked=0;picked<=2;picked++){
            var m=BeforeDamageAttackTest.fixture(a);giveEnergy(m,3);
            m.attack(A,a.id());
            var prompt=m.effectCardPrompt(A);
            check(prompt.minimum()==0&&prompt.maximum()==2,"最多弃 2 张的选择范围不对");
            m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),prompt.candidates().stream().limit(picked).map(c->c.instanceId()).toList());
            check(energy(m)==3-picked&&m.activePokemonView(B).damage()==(40+30*picked)*2,
                "弃 "+picked+" 张时伤害应为 "+(40+30*picked)*2+"，实际 "+m.activePokemonView(B).damage());
        }
    }
    static void doMoreDamageWithDrawback(){
        var a=BeforeDamageAttackTest.attack("{\"type\":\"choice\",\"optional\":true,\"effects\":[{\"type\":\"opt_in\",\"bind\":\"more\"}]}",
            "{\"type\":\"conditional\",\"conditions\":[{\"type\":\"BOUND_COUNT\",\"bind\":\"more\",\"value\":1}],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":40}]}",
            "{\"type\":\"conditional\",\"conditions\":[{\"type\":\"BOUND_COUNT\",\"bind\":\"more\",\"value\":1}],\"effects\":[{\"type\":\"self_damage\",\"target\":\"SELF\",\"amount\":20}]}");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"“You may do N more damage” 被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        for(int option=0;option<=1;option++){
            var m=BeforeDamageAttackTest.fixture(a);
            m.attack(A,a.id());
            var choice=m.effectChoicePrompt(A);
            m.chooseEffectOption(A,choice.resolutionId(),choice.step(),option);
            boolean yes=option==0;
            check(m.activePokemonView(B).damage()==(yes?160:80)&&m.activePokemonView(A).damage()==(yes?40:0),
                (yes?"选择追加":"不追加")+"时伤害或自伤不对：对方 "+m.activePokemonView(B).damage()+" 自己 "+m.activePokemonView(A).damage());
            check(m.currentPlayer().equals(B),"攻击没有结束");
        }
    }
    static void handDiscardForMore(){
        var a=BeforeDamageAttackTest.attack("{\"type\":\"discard\",\"target\":\"HAND\",\"amount\":1,\"minimum\":0,\"card_category\":\"TRAINER\",\"bind\":\"paid\"}",IF_PAID,"");
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"弃手牌追加伤害被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        for(int picked=0;picked<=1;picked++){
            var m=BeforeDamageAttackTest.fixture(a);int hand=m.privateView(A).hand().size();
            m.attack(A,a.id());
            var prompt=m.effectCardPrompt(A);
            check(prompt.candidates().stream().allMatch(c->Set.of("tool","stadium").contains(c.definitionId())),"候选里混进了非训练家卡");
            m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),prompt.candidates().stream().limit(picked).map(c->c.instanceId()).toList());
            check(m.activePokemonView(B).damage()==(picked==1?180:80),"弃 "+picked+" 张手牌时伤害不对："+m.activePokemonView(B).damage());
        }
    }
    static void invalidData(){
        AttackProgramRules.validate(BeforeDamageAttackTest.attack("{\"type\":\"return_energy\",\"target\":\"OWN_ACTIVE\",\"all\":true,\"bind\":\"paid\"}",IF_PAID,""));
        for(String[] bad:List.<String[]>of(
            // 读取未定义的计数
            new String[]{"",IF_PAID,""},
            // 计数只能在伤害前记录
            new String[]{"",IF_PAID.replace("\"paid\"","\"x\""),"{\"type\":\"discard_energy\",\"target\":\"OWN_ACTIVE\",\"all\":true,\"bind\":\"x\"}"},
            new String[]{"","","{\"type\":\"choice\",\"optional\":true,\"effects\":[{\"type\":\"opt_in\",\"bind\":\"more\"}]}"},
            // 同名计数重复定义；重复效果里记录
            new String[]{DISCARD_ALL+","+DISCARD_ALL,IF_PAID,""},
            new String[]{"{\"type\":\"repeat\",\"count\":2,\"effects\":[{\"type\":\"opt_in\",\"bind\":\"more\"}]}","",""},
            // bind 用在不能记录的效果上
            new String[]{"{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":0,\"bind\":\"paid\"}",IF_PAID,""},
            // damage_per_bound 只能在伤害计算阶段，且要有正确的伤害目标与数值
            new String[]{DISCARD_ALL,"","{\"type\":\"damage_per_bound\",\"target\":\"OPPONENT_ACTIVE\",\"bind\":\"paid\",\"amount\":30}"},
            new String[]{DISCARD_ALL,"{\"type\":\"damage_per_bound\",\"target\":\"SELF\",\"bind\":\"paid\",\"amount\":30}",""},
            new String[]{DISCARD_ALL,"{\"type\":\"damage_per_bound\",\"target\":\"OPPONENT_ACTIVE\",\"bind\":\"paid\",\"amount\":25}",""},
            // 条件参数
            new String[]{DISCARD_ALL,IF_PAID.replace("\"value\":1","\"value\":0"),""}))
            {
                try {AttackProgramRules.validate(BeforeDamageAttackTest.attack(bad[0],bad[1],bad[2]));}
                catch(IllegalArgumentException|IllegalStateException expected){continue;}
                throw new AssertionError("Unexpected accepted contract: "+Arrays.toString(bad));
            }
        // opt_in 缺 bind：解析数据时就拒绝
        BeforeDamageAttackTest.reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack("{\"type\":\"choice\",\"optional\":true,\"effects\":[{\"type\":\"opt_in\"}]}","","")));
        var trainer=new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_CARD_PLAYED,
            "{\"type\":\"discard\",\"target\":\"HAND\",\"amount\":1,\"minimum\":1,\"bind\":\"paid\"},"+IF_PAID.replace("\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":50","\"type\":\"draw\",\"amount\":1"))));
        check(ResumableProgramRules.unsupportedReason(trainer,false).isEmpty(),"训练家应能绑定实际手牌弃置张数后抽牌");
        var trainerDamage=new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_CARD_PLAYED,
            "{\"type\":\"discard\",\"target\":\"HAND\",\"amount\":1,\"minimum\":1,\"bind\":\"paid\"},"+IF_PAID)));
        check(ResumableProgramRules.unsupportedReason(trainerDamage,false).isPresent(),"训练家的动作计数不能开放攻击伤害");
        var ability=new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ONCE_PER_TURN,
            "{\"type\":\"choice\",\"optional\":true,\"effects\":[{\"type\":\"opt_in\",\"bind\":\"more\"}]}")));
        check(ResumableProgramRules.unsupportedReason(ability,true).isPresent(),"特性不能使用 opt_in");
    }
}
