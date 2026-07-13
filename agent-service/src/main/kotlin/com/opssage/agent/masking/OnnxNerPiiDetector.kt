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
package com.opssage.agent.masking

import ai.djl.huggingface.translator.TokenClassificationTranslatorFactory
import ai.djl.inference.Predictor
import ai.djl.modality.nlp.translator.NamedEntity
import ai.djl.repository.zoo.Criteria
import ai.djl.repository.zoo.ZooModel
import com.opssage.agent.config.NerProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy

import java.nio.file.Paths
import java.util.concurrent.Semaphore

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
@ConditionalOnProperty("agent.masking.ner.enabled", havingValue = "true")
class OnnxNerPiiDetector(
    private val properties: NerProperties,
) : PiiDetector {

    private val model: ZooModel<String, Array<NamedEntity>> = loadModel()

    private val inferenceGate = Semaphore(properties.maxConcurrentInferences)

    override fun detect(text: String): List<PiiSpan> {
        if (text.isBlank()) {
            return emptyList()
        }
        inferenceGate.acquire()
        return try {
            model.newPredictor().use { predictor ->
                predict(predictor, text)
            }
        } finally {
            inferenceGate.release()
        }
    }

    private fun predict(
        predictor: Predictor<String, Array<NamedEntity>>,
        text: String,
    ): List<PiiSpan> =
        predictor
            .predict(text)
            .asSequence()
            .filter { it.score >= properties.scoreThreshold }
            .mapNotNull { entity ->
                val token = properties.entityTokens[label(entity.entity)]
                token?.let { PiiSpan(entity.start, entity.end, it) }
            }.toList()

    private fun label(raw: String): String = raw.substringAfter('-', raw)

    private fun loadModel(): ZooModel<String, Array<NamedEntity>> {
        check(properties.modelPath.isNotBlank()) {
            "agent.masking.ner.enabled=true requires " +
                "agent.masking.ner.model-path to be set"
        }
        log.atInfo {
            message = "Loading ONNX NER model for PII masking"
            payload = mapOf("modelPath" to properties.modelPath)
        }
        val criteria =
            Criteria
                .builder()
                .setTypes(String::class.java, Array<NamedEntity>::class.java)
                .apply {
                    if (properties.modelPath.contains("://")) {
                        optModelUrls(properties.modelPath)
                    } else {
                        optModelPath(Paths.get(properties.modelPath))
                    }
                }.optEngine("OnnxRuntime")
                .optTranslatorFactory(TokenClassificationTranslatorFactory())
                .build()
        return criteria.loadModel()
    }

    @PreDestroy
    fun close() {
        model.close()
    }
}
