/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.batch.pipeline

import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher
import com.netflix.spinnaker.orca.pipeline.PipelineLauncher
import com.netflix.spinnaker.orca.pipeline.PipelineStartTracker
import com.netflix.spinnaker.orca.pipeline.PipelineStarterListener
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.ExecutionStatus.CANCELED

class PipelineStarterListenerSpec extends Specification {

  def executionRepository = Mock(ExecutionRepository)
  def startTracker = Mock(PipelineStartTracker)
  def pipelineLauncher = Mock(PipelineLauncher)

  @Subject
  PipelineStarterListener listener = new PipelineStarterListener(executionRepository, startTracker, null) {
    @Override
    protected ExecutionLauncher<Pipeline> getPipelineLauncher() {
      return PipelineStarterListenerSpec.this.pipelineLauncher
    }
  }

  def "should do nothing if there is no started pipelines"() {
    given:
    startTracker.getAllStartedExecutions() >> []

    when:
    listener.afterExecution(null, null, null, true)

    then:
    0 * executionRepository._
  }

  def "should start next pipeline in the queue"() {
    given:
    startTracker.getAllStartedExecutions() >> [completedId]
    startTracker.getQueuedPipelines(_) >> [nextId]
    executionRepository.retrievePipeline(_) >> { String id ->
      new Pipeline(id: id, pipelineConfigId: pipelineConfigId, canceled: true, status: CANCELED)
    }

    when:
    listener.afterExecution(null, null, null, true)

    then:
    1 * startTracker.markAsFinished(pipelineConfigId, completedId)

    and:
    1 * pipelineLauncher.start({ it.id == nextId })
    1 * startTracker.removeFromQueue(pipelineConfigId, nextId)

    where:
    completedId = "123"
    nextId = "124"
    pipelineConfigId = "abc"
  }

  def "should mark waiting pipelines that are not started as canceled"() {
    given:
    startTracker.getAllStartedExecutions() >> [completedId]
    startTracker.getQueuedPipelines(_) >> nextIds
    executionRepository.retrievePipeline(_) >> { String id ->
      new Pipeline(id: id, pipelineConfigId: pipelineConfigId, canceled: true, status: CANCELED)
    }

    when:
    listener.afterExecution(null, null, null, true)

    then:
    0 * executionRepository.cancel(nextIds[0])
    1 * executionRepository.cancel(nextIds[1])
    1 * executionRepository.cancel(nextIds[2])

    and:
    1 * startTracker.removeFromQueue(pipelineConfigId, nextIds[0])
    1 * startTracker.removeFromQueue(pipelineConfigId, nextIds[1])
    1 * startTracker.removeFromQueue(pipelineConfigId, nextIds[2])

    where:
    completedId = "123"
    nextIds = ["124", "125", "126"]
    pipelineConfigId = "abc"
  }

  def "if configured to not limit waiting pipelines, then should keep the waiting pipelines in queue"() {
    given:
    startTracker.getAllStartedExecutions() >> [completedId]
    startTracker.getQueuedPipelines(_) >> nextIds
    executionRepository.retrievePipeline(_) >> { String id ->
      new Pipeline(id: id, pipelineConfigId: pipelineConfigId, canceled: true, status: CANCELED, keepWaitingPipelines: true)
    }

    when:
    listener.afterExecution(null, null, null, true)

    then:
    0 * executionRepository.cancel(nextIds[0])
    0 * executionRepository.cancel(nextIds[1])
    0 * executionRepository.cancel(nextIds[2])

    and:
    1 * startTracker.removeFromQueue(pipelineConfigId, nextIds[0])
    0 * startTracker.removeFromQueue(pipelineConfigId, nextIds[1])
    0 * startTracker.removeFromQueue(pipelineConfigId, nextIds[2])

    where:
    completedId = "123"
    nextIds = ["124", "125", "126"]
    pipelineConfigId = "abc"
  }

  def "should mark started executions as finished even without a pipelineConfigId"() {
    when:
    listener.afterExecution(null, null, null, true)

    then:
    1 * startTracker.getAllStartedExecutions() >> ['123']
    1 * executionRepository.retrievePipeline(_) >> new Pipeline(id: '123', canceled: true, status: CANCELED)
    1 * startTracker.markAsFinished(null, '123')
    0 * _._
  }

  def "should process all pipelines in a completed state"() {
    when:
    listener.afterExecution(null, null, null, true)

    then:
    1 * startTracker.getAllStartedExecutions() >> ['123', '124', '125', '126']
    1 * executionRepository.retrievePipeline('123') >> new Pipeline(id: '123')
    1 * executionRepository.retrievePipeline('124') >> new Pipeline(id: '124', canceled: true, status: CANCELED)
    1 * executionRepository.retrievePipeline('125') >> new Pipeline(id: '125')
    1 * executionRepository.retrievePipeline('126') >> new Pipeline(id: '126', canceled: true, status: CANCELED)
    1 * startTracker.markAsFinished(null, '124')
    1 * startTracker.markAsFinished(null, '126')
    0 * _._
  }

  def "should skip pipelines that are not started"() {
    when:
    listener.afterExecution(null, null, null, true)

    then:
    1 * startTracker.getAllStartedExecutions() >> ['123']
    1 * executionRepository.retrievePipeline(_) >> new Pipeline(id: '123')
    0 * _._
  }

}
