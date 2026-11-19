package dev.hua.ptcglive.economy;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Thread-safe UUID wallet with bounded audit and replay protection. */
public final class PokeCoinLedger implements PokeCoinWallet {
    public enum Kind { MINERAL_EXCHANGE, PACK_PURCHASE, CARD_RECYCLE, ADMIN_MIGRATION, CARD_RENTAL, CARD_APPRAISAL, CARD_ORDER }
    public record Transaction(UUID id, UUID playerId, Kind kind, long delta, long balanceAfter,
                              String detail, Instant recordedAt, long serverTick) { }
    public record SavedBalance(UUID playerId, long balance) { }
    public record RentalReceipt(UUID machine, UUID owner, long paid, long pending, int half, boolean closed, int eighth) {
        public RentalReceipt(UUID machine,UUID owner,long paid,long pending,int half,boolean closed){this(machine,owner,paid,pending,half,closed,half*4);}
        public RentalReceipt(UUID machine, UUID owner,long paid,long pending){this(machine,owner,paid,pending,0,false);}
    }
    public record Snapshot(List<SavedBalance> balances, List<Transaction> history, List<UUID> processedRequests,
                           List<RentalReceipt> rentals) {
        public Snapshot(List<SavedBalance> balances, List<Transaction> history, List<UUID> processedRequests) {
            this(balances,history,processedRequests,List.of());
        }
    }

    private static final int MAX_HISTORY = 10_000;
    private static final int MAX_REQUESTS = 4_096;
    private final Clock clock;
    private final Map<UUID, Long> balances = new HashMap<>();
    private final List<Transaction> history = new ArrayList<>();
    private final LinkedHashSet<UUID> processedRequests = new LinkedHashSet<>();
    private final Map<UUID,RentalReceipt> rentals = new HashMap<>();
    private Runnable changeListener = () -> { };

    public PokeCoinLedger(Clock clock) { this.clock = Objects.requireNonNull(clock); }

    public static PokeCoinLedger restore(Clock clock, Snapshot snapshot) {
        var ledger = new PokeCoinLedger(clock);
        for (var row : snapshot.balances()) {
            if (row.balance() < 0 || ledger.balances.putIfAbsent(row.playerId(), row.balance()) != null)
                throw new IllegalArgumentException("宝可币存档包含无效或重复账户");
        }
        if (snapshot.history().size() > MAX_HISTORY || snapshot.processedRequests().size() > MAX_REQUESTS)
            throw new IllegalArgumentException("宝可币存档超过安全上限");
        ledger.history.addAll(snapshot.history());
        ledger.processedRequests.addAll(snapshot.processedRequests());
        if (snapshot.rentals()!=null) for (var row:snapshot.rentals()) {
            if(row.paid()<0 || row.pending()<row.paid() || row.half()<0||row.half()>1||row.eighth()<0||row.eighth()>7 || ledger.rentals.putIfAbsent(row.machine(),row)!=null)
                throw new IllegalArgumentException("租赁收款存档无效");
        }
        return ledger;
    }

    @Override public synchronized long balance(UUID playerId) { return balances.getOrDefault(playerId, 0L); }
    @Override public synchronized List<Transaction> history(UUID playerId) {
        return history.stream().filter(row -> row.playerId().equals(playerId)).toList();
    }

    public synchronized Transaction credit(UUID requestId, UUID playerId, long amount, Kind kind, String detail, long serverTick) {
        requireNewRequest(requestId); requireAmount(amount); requireTick(serverTick);
        long next = Math.addExact(balance(playerId), amount);
        return commit(requestId, playerId, kind, amount, next, detail, serverTick);
    }

    public synchronized Transaction debit(UUID requestId, UUID playerId, long amount, Kind kind, String detail, long serverTick) {
        requireNewRequest(requestId); requireAmount(amount); requireTick(serverTick);
        long current = balance(playerId);
        if (current < amount) throw new IllegalStateException("宝可币余额不足");
        return commit(requestId, playerId, kind, -amount, current - amount, detail, serverTick);
    }

    /** Restores the complete wallet if another part of a compound transaction fails. */
    public synchronized <T> T transaction(java.util.function.Supplier<T> action) {
        Snapshot before = snapshot();
        Runnable listener = changeListener; changeListener = () -> { };
        try { T value = action.get(); listener.run(); return value; }
        catch (RuntimeException failure) { restoreFrom(before); throw failure; }
        finally { changeListener = listener; }
    }

    public synchronized Snapshot snapshot() {
        var saved = balances.entrySet().stream().map(e -> new SavedBalance(e.getKey(), e.getValue())).toList();
        return new Snapshot(saved, List.copyOf(history), List.copyOf(processedRequests),List.copyOf(rentals.values()));
    }

