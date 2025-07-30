/*-
 * #%L
 * dcapi.dettonville.org
 * %%
 * Copyright (C) 2024 Dettonville DevOps
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.dettonville.pipeline.summary

/**
 * Class for keeping tabs on the pipeline progression.
 * It can then be used to create a summary at the of the pipeline via its toString method.
 *
 * The createSummaryHtml method generates a html summary page.
 */
class PipelineSummary implements Serializable {
    String result
    String previousResult
    long duration
    Date start
    private ArrayList<SkippedStep> skippedSteps = new ArrayList<>();
    private ArrayList<ImpactfulAction> impactfulActions = new ArrayList<>();
    def steps

    PipelineSummary(steps){
        this.steps = steps
    }

    /**
     * Add a skippedStep to the list.
     * @param step
     */
    void addStep(SkippedStep step) {
        skippedSteps.add(step)
    }

    void addAction(ImpactfulAction impactfulAction) {
        impactfulActions.add(impactfulAction)
    }

    @Override
    String toString() {
        String skippedStepsString = ""
        for (SkippedStep step : skippedSteps) {
            skippedStepsString += step.toString()
        }

        String impactfulActionsString = ""
        for (ImpactfulAction action : impactfulActions) {
            impactfulActionsString += action.toString()
        }

        return '<h1>Impactful Actions</h1>' +
                '<ul>' +
                impactfulActionsString +
                '</ul>' +
            '<h1>Skipped Steps</h1>' +
                '<ul>' +
                skippedStepsString +
                '</ul>'
    }

    private void processEndOfPipeline(){
        result = steps.currentBuild.result
        if (result == null) {
            result = 'SUCCESS'
        }
        start = new Date((Long)steps.currentBuild.timeInMillis)
        duration = (System.currentTimeMillis() - steps.currentBuild.startTimeInMillis) / 1000
        if (steps.currentBuild.previousBuild != null) {
            previousResult = steps.currentBuild.previousBuild.result
        } else {
            previousResult = "Unknown"
        }
    }

    /**
     * Writes the summary information into a html.
     */
    void createSummaryHtml() {
        processEndOfPipeline()

        steps.echo "Found ${skippedSteps.size()} skipped steps"
        String stepsAndActions = toString()

        steps.dir('pipelineSummary') {
            steps.writeFile encoding: 'UTF-8', file: 'index.html', text: """
            <!DOCTYPE html>
            <html lang="en">
                <body>
                    <p>
                        <h1>Summary</h1>
                        <ul>
                            <li>Result: ${result}</li>
                            <li>Previous Result: ${previousResult}</li>
                            <li>Build: #${steps.currentBuild.number}</li>
                            <li>Scheduled at: ${start}</li>
                            <li>Duration: ${duration} seconds</li>
                            <li>Description: ${steps.currentBuild.description}</li>
                        </ul>
                    </p>
                    <p>
                        ${stepsAndActions}
                    </p>
                </body>
            </html>
        """
        }
    }
}
