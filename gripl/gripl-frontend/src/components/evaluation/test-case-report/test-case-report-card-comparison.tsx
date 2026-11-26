import {ListChecks, Target} from "lucide-react";
import {Card} from "@/components/ui/card";
import {TestCaseReport} from "@/models/dto/ReportData";

interface TestCaseReportCardComparisonProps {
    report: TestCaseReport
}

// "Name (id)" -> id
function idOf(item: string): string {
    return item.slice(item.lastIndexOf("(") + 1, -1);
}

// Color of a detected element. BPMN: correct if its id is a correctly detected element. Text: the detected items are
// extracted activities (activity-1, ...), while correctActivityIds holds label ids, so the status is taken from the
// prediction itself: false positive, inside a neutral label (not scored), or matched to a critical label.
function detectedClass(report: TestCaseReport, item: string): string {
    const id = idOf(item);
    if (report.textResult) {
        if (report.falsePositiveIds.includes(id)) return "text-destructive";
        const prediction = report.textResult.predictions.find(p => p.id === id);
        if (prediction?.inNeutralLabel) return "text-muted-foreground";
        return prediction?.matchedLabelId ? "text-chart-success" : "text-destructive";
    }
    return report.correctActivityIds?.some(correctId => item.includes(`(${correctId})`)) ? "text-chart-success" : "text-destructive";
}

export default function TestCaseReportCardComparison({ report }: TestCaseReportCardComparisonProps) {

    return <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div>
            <h3 className="font-semibold text-sm mb-3 flex items-center gap-2">
                <Target className="h-4 w-4" />
                Expected BPMN Elements
            </h3>
            <Card className="p-3">
                <div className="space-y-1">
                    {report.expectedNamesWithIds.map((item, index) => {
                        const isCorrect = report.correctActivityIds?.some(id => item.includes(`(${id})`))
                        return <div key={index} className={`text-sm ${isCorrect ? 'text-chart-success' : 'text-warning'}`}>
                            • {item}
                        </div>
                    })}
                </div>
            </Card>
        </div>

        <div>
            <h3 className="font-semibold text-sm mb-3 flex items-center gap-2">
                <ListChecks className="h-4 w-4" />
                Detected BPMN Elements
            </h3>
            <Card className="p-3">
                <div className="space-y-1">
                    {report.actualNamesWithIds.map((item, index) => {
                        const className = detectedClass(report, item);
                        return <div key={index} className={`text-sm ${className}`}>
                            • {item}{className === "text-muted-foreground" && " – neutral, not scored"}
                        </div>
                    })}
                </div>
            </Card>
        </div>
    </div>
}
