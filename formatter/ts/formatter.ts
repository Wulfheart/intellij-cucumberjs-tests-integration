import {Formatter, type IFormatterOptions} from "@cucumber/cucumber";
import * as messages from '@cucumber/messages'
import {TestStepResultStatus} from '@cucumber/messages'
import {TeamcityMessage} from "./teamcityMessage.js";
import * as path from 'path';
import IEnvelope = messages.Envelope;
import {Query} from "@cucumber/query";

export default class CustomFormatter extends Formatter {
    private workingDirectory: string;
    private query = new Query();

    // Track currently open suites to ensure proper closing
    private currentFeatureName: string | null = null;
    private currentScenarioName: string | null = null;
    private currentExampleName: string | null = null;
    private currentExampleLine: number | null = null; // Used for uniqueness tracking

    constructor(options: IFormatterOptions) {
        super(options);
        this.workingDirectory = process.cwd();

        options.eventBroadcaster.on('envelope', (envelope: IEnvelope) => {
            this.query.update(envelope);

            if (envelope.testRunStarted) {
                this.onTestRunStarted();
            }
            if (envelope.testCaseStarted) {
                this.onTestCaseStarted(envelope.testCaseStarted);
            }
            if (envelope.testStepStarted) {
                this.onTestStepStarted(envelope.testStepStarted);
            }
            if (envelope.testStepFinished) {
                this.onTestStepFinished(envelope.testStepFinished);
            }
            if (envelope.testRunFinished) {
                this.onTestRunFinished();
            }
        });
    }

    private getLineageForTestCase(event: messages.TestCaseStarted | messages.TestCaseFinished) {
        const pickle = this.query.findPickleBy(event);
        if (!pickle) return undefined;
        return this.query.findLineageBy(pickle);
    }


    private onTestRunStarted() {
        log(TeamcityMessage.new("enteredTheMatrix"));
    }

    private onTestRunFinished() {
        // Close any remaining open suites using tracked names
        if (this.currentExampleName) {
            log(
                TeamcityMessage.new("testSuiteFinished")
                    .addAttribute("name", this.currentExampleName)
            );
            this.currentExampleName = null;
            this.currentExampleLine = null;
        }

        if (this.currentScenarioName) {
            log(
                TeamcityMessage.new("testSuiteFinished")
                    .addAttribute("name", this.currentScenarioName)
            );
            this.currentScenarioName = null;
        }

        if (this.currentFeatureName) {
            log(
                TeamcityMessage.new("testSuiteFinished")
                    .addAttribute("name", this.currentFeatureName)
            );
            this.currentFeatureName = null;
        }
    }

