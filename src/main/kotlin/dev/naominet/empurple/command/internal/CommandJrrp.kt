package dev.naominet.empurple.command.internal

import dev.naominet.empurple.command.CommandContext
import dev.naominet.empurple.command.ICommand
import dev.naominet.purple.framework.core.Bot
import dev.naominet.purple.framework.utils.MessageBuilder
import java.time.LocalDate

class CommandJrrp(
    override val name: String = "jrrp"
) : ICommand {
    override suspend fun exec(context: CommandContext) {
        if (context.args.isNotBlank()) {
            reply(context, "用法：/jrrp 或 /今日人品")
            return
        }

        val score = calculateScore(context.senderId, LocalDate.now())
        reply(context, "你今天的人品是 $score/100\n${evaluation(score)}")
    }

    private fun reply(context: CommandContext, message: String) {
        Bot.sendGroupMessage(
            context.groupId,
            MessageBuilder().replyGroup(context.message.message_id, message).build()
        )
    }

    companion object {
        /**
         * 根据用户 ID 和日期生成稳定分数：同一用户当天结果相同，次日自动变化。
         */
        fun calculateScore(userId: Long, date: LocalDate): Int {
            val seed = userId xor (date.toEpochDay() * DATE_MIX_CONSTANT)
            val mixed = mix64(seed)
            return java.lang.Long.remainderUnsigned(mixed, 101L).toInt()
        }

        fun evaluation(score: Int): String = when (score) {
            100 -> "大吉！今天的好运已经溢出来了。"
            in 90..99 -> "大吉，今天很适合做期待已久的事。"
            in 75..89 -> "吉，运气不错，放心向前冲吧。"
            in 60..74 -> "小吉，平稳顺利的一天。"
            in 40..59 -> "中平，保持平常心就好。"
            in 20..39 -> "小凶，做事谨慎一点会更稳妥。"
            in 1..19 -> "凶，重要决定不妨多考虑一下。"
            else -> "大凶……今天先好好休息，明天会更好。"
        }

        // SplitMix64 的终结混合步骤，用于消除连续用户 ID 和日期的分布规律。
        private fun mix64(value: Long): Long {
            var result = value
            result = (result xor (result ushr 30)) * -4658895280553007687L
            result = (result xor (result ushr 27)) * -7723592293110705685L
            return result xor (result ushr 31)
        }

        private const val DATE_MIX_CONSTANT = -7046029254386353131L
    }
}
