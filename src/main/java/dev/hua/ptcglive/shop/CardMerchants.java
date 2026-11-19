package dev.hua.ptcglive.shop;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Registers only the retired ID so old chunks decode safely; no spawn or trading hooks remain. */
public final class CardMerchants {
    public static final EntityType<CardMerchantEntity> TYPE = Registry.register(Registries.ENTITY_TYPE,
        Identifier.of("ptcglive", "card_pack_merchant"), EntityType.Builder.create(CardMerchantEntity::new, SpawnGroup.MISC)
            .dimensions(0.6f, 1.8f).eyeHeight(1.62f).maxTrackingRange(1).build("ptcglive:card_pack_merchant"));
    private CardMerchants() { }
    public static void initialize() { FabricDefaultAttributeRegistry.register(TYPE, VillagerEntity.createVillagerAttributes()); }
}
