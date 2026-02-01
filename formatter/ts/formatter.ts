import {Formatter, type IFormatterOptions} from "@cucumber/cucumber";
import * as messages from '@cucumber/messages'
import {TestStepResultStatus} from '@cucumber/messages'
import {TeamcityMessage} from "./teamcityMessage.js";
import * as path from 'path';
import IEnvelope = messages.Envelope;
import {Query} from "@cucumber/query";

interface SourceLocation {
    uri: string;
    line: number;
}

export default class CustomFormatter extends Formatter {
    private workingDirectory: string;

    private query = new Query();

    constructor(options: IFormatterOptions) {
        super(options);
        this.workingDirectory = process.cwd();

        options.eventBroadcaster.on('envelope', (envelope: IEnvelope) => {
            this.query.update(envelope);
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
            if (envelope.testCaseFinished) {
                this.onTestCaseFinished(envelope.testCaseFinished)
            }
        })
    }

    private onTestCaseStarted(event: messages.TestCaseStarted) {
        const location = this.getTestCaseLocation(event);
        const message = TeamcityMessage.new("testSuiteStarted")
            .addAttribute("name", event.testCaseId);

        if (location) {
            const absolutePath = path.resolve(this.workingDirectory, location.uri);
            message.addAttribute("locationHint", `file://${absolutePath}:${location.line}`);
        }

        log(message);
    }

    private onTestCaseFinished(event: messages.TestCaseFinished) {
        const testCaseId = this.query.findTestCaseBy(event)?.id ?? "";
        log(
            TeamcityMessage.new("testSuiteFinished")
                .addAttribute("name", testCaseId)
        );
    }

    private getTestCaseLocation(event: messages.TestCaseStarted): SourceLocation | undefined {
        const pickle = this.query.findPickleBy(event)
        if(pickle === undefined) {
            return undefined;
        }
        const location = this.query.findLocationOf(pickle);
        if(location === undefined) {
            return undefined;
        }
        return {
            line: location.line,
            uri: pickle.uri
        };
    }

    private onTestRunStarted(event: messages.TestRunStarted) {
        log(TeamcityMessage.new("enteredTheMatrix"))
    }

    private onTestStepStarted(event: messages.TestStepStarted) {
        log(TeamcityMessage.new("testStarted")
            .addAttribute("name", event.testStepId)
            // .addAttribute("nodeId", event.testStepId)
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
                        // .addAttribute("nodeId", event.testStepId)
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
                // .addAttribute("nodeId", event.testStepId)
        )
    }


}

function log(message: TeamcityMessage) {
    console.log(message.toString())
}

