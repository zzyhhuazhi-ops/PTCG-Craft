import com.google.gson.*;
import com.sun.net.httpserver.*;
import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.collection.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Development-only, loopback-only, read-only view of the real classification engine. */
public final class CollectionPreview {
    private static final Gson JSON=new Gson();
    private static final List<CardCatalog.Entry> CARDS=CardCatalog.load();
    public static void main(String[] args) throws Exception {
        final JsonObject aliases;
        try(var input=CollectionPreview.class.getResourceAsStream("/assets/ptcglive_mc/card_art_aliases.json")) {
            aliases=JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("aliases");
        }
        var server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),9887),0);
        var staticFiles=Map.of("/","index.html","/app.js","app.js","/style.css","style.css");
        server.createContext("/",exchange->{
            try {
                String path=exchange.getRequestURI().getPath();byte[] body;String mime;
                if(path.equals("/api/meta")&&exchange.getRequestMethod().equals("GET")) {
                    body=JSON.toJson(Map.of("facets",CollectionSearch.facets(),"formats",CollectionSearch.formats(),"count",CARDS.size())).getBytes(StandardCharsets.UTF_8);mime="application/json";
                } else if(path.equals("/api/query")&&exchange.getRequestMethod().equals("POST")) {
                    byte[] request=exchange.getRequestBody().readNBytes(16385);
                    if(request.length>16384)throw new IllegalArgumentException("request too large");
                    var obj=JsonParser.parseString(new String(request,StandardCharsets.UTF_8)).getAsJsonObject();
                    var filter=CollectionSearch.parseFilters(obj.get("filter").toString());
                    var terms=CollectionSearch.queryTerms(obj.get("query").getAsString());
                    var results=CARDS.stream().filter(c->CollectionSearch.matches(c,0,0,filter,terms)).sorted(CollectionSearch.comparator(filter)).toList();
                    body=JSON.toJson(Map.of("total",results.size(),"rows",results.stream().limit(120).toList())).getBytes(StandardCharsets.UTF_8);mime="application/json";
                } else if(path.startsWith("/art/")&&exchange.getRequestMethod().equals("GET")) {
                    String id=path.substring(5);
                    if(!id.matches("[a-z0-9_-]+"))throw new IllegalArgumentException("invalid art id");
                    if(aliases.has(id))id=aliases.get(id).getAsString();
                    try(var input=CollectionPreview.class.getResourceAsStream("/assets/ptcglive_mc/textures/item/cards/"+id+".png")) {
                        if(input==null)throw new IllegalArgumentException("image unavailable");body=input.readAllBytes();mime="image/png";
                    }
                } else if(staticFiles.containsKey(path)&&exchange.getRequestMethod().equals("GET")) {
                    body=Files.readAllBytes(Path.of("tools/collection-preview",staticFiles.get(path)));
                    mime=path.endsWith(".js")?"text/javascript":path.endsWith(".css")?"text/css":"text/html";
                } else {exchange.sendResponseHeaders(404,-1);return;}
                exchange.getResponseHeaders().set("Content-Type",mime+"; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control","no-store");
                exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);
            } catch(Exception error) {
                byte[] body=JSON.toJson(Map.of("error",String.valueOf(error.getMessage()))).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400,body.length);exchange.getResponseBody().write(body);
            } finally {exchange.close();}
        });
        server.start();System.out.println("Read-only collection preview: http://127.0.0.1:9887 — no Minecraft or player data");
    }
}
