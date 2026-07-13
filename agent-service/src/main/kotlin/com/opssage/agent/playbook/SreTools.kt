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
package com.opssage.agent.playbook

object SreTools {

    const val GET_SERVICE_HEALTH = "getServiceHealth"
    const val GET_SERVICE_CORRECTNESS = "getServiceCorrectness"
    const val GET_ALERT_CONTEXT = "getAlertContext"
    const val GET_KUBERNETES_SERVICE_EVENTS = "getKubernetesServiceEvents"
    const val FIND_TOP_LOG_ERRORS = "findTopLogErrors"
    const val FIND_LOG_ERRORS_BY_TEXT = "findLogErrorsByText"
    const val FIND_SERVICE_TRACES = "findServiceTraces"
    const val FIND_USER_RELATED_TRACES = "findUserRelatedTraces"

    const val SERVICE = "service"
    const val NAMESPACE = "namespace"
    const val LOOKBACK = "lookback"
    const val QUERY = "query"
    const val USER_ID = "userId"
}
