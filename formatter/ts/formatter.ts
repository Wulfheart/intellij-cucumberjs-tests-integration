import {Formatter, type IFormatterOptions} from "@cucumber/cucumber";
import * as messages from '@cucumber/messages'
import {TestStepResultStatus} from '@cucumber/messages'
import {TeamcityMessage} from "./teamcityMessage.js";
import IEnvelope = messages.Envelope;

export default class CustomFormatter extends Formatter {
    private init(options: IFormatterOptions) {

        // Get event data collector
        this.eventDataCollector = (options as any).eventDataCollector;
    }

    constructor(options: IFormatterOptions) {


        options.eventBroadcaster.on('envelope', (envelope: IEnvelope) => {
            // console.log(Object.keys(envelope))
            if (envelope.testCaseStarted) {
                this.onTestCaseStarted(envelope.testCaseStarted);
            }
            if (envelope.testRunStarted) {
                this.onTestRunStarted(envelope.testRunStarted)
            }
            if (envelope.testStepStarted) {
                this.onTestStepStarted(envelope.testStepStarted)
            }
            if (envelope.testStepFinished) {
                this.onTestStepFinished(envelope.testStepFinished)
            }
        })
        super(options);
    }

    private onTestCaseStarted(event: messages.TestCaseStarted) {
        log(
            TeamcityMessage.new("testSuiteStarted")
                .addAttribute("name", event.testCaseId)
        )
    }

    private onTestRunStarted(event: messages.TestRunStarted) {
        log(TeamcityMessage.new("enteredTheMatrix"))
    }

    private onTestStepStarted(event: messages.TestStepStarted) {
        log(TeamcityMessage.new("testStarted")
            .addAttribute("name", event.testStepId)
            .addAttribute("nodeId", event.testStepId)
            .addAttribute("captureStandardOutput", "true")
        )
    }

    private onTestStepFinished(event: messages.TestStepFinished) {
        console.log(event.testStepResult.status)
        switch (event.testStepResult.status) {
            case TestStepResultStatus.UNDEFINED:
                // console.log(`##teamcity[testFailed name = 'Step: ${common.escape(stepName)}' timestamp='${common.getCurrentDate()}' message='' ]`)
                break
            case TestStepResultStatus.AMBIGUOUS:
            case TestStepResultStatus.FAILED:
                log(
                    TeamcityMessage.new("testFailed")
                        .addAttribute("name", event.testStepId)
                        .addAttribute("nodeId", event.testStepId)
                        .addAttribute("message", "")
                )
                // Todo: Message
                break
            case TestStepResultStatus.SKIPPED:
                // console.log(`##teamcity[testIgnored name = 'Step: ${common.escape(stepName)}' timestamp='${common.getCurrentDate()}']`)
                break
        }

        log(
            TeamcityMessage.new("testFinished")
                .addAttribute("name", event.testStepId)
                .addAttribute("nodeId", event.testStepId)
        )
    }


}

function log(message: TeamcityMessage) {
    console.log(message.toString())
}

