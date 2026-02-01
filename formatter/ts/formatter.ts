import {Formatter, type IFormatterOptions} from "@cucumber/cucumber";
import * as messages from '@cucumber/messages'
import {TestStepResultStatus} from '@cucumber/messages'
import {TeamcityMessage} from "./teamcityMessage.js";
import * as path from 'path';
import IEnvelope = messages.Envelope;

interface SourceLocation {
    uri: string;
    line: number;
}

export default class CustomFormatter extends Formatter {
    // Maps pickleId -> source location
    private pickleLocations: Map<string, SourceLocation> = new Map();
    // Maps testCaseId -> pickleId
    private testCaseToPickle: Map<string, string> = new Map();
    // Maps testCaseStartedId -> testCaseId
    private testCaseStartedToTestCase: Map<string, string> = new Map();
    // Maps astNodeId -> line number (from gherkinDocument)
    private astNodeLines: Map<string, number> = new Map();
    // Working directory for resolving absolute paths
    private workingDirectory: string;

    constructor(options: IFormatterOptions) {
        super(options);
        this.workingDirectory = process.cwd();

        options.eventBroadcaster.on('envelope', (envelope: IEnvelope) => {
            if (envelope.gherkinDocument) {
                this.onGherkinDocument(envelope.gherkinDocument);
            }
            if (envelope.pickle) {
                this.onPickle(envelope.pickle);
            }
            if (envelope.testCase) {
                this.onTestCase(envelope.testCase);
            }
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

    private onGherkinDocument(doc: messages.GherkinDocument) {
        // Extract line numbers for all AST nodes (scenarios, examples, etc.)
        if (!doc.feature) return;

        for (const child of doc.feature.children) {
            if (child.scenario) {
                const scenario = child.scenario;
                if (scenario.id && scenario.location) {
                    this.astNodeLines.set(scenario.id, scenario.location.line);
                }
                // Also capture example table rows for scenario outlines
                if (scenario.examples) {
                    for (const examples of scenario.examples) {
                        if (examples.tableBody) {
                            for (const row of examples.tableBody) {
                                if (row.id && row.location) {
                                    this.astNodeLines.set(row.id, row.location.line);
                                }
                            }
                        }
                    }
                }
            }
            // Handle rule children (scenarios inside rules)
            if (child.rule) {
                for (const ruleChild of child.rule.children) {
                    if (ruleChild.scenario) {
                        const scenario = ruleChild.scenario;
                        if (scenario.id && scenario.location) {
                            this.astNodeLines.set(scenario.id, scenario.location.line);
                        }
                        if (scenario.examples) {
                            for (const examples of scenario.examples) {
                                if (examples.tableBody) {
                                    for (const row of examples.tableBody) {
                                        if (row.id && row.location) {
                                            this.astNodeLines.set(row.id, row.location.line);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private onPickle(pickle: messages.Pickle) {
        // Store pickle location
        // For scenario outlines with examples, use the last astNodeId (the example row)
        // For regular scenarios, use the first (and only) astNodeId
        if (pickle.id && pickle.uri) {
            const astNodeId = pickle.astNodeIds.length > 0
                ? pickle.astNodeIds[pickle.astNodeIds.length - 1]
                : undefined;

            const line = astNodeId ? this.astNodeLines.get(astNodeId) : undefined;

            this.pickleLocations.set(pickle.id, {
                uri: pickle.uri,
                line: line ?? 1
            });
        }
    }

    private onTestCase(testCase: messages.TestCase) {
        // Map testCaseId -> pickleId
        if (testCase.id && testCase.pickleId) {
            this.testCaseToPickle.set(testCase.id, testCase.pickleId);
        }
    }

    private onTestCaseStarted(event: messages.TestCaseStarted) {
        // Map testCaseStartedId -> testCaseId for later use in step events
        if (event.id && event.testCaseId) {
            this.testCaseStartedToTestCase.set(event.id, event.testCaseId);
        }

        const location = this.getTestCaseLocation(event.testCaseId);
        const message = TeamcityMessage.new("testSuiteStarted")
            .addAttribute("name", event.testCaseId);

        if (location) {
            const absolutePath = path.resolve(this.workingDirectory, location.uri);
            message.addAttribute("locationHint", `file://${absolutePath}:${location.line}`);
        }

        log(message);
    }

    private onTestCaseFinished(event: messages.TestCaseFinished) {
        log(
            TeamcityMessage.new("testSuiteFinished")
                .addAttribute("name", event.testCaseId)
        );
    }

    private getTestCaseLocation(testCaseId: string): SourceLocation | undefined {
        const pickleId = this.testCaseToPickle.get(testCaseId);
        if (!pickleId) return undefined;
        return this.pickleLocations.get(pickleId);
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

