import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.client.PtcgLiveTableLayout;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Development-only loopback harness. Real MatchSession, synthetic cards; never accesses a Minecraft save. */
public final class BattleExperiencePreview {
    static final Gson JSON=new Gson();static MatchSession session;
    static void reset(){var attack=new AttackDefinition("hit","测试火焰",Map.of(),60,EnergyType.FIRE,EffectSupport.SUPPORTED,null);
        session=MatchSession.multiplayer(BattleExperienceTest.fixture(EnergyType.FIRE,null,attack),BattleExperienceTest.A,BattleExperienceTest.B);session.addSpectator(BattleExperienceTest.C);}
    public static void main(String[] args)throws Exception {
        reset();var server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),9888),0);
        server.createContext("/",e->{try {
            String mime="application/json",message="";byte[] body;
            if(e.getRequestURI().getPath().equals("/")&&e.getRequestMethod().equals("GET")){body=Files.readAllBytes(Path.of("tools/battle-experience-preview.html"));mime="text/html";}
            else if(e.getRequestURI().getPath().equals("/state")&&e.getRequestMethod().equals("POST")){
                byte[] bytes=e.getRequestBody().readNBytes(4097);if(bytes.length>4096)throw new IllegalArgumentException("过大请求");
                var q=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();String side=q.get("seat").getAsString();
                UUID seat=side.equals("A")?BattleExperienceTest.A:side.equals("B")?BattleExperienceTest.B:null;
                UUID connection=seat==null?BattleExperienceTest.C:seat;
                synchronized(BattleExperiencePreview.class){
                    if(q.has("reset"))reset();
                    if(q.has("option"))try {session.submit(connection,seat,q.get("revision").getAsLong(),q.get("option").getAsInt());}catch(IllegalArgumentException|IllegalStateException rejected){message=rejected.getMessage();}
                    int w=Math.max(320,Math.min(1920,q.get("width").getAsInt())),h=Math.max(240,Math.min(1080,q.get("height").getAsInt()));
                    body=JSON.toJson(Map.of("snapshot",session.view(connection,seat),"layout",PtcgLiveTableLayout.forScreen(w,h),"message",message)).getBytes(StandardCharsets.UTF_8);
                }
            }else{e.sendResponseHeaders(404,-1);return;}
            e.getResponseHeaders().set("Content-Type",mime+"; charset=utf-8");e.getResponseHeaders().set("Cache-Control","no-store");e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);
        }catch(Exception ex){byte[] body=JSON.toJson(Map.of("error",ex.toString())).getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(400,body.length);e.getResponseBody().write(body);}finally{e.close();}});
        server.start();System.out.println("Battle engine verification: http://127.0.0.1:9888 (synthetic fixtures, not Minecraft rendering)");
    }
}
