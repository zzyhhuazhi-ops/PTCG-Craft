package dev.hua.ptcglive.client;

import net.minecraft.util.Identifier;

/**
 * 任何已上传到显卡的卡图。两种卡图缓存的 Art record 都实现它，
 * 这样 {@link CardPainter} 可以用同一套画法处理缩略图和高清图。
 */
interface CardArt {
    Identifier texture();
    int width();
    int height();
}