    public synchronized long rentalPaid(UUID machine) {
        var row=rentals.get(machine); return row==null?0:row.paid();
    }
    /** Permanent cumulative receipts, saved in the SAME snapshot as balances; never evicted with request history. */
    public synchronized long claimRental(UUID machine, UUID owner, long through, long tick) {
        if (through<0) throw new IllegalArgumentException("租赁收益无效");
        var old=rentals.getOrDefault(machine,new RentalReceipt(machine,owner,0,0));
        if(!old.owner().equals(owner))throw new IllegalArgumentException("租赁所有者不符");
        long amount=Math.max(through,old.paid())-old.paid();
        if(amount==0)return 0;
        return transaction(()->{
            credit(UUID.randomUUID(),owner,amount,Kind.CARD_RENTAL,"租赁方块收益",tick);
            rentals.put(machine,new RentalReceipt(machine,owner,through,Math.max(through,old.pending()),old.half(),old.closed(),old.eighth()));
            return amount;
        });
    }
    /** Queue before block destruction; overflow/unavailable payment leaves an owner-bound durable claim. */
    public synchronized void queueRental(UUID machine, UUID owner, long through) {
        var old=rentals.getOrDefault(machine,new RentalReceipt(machine,owner,0,0));
        if(!old.owner().equals(owner)||through<0)throw new IllegalArgumentException("租赁待领取数据无效");
        rentals.put(machine,new RentalReceipt(machine,owner,old.paid(),Math.max(through,old.pending()),old.half(),old.closed(),old.eighth()));
        changeListener.run();
    }
    public synchronized void queueFinalRental(UUID machine,UUID owner,long through,int half) {
        if(half<0||half>1)throw new IllegalArgumentException("租赁余数无效");
        queueFinalRentalEighth(machine,owner,through,half*4);
    }
    public synchronized void queueFinalRentalEighth(UUID machine,UUID owner,long through,int eighth) {
        if(eighth<0||eighth>7)throw new IllegalArgumentException("租赁余数无效");
        queueRental(machine,owner,through);var row=rentals.get(machine);
        if(!row.closed())rentals.put(machine,new RentalReceipt(machine,owner,row.paid(),row.pending(),eighth/4,true,eighth));
        changeListener.run();
    }
    public synchronized void retryRentals(long tick) {
        for(var row:List.copyOf(rentals.values())) if(row.pending()>row.paid()) {
            try { claimRental(row.machine(),row.owner(),row.pending(),tick); }
            catch(ArithmeticException overflow) { /* Keep the claim until the wallet has room. */ }
        }
        var groups=new HashMap<UUID,List<RentalReceipt>>();
        for(var row:rentals.values())if(row.closed()&&row.eighth()>0)groups.computeIfAbsent(row.owner(),id->new ArrayList<>()).add(row);
        for(var group:groups.entrySet()){
            long units=group.getValue().stream().mapToLong(RentalReceipt::eighth).sum();if(units<8)continue;
            try{transaction(()->{
                credit(UUID.randomUUID(),group.getKey(),units/8,Kind.CARD_RENTAL,"租赁方块收益：零币余数合并",tick);
                int rest=(int)(units%8);
                for(var row:group.getValue()){rentals.put(row.machine(),new RentalReceipt(row.machine(),row.owner(),row.paid(),row.pending(),rest/4,true,rest));rest=0;}
                return null;
            });}catch(ArithmeticException overflow){ /* Claims remain until the wallet has space. */ }
        }
    }

    /** Used only by a higher-level compound transaction after another authoritative store failed. */
    public synchronized void restoreSnapshot(Snapshot snapshot) { restoreFrom(snapshot); }

    public synchronized void onChanged(Runnable listener) { changeListener = Objects.requireNonNull(listener); }

    private Transaction commit(UUID requestId, UUID playerId, Kind kind, long delta, long next, String detail, long tick) {
        balances.put(playerId, next); processedRequests.add(requestId);
        while (processedRequests.size() > MAX_REQUESTS) processedRequests.remove(processedRequests.iterator().next());
        var row = new Transaction(requestId, playerId, Objects.requireNonNull(kind), delta, next,
            detail == null ? "" : detail, clock.instant(), tick);
        history.add(row); if (history.size() > MAX_HISTORY) history.remove(0); changeListener.run(); return row;
    }
    private void requireNewRequest(UUID id) {
        Objects.requireNonNull(id, "交易请求 ID 不能为空");
        if (processedRequests.contains(id)) throw new IllegalStateException("该交易请求已处理，请刷新商城");
    }
    private static void requireAmount(long amount) { if (amount < 1) throw new IllegalArgumentException("交易数量必须为正数"); }
    private static void requireTick(long tick) { if (tick < 0) throw new IllegalArgumentException("服务器刻必须为非负数"); }
    private void restoreFrom(Snapshot snapshot) {
        balances.clear(); history.clear(); processedRequests.clear(); rentals.clear();
        snapshot.balances().forEach(row -> balances.put(row.playerId(), row.balance()));
        history.addAll(snapshot.history()); processedRequests.addAll(snapshot.processedRequests());
        if(snapshot.rentals()!=null)snapshot.rentals().forEach(row->rentals.put(row.machine(),row));
    }
}
