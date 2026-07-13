package dev.naominet.empurple.utils

import dev.naominet.empurple.maimai.dto.MusicLevel
import dev.naominet.empurple.maimai.dto.UserRatingData
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.RoundRectangle2D
import javax.imageio.ImageIO

object ImageBuilder {
    private val comboImages = mapOf(1 to "FC", 2 to "FCp", 3 to "AP", 4 to "APp")
    private val syncImages = mapOf(1 to "FS", 2 to "FSp", 3 to "FSD", 4 to "FSDp")

    fun drawRatingCard(g: Graphics2D, data: UserRatingData, x: Int, y: Int) {
        g.color = Color(237, 234, 255)
        g.fillRoundRect(x, y, 300, 100, 60, 60)

        val oldClip = g.clip
        g.clip = RoundRectangle2D.Float((x + 10).toFloat(), (y + 10).toFloat(), 80f, 80f, 40f, 40f)
        ResourceHelper.coverImage(data.musicId.toString())?.let { g.drawImage(it, x + 10, y + 10, 80, 80, null) }
        g.clip = oldClip

        comboImages[data.comboStatus]?.let { drawResource(g, "/b50/UI_CHR_PlayBonus_$it.png", x + 148, y + 66, 32, 32) }
        syncImages[data.syncStatus]?.let { drawResource(g, "/b50/UI_CHR_PlayBonus_$it.png", x + 178, y + 66, 32, 32) }

        g.color = Color(61, 61, 61)
        g.font = DesignSystem.miSansRegular(16)
        val title = (data.musicName ?: "Unknown").let { if (it.length > 10) it.take(10) + "..." else it }
        g.drawString(title, x + 100, y + 36)
        g.font = DesignSystem.miSansBold(26)
        g.drawString(data.formatRatingSimple(data.achievement.toLong()), x + 100, y + 65)

        if (data.musicId > 10000) drawResource(g, "/b50/DX.png", x + 230, y + 20, 60, 21)

        val ds = MusicDataProvider.getDs(data.musicId, data.level)
        val result = RatingCalculator.computeRaWithRate(ds, data.achievement / 10000.0)
        drawResource(g, "/b50/UI_TTR_Rank_${result.rate}.png", x + 95, y + 67, 55, 28)

        g.color = when (MusicLevel.fromInt(data.level)) {
            MusicLevel.Basic -> Color(98, 140, 123)
            MusicLevel.Advanced -> Color(181, 131, 0)
            MusicLevel.Expert -> Color(211, 122, 122)
            MusicLevel.Master -> Color(103, 80, 164)
            MusicLevel.ReMaster -> Color(208, 200, 255)
        }
        g.fillRoundRect(x + 225, y + 70, 68, 26, 30, 90)
        g.color = Color(61, 61, 61)
        g.font = DesignSystem.miSansRegular(12)
        g.drawString("RA ${result.ra}", x + 237, y + 87)
    }

    private fun drawResource(g: Graphics2D, path: String, x: Int, y: Int, width: Int, height: Int) {
        val stream = ImageBuilder::class.java.getResourceAsStream(path) ?: return
        stream.use { g.drawImage(ImageIO.read(it), x, y, width, height, null) }
    }
}
