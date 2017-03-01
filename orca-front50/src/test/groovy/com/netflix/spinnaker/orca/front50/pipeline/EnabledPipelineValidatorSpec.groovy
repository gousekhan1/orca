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

package com.netflix.spinnaker.orca.front50.pipeline

import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.PipelineValidator.PipelineValidationFailed
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import spock.lang.Specification
import spock.lang.Subject

class EnabledPipelineValidatorSpec extends Specification {

  def front50Service = Stub(Front50Service)
  @Subject def validator = new EnabledPipelineValidator(front50Service)

  def pipeline = Pipeline
    .builder()
    .withApplication("whatever")
    .withPipelineConfigId("1337")
    .build()

  def "allows one-off pipeline to run"() {
    given:
    front50Service.getPipelines(pipeline.application) >> []

    when:
    validator.checkRunnable(pipeline)

    then:
    notThrown(PipelineValidationFailed)
  }

  def "allows enabled pipeline to run"() {
    given:
    front50Service.getPipelines(pipeline.application) >> [
      [id: pipeline.pipelineConfigId, application: pipeline.application, name: "whatever", disabled: false]
    ]

    when:
    validator.checkRunnable(pipeline)

    then:
    notThrown(PipelineValidationFailed)
  }

  def "prevents disabled pipeline from running"() {
    given:
    front50Service.getPipelines(pipeline.application) >> [
      [id: pipeline.pipelineConfigId, application: pipeline.application, name: "whatever", disabled: true]
    ]

    when:
    validator.checkRunnable(pipeline)

    then:
    thrown(EnabledPipelineValidator.PipelineIsDisabled)
  }

  def "allows enabled strategy to run"() {
    given:
    pipeline.trigger.parameters = [strategy: true]

    and:
    front50Service.getStrategies(pipeline.application) >> [
      [id: pipeline.pipelineConfigId, application: pipeline.application, name: "whatever", disabled: false]
    ]

    when:
    validator.checkRunnable(pipeline)

    then:
    notThrown(PipelineValidationFailed)
  }

  def "prevents disabled strategy from running"() {
    given:
    pipeline.trigger.parameters = [strategy: true]

    and:
    front50Service.getStrategies(pipeline.application) >> [
      [id: pipeline.pipelineConfigId, application: pipeline.application, name: "whatever", disabled: true]
    ]

    when:
    validator.checkRunnable(pipeline)

    then:
    thrown(EnabledPipelineValidator.PipelineIsDisabled)
  }

}
