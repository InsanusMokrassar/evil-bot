package com.github.djaler.evilbot.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.djaler.evilbot.model.Reaction
import dev.inmo.tgbotapi.bot.RequestsExecutor
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.types.ExtendedBot
import dev.inmo.tgbotapi.types.message.abstracts.ContentMessage
import dev.inmo.tgbotapi.types.message.abstracts.FromUserMessage
import dev.inmo.tgbotapi.types.message.abstracts.Message
import dev.inmo.tgbotapi.types.message.abstracts.PossiblyReplyMessage
import dev.inmo.tgbotapi.types.message.content.TextContent
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct
import javax.validation.ConstraintViolationException
import javax.validation.Validator
import kotlin.random.Random

@Component
class ReactionHandler(
    private val objectMapper: ObjectMapper,
    private val validator: Validator,
    private val botInfo: ExtendedBot,
    private val requestsExecutor: RequestsExecutor
) : MessageHandler() {
    private lateinit var reactions: List<Reaction>

    override val order = Int.MAX_VALUE

    @PostConstruct
    fun init() {
        val resources = PathMatchingResourcePatternResolver().getResources("classpath:reactions/*.json")

        val loadedReactions = mutableListOf<Reaction>()

        for (resource in resources) {
            val reactions: List<Reaction> = objectMapper.readValue(resource.inputStream)
            for (reaction in reactions) {
                val violations = validator.validate(reaction)
                if (violations.isNotEmpty()) {
                    throw ConstraintViolationException(violations)
                }

                loadedReactions.add(reaction)
            }
        }

        reactions = loadedReactions
    }

    override suspend fun handleMessage(message: Message): Boolean {
        if (message !is ContentMessage<*>) {
            return false
        }
        val content = message.content as? TextContent ?: return false

        val messageReplyToBot = isReplyToBot(message)

        for (reaction in reactions) {
            val chance = reaction.chance

            if (chance < Random.nextDouble(0.0, 100.0)) {
                continue
            }

            if (reaction.replyToBot && !messageReplyToBot) {
                continue
            }

            if (reaction.triggers.isNotEmpty() &&
                reaction.triggers.none { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(content.text) }
            ) {
                continue
            }

            requestsExecutor.reply(message, reaction.reactions.random())

            return true
        }

        return false
    }

    private fun isReplyToBot(message: Message): Boolean {
        if (message !is PossiblyReplyMessage) {
            return false
        }

        val replyTo = message.replyTo

        if (replyTo !is FromUserMessage) {
            return false
        }

        return replyTo.user.username == botInfo.username
    }
}
