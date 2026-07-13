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

object PiiSpans {

    fun resolveOverlaps(spans: List<PiiSpan>): List<PiiSpan> {
        val ordered =
            spans.sortedWith(
                compareBy({ it.start }, { -(it.endExclusive - it.start) }),
            )
        val accepted = mutableListOf<PiiSpan>()
        var coveredUpTo = -1
        for (span in ordered) {
            if (span.start >= coveredUpTo) {
                accepted += span
                coveredUpTo = span.endExclusive
            }
        }
        return accepted
    }
}
