/*
 * Copyright 2026 Ivan Khanas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.opssage.agent.investigation

import com.opssage.agent.config.AgentMemoryProperties
import com.opssage.agent.exception.ConversationNotFoundException
import com.opssage.agent.exception.InvestigationFailedException
import com.opssage.agent.llm.ConversationTurn
import com.opssage.agent.llm.LlmClient
import com.opssage.agent.llm.LlmInvocationException
import com.opssage.agent.model.Conversation
import com.opssage.agent.model.ConversationStatus
import com.opssage.agent.repository.ConversationRepository
import io.github.oshai.kotlinlogging.KotlinLogging

import java.time.Instant

import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

@Service
class InvestigationService(
    private val llmClient: LlmClient,
    private val confidenceCalculator: ConfidenceCalculator,
    private val conversationRepository: ConversationRepository,
    private val anchorWindowResolver: AnchorWindowResolver,
    private val memoryProperties: AgentMemoryProperties,
) {

    fun investigate(request: InvestigationRequest): InvestigationReport {
        val window =
            anchorWindowResolver.resolve(
                request.from,
                request.to,
                request.lookback,
            )
        val base =
            Conversation(
                title = request.title,
                investigationType = request.investigationType,
                anchorWindow = window,
            )
        val systemPrompt =
            SystemPrompts.promptFor(request.investigationType, window)
        return runInvestigation(base, systemPrompt, emptyList(), request.input)
    }

    fun reply(
        conversationId: String,
        input: String,
    ): InvestigationReport {
        val conversation =
            conversationRepository.findById(conversationId)
                ?: throw ConversationNotFoundException(conversationId)

        val window =
            conversation.anchorWindow ?: anchorWindowResolver.resolve()
        val systemPrompt =
            SystemPrompts.promptFor(conversation.investigationType, window)
        val history =
            ConversationTurnMapper.toHistory(
                conversation.messages,
                memoryProperties.maxMessages,
            )
        return runInvestigation(conversation, systemPrompt, history, input)
    }

    private fun runInvestigation(
        base: Conversation,
        systemPrompt: String,
        history: List<ConversationTurn>,
        input: String,
    ): InvestigationReport {
        val verdict =
            try {
                llmClient.investigate(systemPrompt, history, input)
            } catch (ex: LlmInvocationException) {
                persistFailure(base, input, ex)
                throw InvestigationFailedException(
                    "Investigation failed while calling the model",
                    ex,
                )
            }
        val confidence =
            confidenceCalculator.reconcile(
                verdict.confidence,
                verdict.evidence.size,
            )

        val saved =
            conversationRepository.save(
                base.copy(
                    status = ConversationStatus.COMPLETED,
                    messages =
                        base.messages +
                            ConversationMessageFactory.userMessage(input) +
                            ConversationMessageFactory.assistantMessage(
                                verdict.summary,
                                confidence,
                                verdict.evidence,
                            ),
                    updatedAt = Instant.now(),
                ),
            )
        val savedId =
            requireNotNull(saved.id) {
                "Persisted conversation must have an id"
            }

        log.atInfo {
            message = "Completed investigation"
            payload =
                mapOf(
                    "conversationId" to savedId,
                    "type" to base.investigationType,
                    "confidence" to confidence,
                    "evidence" to verdict.evidence.size,
                    "historyTurns" to history.size,
                )
        }

        return InvestigationReport(
            conversationId = savedId,
            investigationType = base.investigationType,
            summary = verdict.summary,
            confidence = confidence,
            evidence = verdict.evidence,
        )
    }

    private fun persistFailure(
        base: Conversation,
        input: String,
        error: Exception,
    ) {
        conversationRepository.save(
            base.copy(
                status = ConversationStatus.FAILED,
                messages =
                    base.messages +
                        ConversationMessageFactory.userMessage(input),
                updatedAt = Instant.now(),
            ),
        )
        log.atWarn {
            message = "Investigation failed"
            payload =
                mapOf(
                    "type" to base.investigationType,
                    "conversationId" to (base.id ?: "new"),
                )
            cause = error
        }
    }
}
