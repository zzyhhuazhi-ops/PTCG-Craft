package dev.hua.ptcglive.rental;
import java.util.List;
public record RentalView(String session,int revision,String cardId,String name,String rarity,int rate,long coins,long balance,
                         int multiplier,int half,String fuelName,int fuelCount,int reserved,
                         List<HeldCard> inventory) {
    public record HeldCard(int slot,String id,String name,String rarity,int rate,int count){}
}
