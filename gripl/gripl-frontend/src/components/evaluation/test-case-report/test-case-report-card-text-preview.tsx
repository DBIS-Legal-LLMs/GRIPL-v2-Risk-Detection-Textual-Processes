"use client"

import {ReactNode, useMemo, useState} from "react";
import {FileText} from "lucide-react";
import {Card} from "@/components/ui/card";
import {Button} from "@/components/ui/button";
import {Tooltip, TooltipContent, TooltipProvider, TooltipTrigger} from "@/components/ui/tooltip";
import {defaultColors} from "@/components/evaluation/charts/common/palettes";
import type {
    TestCaseReport,
    TextLabelOutcome,
    TextLabelResult,
    TextPredictionResult,
    TextTestCaseResult
} from "@/models/dto/ReportData";

type MatchMode = "partial" | "strict";

// Same outcome colors as the BPMN preview and the confusion matrix charts
const OUTCOME_STYLE: Record<TextLabelOutcome, { color: string; label: string }> = {
    TP: {color: defaultColors.truePositive, label: "True Positive"},
    FP: {color: defaultColors.falsePositive, label: "False Positive"},
    FN: {color: defaultColors.falseNegative, label: "False Negative"},
    TN: {color: defaultColors.trueNegative, label: "True Negative"},
    IGNORED: {color: "#9ca3af", label: "Ignored (orphaned label)"},
};

interface Segment {
    start: number;
    end: number;
    label?: TextLabelResult;
    // located predictions without a label (partial matching) covering this segment
    unmatched: TextPredictionResult[];
}

// Splits the text at every label and unmatched-prediction boundary, so each segment has one style
function segmentText(result: TextTestCaseResult, unmatched: TextPredictionResult[]): Segment[] {
    const located = unmatched.filter((p) => p.start != null && p.end != null);
    const boundaries = new Set<number>([0, result.processText.length]);
    result.labels.forEach((l) => { boundaries.add(l.start); boundaries.add(l.end); });
    located.forEach((p) => { boundaries.add(p.start!); boundaries.add(p.end!); });
    const points = [...boundaries].filter((b) => b >= 0 && b <= result.processText.length).sort((a, b) => a - b);

    const segments: Segment[] = [];
    for (let i = 0; i < points.length - 1; i++) {
        const start = points[i], end = points[i + 1];
        if (start === end) continue;
        segments.push({
            start,
            end,
            label: result.labels.find((l) => l.start <= start && end <= l.end),
            unmatched: located.filter((p) => p.start! <= start && end <= p.end!),
        });
    }
    return segments;
}

function format(value: number | null | undefined): string {
    return value == null ? "-" : value.toFixed(3);
}

interface TestCaseReportCardTextPreviewProps {
    report: TestCaseReport & { textResult: TextTestCaseResult };
}

