/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.q

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.discovery.DiscoveryActivated
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

@Component
open class ExecutionWorker
@Autowired constructor(
  private val queue: Queue,
  @Qualifier("messageHandlerPool") private val executor: Executor,
  val registry: Registry,
  handlers: Collection<MessageHandler<*>>
) : DiscoveryActivated {

  override val log: Logger = getLogger(javaClass)
  override val enabled = AtomicBoolean(false)
  // TODO: this could be neater
  private val handlers = handlers.groupBy(MessageHandler<*>::messageType).mapValues { it.value.first() }

  private val emptyReceivesId = registry.createId("orca.nu.worker.emptyReceives")
  private val pollOpsRateId = registry.createId("orca.nu.worker.pollOpsRate")
  private val pollErrorRateId = registry.createId("orca.nu.worker.pollErrorRate")

  @Scheduled(fixedDelay = 10)
  fun pollOnce() =
    ifEnabled {
      registry.counter(pollOpsRateId).increment()

      val message = queue.poll()
      when (message) {
        null -> {
          log.debug("No events")
          registry.counter(emptyReceivesId).increment()
        }
        else -> {
          log.info("Received message $message")
          val handler = handlers[message.javaClass]
          if (handler != null) {
            executor.execute {
              handler.handleAndAck(message)
            }
          } else {
            registry.counter(pollErrorRateId).increment()

            // TODO: DLQ
            throw IllegalStateException("Unsupported message type ${message.javaClass.simpleName}")
          }
        }
      }
    }
}