    private onTestCaseStarted(event: messages.TestCaseStarted) {
        const lineage = this.getLineageForTestCase(event);
        if (!lineage?.feature || !lineage?.scenario) return;

        const pickle = this.query.findPickleBy(event);
        if (!pickle?.uri) return;

        // Determine new suite names
        const newFeatureName = `Feature: ${lineage.feature.name}`;
        const isOutline = lineage.exampleIndex !== undefined;
        const prefix = isOutline ? "Scenario Outline" : "Scenario";
        const newScenarioName = `${prefix}: ${lineage.scenario.name}`;

        let newExampleName: string | null = null;
        let newExampleLine: number | null = null;
        if (lineage.exampleIndex !== undefined) {
            const location = this.query.findLocationOf(pickle);
            newExampleLine = location?.line ?? lineage.scenario.location.line;
            const exampleNumber = lineage.exampleIndex + 1; // 0-based to 1-based
            const examplesName = lineage.examples?.name;
            if (examplesName) {
                newExampleName = `Example #${exampleNumber}: ${examplesName}`;
            } else {
                newExampleName = `Example #${exampleNumber}`;
            }
        }

        // Close previous suites if they changed (in reverse order: example -> scenario -> feature)
        // Use line number for example comparison to handle duplicate names across Examples tables
        if (this.currentExampleName && this.currentExampleLine !== newExampleLine) {
            log(
                TeamcityMessage.new("testSuiteFinished")
                    .addAttribute("name", this.currentExampleName)
            );
            this.currentExampleName = null;
            this.currentExampleLine = null;
        }

        if (this.currentScenarioName && this.currentScenarioName !== newScenarioName) {
            // Close example first if still open
            if (this.currentExampleName) {
                log(
                    TeamcityMessage.new("testSuiteFinished")
                        .addAttribute("name", this.currentExampleName)
                );
                this.currentExampleName = null;
                this.currentExampleLine = null;
            }
            log(
                TeamcityMessage.new("testSuiteFinished")
                    .addAttribute("name", this.currentScenarioName)
            );
            this.currentScenarioName = null;
        }

        if (this.currentFeatureName && this.currentFeatureName !== newFeatureName) {
            // Close scenario first if still open
            if (this.currentScenarioName) {
                if (this.currentExampleName) {
                    log(
                        TeamcityMessage.new("testSuiteFinished")
                            .addAttribute("name", this.currentExampleName)
                    );
                    this.currentExampleName = null;
                    this.currentExampleLine = null;
                }
                log(
                    TeamcityMessage.new("testSuiteFinished")
                        .addAttribute("name", this.currentScenarioName)
                );
                this.currentScenarioName = null;
            }
            log(
                TeamcityMessage.new("testSuiteFinished")
                    .addAttribute("name", this.currentFeatureName)
            );
            this.currentFeatureName = null;
        }

        // Start new suites (in order: feature -> scenario -> example)
        if (!this.currentFeatureName) {
            const absolutePath = path.resolve(this.workingDirectory, pickle.uri);
            log(
                TeamcityMessage.new("testSuiteStarted")
                    .addAttribute("name", newFeatureName)
                    .addAttribute("locationHint", `file://${absolutePath}:${lineage.feature.location.line}`)
            );
            this.currentFeatureName = newFeatureName;
        }

        if (!this.currentScenarioName) {
            const absolutePath = path.resolve(this.workingDirectory, pickle.uri);
            log(
                TeamcityMessage.new("testSuiteStarted")
                    .addAttribute("name", newScenarioName)
                    .addAttribute("locationHint", `file://${absolutePath}:${lineage.scenario.location.line}`)
            );
            this.currentScenarioName = newScenarioName;
        }

        if (newExampleName && this.currentExampleLine !== newExampleLine) {
            const absolutePath = path.resolve(this.workingDirectory, pickle.uri);
            log(
                TeamcityMessage.new("testSuiteStarted")
                    .addAttribute("name", newExampleName)
                    .addAttribute("locationHint", `file://${absolutePath}:${newExampleLine}`)
            );
            this.currentExampleName = newExampleName;
            this.currentExampleLine = newExampleLine;
        }
    }

    private onTestStepStarted(event: messages.TestStepStarted) {
        const testStep = this.query.findTestStepBy(event);
        const stepName = this.getStepName(testStep);

        log(
            TeamcityMessage.new("testStarted")
                .addAttribute("name", stepName)
                .addAttribute("captureStandardOutput", "true")
        );
    }

    private onTestStepFinished(event: messages.TestStepFinished) {
        const testStep = this.query.findTestStepBy(event);
        const stepName = this.getStepName(testStep);

        switch (event.testStepResult.status) {
            case TestStepResultStatus.UNDEFINED:
                log(
                    TeamcityMessage.new("testFailed")
                        .addAttribute("name", stepName)
                        .addAttribute("message", "Step is undefined")
                );
                break;
            case TestStepResultStatus.AMBIGUOUS:
                log(
                    TeamcityMessage.new("testFailed")
                        .addAttribute("name", stepName)
                        .addAttribute("message", "Step is ambiguous")
                );
                break;
            case TestStepResultStatus.FAILED:
                const errorMessage = event.testStepResult.message ?? "";
                log(
                    TeamcityMessage.new("testFailed")
                        .addAttribute("name", stepName)
                        .addAttribute("message", errorMessage)
                );
                break;
            case TestStepResultStatus.SKIPPED:
                log(
                    TeamcityMessage.new("testIgnored")
                        .addAttribute("name", stepName)
                        .addAttribute("message", "Step was skipped")
                );
                break;
            case TestStepResultStatus.PENDING:
                log(
                    TeamcityMessage.new("testIgnored")
                        .addAttribute("name", stepName)
                        .addAttribute("message", "Step is pending")
                );
                break;
        }

        log(
            TeamcityMessage.new("testFinished")
                .addAttribute("name", stepName)
        );
    }

    private getStepName(testStep: messages.TestStep | undefined): string {
        if (!testStep) return "Unknown step";

        if (testStep.pickleStepId) {
            const pickleStep = this.query.findPickleStepBy(testStep);
            if (pickleStep) {
                return pickleStep.text;
            }
        }

        // Hook step
        if (testStep.hookId) {
            return "Hook";
        }

        return testStep.id;
    }
}

function log(message: TeamcityMessage) {
    console.log(message.toString());
}
