import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import dev.hua.ptcglive.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;

/** Resource integrity is NOT semantic title verification. Unreviewed images remain pending. */
public final class CardNameAudit {
    public static void main(String[] args) throws Exception {
        String supplied = System.getProperty("ptcglive.test.packDir", "");
        if (supplied.isBlank()) throw new IllegalArgumentException("请设置 -PcardArtPackDir=<高清包目录>");
        Path directory = Path.of(supplied);
        Map<String, ZipFile> packs = new HashMap<>();
        var cards = CardCatalog.load();
        Map<String, CardCatalog.Entry> byId = new HashMap<>();
        for (var card : cards) byId.put(card.id(), card);
        Map<String, String> aliases = new HashMap<>();
        try (var stream = CardNameAudit.class.getClassLoader().getResourceAsStream("assets/ptcglive_mc/card_art_aliases.json")) {
            if (stream == null) throw new AssertionError("缺少高清卡面别名索引");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            root.getAsJsonObject("aliases").entrySet().forEach(entry -> aliases.put(entry.getKey(), entry.getValue().getAsString()));
        }
        var rows = new ArrayList<Map<String, Object>>();
        int present = 0, verified = 0, stale = 0;
        try {
            for (var card : cards) {
                String sourceId = aliases.getOrDefault(card.id(), card.id());
                var source = byId.get(sourceId);
                if (source == null) throw new AssertionError("高清别名指向不存在卡牌：" + card.id() + " -> " + sourceId);
                String filename = "ptcglive-hd-" + source.set() + ".zip";
                if (!packs.containsKey(source.set())) {
                    Path path = directory.resolve(filename);
                    packs.put(source.set(), Files.isRegularFile(path) ? new ZipFile(path.toFile()) : null);
                }
                var zip = packs.get(source.set());
                var entry = zip == null ? null : zip.getEntry("assets/ptcglive_mc/card_art/" + sourceId + ".jpg");
                String sha = "";
                if (entry != null) {
                    try (var stream = zip.getInputStream(entry)) {
                        var digest = MessageDigest.getInstance("SHA-256"); byte[] bytes = new byte[65536]; int count;
                        while ((count = stream.read(bytes)) >= 0) digest.update(bytes, 0, count);
                        sha = HexFormat.of().formatHex(digest.digest());
                    }
                    present++;
                }
                var review = CardNameReviews.all().get(card.id());
                String status = review == null ? "待逐张核对" : sha.equals(review.jpegSha256()) ? "标题已看图核对" : "资源改变或缺失，需重新核对";
                if (review != null) { if (sha.equals(review.jpegSha256())) verified++; else stale++; }
                var row = new LinkedHashMap<String, Object>();
                row.put("cardId", card.id()); row.put("displayLabel", card.label()); row.put("pack", filename);
                if (!sourceId.equals(card.id())) row.put("hdAliasSource", sourceId);
                row.put("imagePresent", entry != null); row.put("jpegSha256", sha); row.put("titleReview", status);
                if (review != null) row.put("review", review);
                rows.add(row);
            }
        } finally { for (var zip : packs.values()) if (zip != null) zip.close(); }
        var ids = cards.stream().map(CardCatalog.Entry::id).collect(java.util.stream.Collectors.toSet());
        if (!ids.containsAll(CardNameReviews.all().keySet())) throw new AssertionError("核对清单引用不存在卡牌");
        var report = new LinkedHashMap<String, Object>();
        report.put("generatedAt", java.time.Instant.now().toString()); report.put("packDirectory", directory.toString());
        report.put("scope", "文件对应与 SHA256 全量扫描；只有有人工视觉核对记录且哈希一致才算标题已核对。不证明官方合法性或图像译文准确。");
        report.put("total", cards.size()); report.put("imagesPresent", present); report.put("titlesReviewed", verified);
        report.put("titlesPending", cards.size() - verified); report.put("staleReviews", stale); report.put("cards", rows);
        var output = Path.of("build/reports/card-name-audit.json"); Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report));
        System.out.println("卡名核对：总计=" + cards.size() + "，高清资源存在=" + present + "，标题已核对=" + verified + "，失效记录=" + stale);
        if (present != cards.size() || stale != 0) throw new AssertionError("高清资源缺失或已核对卡面改变，详见报告");
    }
}
