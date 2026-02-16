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
            if (envelope.testCaseFinished) {
                this.onTestCaseFinished(envelope.testCaseFinished);
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

    private getPreviousTestCase(): messages.TestCaseStarted | undefined {
        const allStarted = this.query.findAllTestCaseStarted();
        return allStarted.length > 1 ? allStarted[allStarted.length - 2] : undefined;
    }

    private getNextUnfinishedTestCase(currentEvent: messages.TestCaseFinished): messages.TestCaseStarted | undefined {
        const allStarted = this.query.findAllTestCaseStarted();
        const allFinished = this.query.findAllTestCaseFinished();
        const finishedIds = new Set(allFinished.map(f => f.testCaseStartedId));

        // Find the first test case that hasn't finished yet (excluding the current one)
        return allStarted.find(s => s.id !== currentEvent.testCaseStartedId && !finishedIds.has(s.id));
    }

    private onTestRunStarted() {
        log(TeamcityMessage.new("enteredTheMatrix"));
    }

    private onTestRunFinished() {
        // Close remaining open suites based on the last test case
        const allStarted = this.query.findAllTestCaseStarted();
        const lastTestCase = allStarted[allStarted.length - 1];
        if (!lastTestCase) return;

        const lineage = this.getLineageForTestCase(lastTestCase);
        if (!lineage?.feature || !lineage?.scenario) return;

        // Close example suite if it was a scenario outline
        if (lineage.exampleIndex !== undefined) {
            this.logExampleSuiteFinished(lineage.exampleIndex);
        }

        // Close scenario suite
        this.logScenarioSuiteFinished(lineage);

        // Close feature suite
        this.logFeatureSuiteFinished(lineage);
    }

    private onTestCaseStarted(event: messages.TestCaseStarted) {
        const lineage = this.getLineageForTestCase(event);
        if (!lineage?.feature || !lineage?.scenario) return;

        const pickle = this.query.findPickleBy(event);
        if (!pickle?.uri) return;

        const prevTestCase = this.getPreviousTestCase();
        const prevLineage = prevTestCase ? this.getLineageForTestCase(prevTestCase) : undefined;

        // Check if feature changed
        const featureChanged = !prevLineage || prevLineage.gherkinDocument?.uri !== lineage.gherkinDocument?.uri;

        // Check if scenario changed
        const scenarioChanged = !prevLineage || prevLineage.scenario?.id !== lineage.scenario.id;

        // Close previous suites if needed
        if (featureChanged && prevLineage) {
            if (prevLineage.exampleIndex !== undefined) {
                this.logExampleSuiteFinished(prevLineage.exampleIndex);
            }
            if (prevLineage.scenario) {
                this.logScenarioSuiteFinished(prevLineage);
            }
            if (prevLineage.feature) {
                this.logFeatureSuiteFinished(prevLineage);
            }
        } else if (scenarioChanged && prevLineage) {
            if (prevLineage.exampleIndex !== undefined) {
                this.logExampleSuiteFinished(prevLineage.exampleIndex);
            }
            if (prevLineage.scenario) {
                this.logScenarioSuiteFinished(prevLineage);
            }
        } else if (prevLineage?.exampleIndex !== undefined) {
            // Same scenario but different example
            this.logExampleSuiteFinished(prevLineage.exampleIndex);
        }

        // Start new suites
        if (featureChanged) {
            this.logFeatureSuiteStarted(lineage, pickle.uri);
        }

        if (scenarioChanged) {
            this.logScenarioSuiteStarted(lineage, pickle.uri);
        }

        // Start example suite for scenario outlines
        if (lineage.exampleIndex !== undefined) {
            this.logExampleSuiteStarted(lineage, pickle);
        }
    }

    private onTestCaseFinished(event: messages.TestCaseFinished) {
        // Example suites are closed when the next test case starts or at test run end
        // No action needed here since we handle transitions in onTestCaseStarted
    }

    private logFeatureSuiteStarted(lineage: NonNullable<ReturnType<Query['findLineageBy']>>, uri: string) {
        if (!lineage.feature) return;
        const absolutePath = path.resolve(this.workingDirectory, uri);
        log(
            TeamcityMessage.new("testSuiteStarted")
                .addAttribute("name", `Feature: ${lineage.feature.name}`)
                .addAttribute("locationHint", `file://${absolutePath}:${lineage.feature.location.line}`)
        );
    }

    private logFeatureSuiteFinished(lineage: NonNullable<ReturnType<Query['findLineageBy']>>) {
        if (!lineage.feature) return;
        log(
            TeamcityMessage.new("testSuiteFinished")
                .addAttribute("name", `Feature: ${lineage.feature.name}`)
        );
    }

    private logScenarioSuiteStarted(lineage: NonNullable<ReturnType<Query['findLineageBy']>>, uri: string) {
        if (!lineage.scenario) return;
        const absolutePath = path.resolve(this.workingDirectory, uri);
        const isOutline = lineage.exampleIndex !== undefined;
        const prefix = isOutline ? "Scenario Outline" : "Scenario";
        log(
            TeamcityMessage.new("testSuiteStarted")
                .addAttribute("name", `${prefix}: ${lineage.scenario.name}`)
                .addAttribute("locationHint", `file://${absolutePath}:${lineage.scenario.location.line}`)
        );
    }

    private logScenarioSuiteFinished(lineage: NonNullable<ReturnType<Query['findLineageBy']>>) {
        if (!lineage.scenario) return;
        const isOutline = lineage.exampleIndex !== undefined;
        const prefix = isOutline ? "Scenario Outline" : "Scenario";
        log(
            TeamcityMessage.new("testSuiteFinished")
                .addAttribute("name", `${prefix}: ${lineage.scenario.name}`)
        );
    }

    private logExampleSuiteStarted(lineage: NonNullable<ReturnType<Query['findLineageBy']>>, pickle: messages.Pickle) {
        if (lineage.exampleIndex === undefined || !lineage.scenario) return;
        const location = this.query.findLocationOf(pickle);
        const uri = pickle.uri ?? '';
        const absolutePath = path.resolve(this.workingDirectory, uri);
        const line = location?.line ?? lineage.scenario.location.line;
        const displayIndex = lineage.exampleIndex + 1; // 0-based to 1-based

        log(
            TeamcityMessage.new("testSuiteStarted")
                .addAttribute("name", `Example #${displayIndex}`)
                .addAttribute("locationHint", `file://${absolutePath}:${line}`)
        );
    }

    private logExampleSuiteFinished(exampleIndex: number) {
        const displayIndex = exampleIndex + 1; // 0-based to 1-based
        log(
            TeamcityMessage.new("testSuiteFinished")
                .addAttribute("name", `Example #${displayIndex}`)
        );
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