// Text counterpart of the BPMN preview: the process text with every labeled activity colored by its outcome
export default function TestCaseReportCardTextPreview({report}: TestCaseReportCardTextPreviewProps) {
    const result = report.textResult;
    const [mode, setMode] = useState<MatchMode>("partial");

    const predictionById = useMemo(() => new Map(result.predictions.map((p) => [p.id, p])), [result]);
    const unmatched = useMemo(() => result.predictions.filter((p) => !p.matchedLabelId), [result]);
    const segments = useMemo(() => segmentText(result, unmatched), [result, unmatched]);

    const outcomeOf = (label: TextLabelResult) => (mode === "partial" ? label.outcome : label.strictOutcome);
    const counts = result.labels.reduce((acc, l) => {
        const outcome = outcomeOf(l);
        acc[outcome] = (acc[outcome] ?? 0) + 1;
        return acc;
    }, {} as Partial<Record<TextLabelOutcome, number>>);
    // FPs of predictions without a label are not part of the label outcomes
    const unmatchedCriticalCount = Math.max(0, mode === "partial"
        ? unmatched.filter((p) => p.isCritical).length
        : result.strict.falsePositives - (counts.FP ?? 0));
    // The legend shows the same FP count as the report: labels predicted critical + critical predictions without label
    const legendCounts = {...counts, FP: (counts.FP ?? 0) + unmatchedCriticalCount};

    function labelTooltip(label: TextLabelResult): ReactNode {
        const outcome = outcomeOf(label);
        const prediction = label.matchedPredictionId ? predictionById.get(label.matchedPredictionId) : undefined;
        return <div className="max-w-sm space-y-1 text-left">
            <p className="font-semibold">
                {OUTCOME_STYLE[outcome].label}
                {outcome === "FN" && label.falseNegativeCause && mode === "partial" &&
                    ` (${label.falseNegativeCause === "EXTRACTION" ? "not extracted" : "classified non-critical"})`}
            </p>
            <p>Label: {label.isCritical ? "GDPR critical" : "not critical"}</p>
            {label.reason && <p className="text-muted-foreground">Label reason: {label.reason}</p>}
            {prediction ? <>
                <p>LLM activity: {prediction.name ?? prediction.id} ({prediction.isCritical ? "critical" : "not critical"}), IoU {format(label.iou)}</p>
                <p className="italic text-muted-foreground">&ldquo;{prediction.exactText}&rdquo;</p>
                {prediction.reason && <p className="text-muted-foreground">LLM reason: {prediction.reason}</p>}
            </> : <p className="text-muted-foreground">No LLM activity matched this label.</p>}
        </div>
    }

    return <div>
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <h3 className="flex items-center gap-2 text-sm font-semibold">
                <FileText className="h-4 w-4"/>
                Text Preview
            </h3>
            <div className="flex gap-1">
                <Button size="sm" variant={mode === "partial" ? "default" : "outline"} onClick={() => setMode("partial")}>
                    Partial matching
                </Button>
                <Button size="sm" variant={mode === "strict" ? "default" : "outline"} onClick={() => setMode("strict")}>
                    Strict (IoU ≥ 0.5)
                </Button>
            </div>
        </div>

        <Card className="space-y-3 p-3">
            {/* Legend: outcome counts of the labels in the selected mode */}
            <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
                {(Object.keys(OUTCOME_STYLE) as TextLabelOutcome[])
                    .filter((o) => o !== "IGNORED" || counts.IGNORED)
                    .map((outcome) => (
                        <span key={outcome} className="flex items-center gap-1.5">
                            <span className="inline-block h-3 w-3 rounded-sm" style={{backgroundColor: OUTCOME_STYLE[outcome].color}}/>
                            {OUTCOME_STYLE[outcome].label}: {legendCounts[outcome] ?? 0}
                        </span>
                    ))}
                <span className="flex items-center gap-1.5">
                    <span className="inline-block w-4 border-b-2 border-dotted" style={{borderColor: defaultColors.falsePositive}}/>
                    LLM activity without label{mode === "strict" ? " (partial matching)" : ""}
                </span>
            </div>

            <TooltipProvider delayDuration={100}>
                <div className="max-h-[420px] overflow-y-auto whitespace-pre-wrap rounded-md border px-3 py-2 text-sm leading-loose">
                    {segments.map((segment) => {
                        const text = result.processText.slice(segment.start, segment.end);
                        const unmatchedStyle = segment.unmatched.length > 0 ? {
                            textDecorationLine: "underline",
                            textDecorationStyle: "dotted" as const,
                            textDecorationThickness: "2px",
                            textDecorationColor: segment.unmatched.some((p) => p.isCritical) ? defaultColors.falsePositive : "#9ca3af",
                        } : {};
                        if (!segment.label) {
                            return <span key={segment.start} style={unmatchedStyle}>{text}</span>;
                        }
                        const color = OUTCOME_STYLE[outcomeOf(segment.label)].color;
                        return <Tooltip key={segment.start}>
                            <TooltipTrigger asChild>
                                <mark
                                    className="cursor-help rounded-sm text-foreground"
                                    style={{backgroundColor: `${color}33`, boxShadow: `inset 0 -2px 0 ${color}`, ...unmatchedStyle}}
                                >
                                    {text}
                                </mark>
                            </TooltipTrigger>
                            <TooltipContent side="top" className="border bg-popover text-popover-foreground shadow-md">
                                {labelTooltip(segment.label)}
                            </TooltipContent>
                        </Tooltip>;
                    })}
                </div>
            </TooltipProvider>

            <div className="grid grid-cols-2 gap-2 text-xs md:grid-cols-4">
                <Metric label="Extraction P / R / F1"
                        value={`${format(result.extractionPrecision)} / ${format(result.extractionRecall)} / ${format(result.extractionF1)}`}/>
                <Metric label="Localized quotes" value={format(result.localizationRate)}/>
                <Metric label="Mean IoU" value={format(result.meanIou)}/>
                <Metric label="FN extraction / classification"
                        value={`${result.falseNegativesByExtraction} / ${result.falseNegativesByClassification}`}/>
            </div>
            {mode === "strict" && <p className="text-xs text-muted-foreground">
                Strict: TP {result.strict.truePositives}, FP {result.strict.falsePositives}, FN {result.strict.falseNegatives},
                TN {result.strict.trueNegatives} (of the FPs, {unmatchedCriticalCount} are LLM activities without a label)
            </p>}

            {unmatched.length > 0 && <div className="space-y-1">
                <p className="text-xs font-semibold">LLM activities without a label (partial matching)</p>
                <ul className="space-y-1 text-xs">
                    {unmatched.map((p) => (
                        <li key={p.id} className="flex flex-wrap gap-x-2">
                            <span className="font-medium">{p.name ?? p.id}</span>
                            <span className="text-muted-foreground">
                                {p.isCritical ? "critical → false positive" : "not critical (no effect)"},{" "}
                                {p.start == null ? "quote not found in the text" : "outside all labels"}
                            </span>
                            <span className="italic text-muted-foreground">&ldquo;{p.exactText}&rdquo;</span>
                        </li>
                    ))}
                </ul>
            </div>}
        </Card>
    </div>
}

function Metric({label, value}: { label: string; value: string }) {
    return <div className="rounded-md bg-muted/50 px-2 py-1.5">
        <div className="text-muted-foreground">{label}</div>
        <div className="font-semibold">{value}</div>
    </div>
}

export function hasTextResult(report: TestCaseReport): report is TestCaseReport & { textResult: TextTestCaseResult } {
    return report.textResult != null;
}
