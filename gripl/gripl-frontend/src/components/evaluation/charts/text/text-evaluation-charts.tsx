"use client"

import {useState} from "react";
import {Button} from "@/components/ui/button";
import TextBarChart from "@/components/evaluation/charts/text/text-bar-chart";
import {textEvaluationColors} from "@/components/evaluation/charts/common/palettes";
import TextMetricsTable from "@/components/evaluation/text/text-metrics-table";
import type {AggregatedEvaluationResults, AggregatedTextResult} from "@/models/evaluation/AggregatedEvaluationResult";

type TextResults = Array<{ label: string; text: AggregatedTextResult; f1: number; sdF1: number }>;

// Which pipeline step causes the missed critical activities: extraction (not found) or classification
export function FalseNegativeCausesChart({textResults}: { textResults: TextResults }) {
    return <TextBarChart
        chartId="text-fn-causes-chart"
        title="False Negatives by Cause"
        description="Missed critical activities: not extracted vs. extracted but classified non-critical (mean per run)"
        categories={textResults.map((r) => r.label)}
        stacked
        yTitle="False negatives"
        decimals={1}
        series={[
            {
                name: "Not extracted",
                color: textEvaluationColors.fnExtraction,
                mean: textResults.map((r) => r.text.avgFalseNegativesByExtraction),
                sd: textResults.map((r) => r.text.stdFalseNegativesByExtraction),
            },
            {
                name: "Classified non-critical",
                color: textEvaluationColors.fnClassification,
                mean: textResults.map((r) => r.text.avgFalseNegativesByClassification),
                sd: textResults.map((r) => r.text.stdFalseNegativesByClassification),
            },
        ]}
    />
}

// Quality of step 1 of the text pipeline: are the labeled activities found, are the quotes faithful?
export function ExtractionQualityChart({textResults}: { textResults: TextResults }) {
    return <TextBarChart
        chartId="text-extraction-quality-chart"
        title="Extraction Quality"
        description="Extraction precision and recall of the activities, and share of quotes found in the text (mean ± SD)"
        categories={textResults.map((r) => r.label)}
        yTitle="Rate"
        yMax={1}
        series={[
            {
                name: "Extraction Precision",
                color: textEvaluationColors.extractionPrecision,
                mean: textResults.map((r) => r.text.avgExtractionPrecision),
                sd: textResults.map((r) => r.text.stdExtractionPrecision),
            },
            {
                name: "Extraction Recall",
                color: textEvaluationColors.extractionRecall,
                mean: textResults.map((r) => r.text.avgExtractionRecall),
                sd: textResults.map((r) => r.text.stdExtractionRecall),
            },
            {
                name: "Localized Quotes",
                color: textEvaluationColors.localization,
                mean: textResults.map((r) => r.text.avgLocalizationRate),
                sd: textResults.map((r) => r.text.stdLocalizationRate),
            },
        ]}
    />
}

// Sensitivity of the results to the matching criterion
export function PartialVsStrictChart({textResults}: { textResults: TextResults }) {
    return <TextBarChart
        chartId="text-partial-strict-chart"
        title="Partial vs. Strict Matching"
        description="F1-Score with partial matching (any overlap) and strict matching (IoU ≥ 0.5), mean ± SD"
        categories={textResults.map((r) => r.label)}
        yTitle="F1-Score"
        yMax={1}
        series={[
            {name: "Partial", color: textEvaluationColors.partial, mean: textResults.map((r) => r.f1), sd: textResults.map((r) => r.sdF1)},
            {
                name: "Strict (IoU ≥ 0.5)",
                color: textEvaluationColors.strict,
                mean: textResults.map((r) => r.text.avgStrictF1Score),
                sd: textResults.map((r) => r.text.stdStrictF1Score),
            },
        ]}
    />
}

type ComparisonMetric = "F1" | "Precision" | "Recall";

const METRIC_KEYS: Record<ComparisonMetric, { mean: "avgF1Score" | "avgPrecision" | "avgRecall"; sd: "stdF1Score" | "stdPrecision" | "stdRecall" }> = {
    F1: {mean: "avgF1Score", sd: "stdF1Score"},
    Precision: {mean: "avgPrecision", sd: "stdPrecision"},
    Recall: {mean: "avgRecall", sd: "stdRecall"},
};

const MODALITY_SUFFIX = /^(.*) \[(BPMN|TEXT)]$/;

// The core comparison of the thesis: the same model on the BPMN model and on the text of the same processes
export function ModalityComparisonChart({stats}: { stats: AggregatedEvaluationResults }) {
    const [metric, setMetric] = useState<ComparisonMetric>("F1");

    // Group the run labels "<model> [BPMN]" / "<model> [TEXT]" by model
    const byModel = new Map<string, { BPMN?: string; TEXT?: string }>();
    for (const label of Object.keys(stats)) {
        const match = MODALITY_SUFFIX.exec(label);
        if (!match) continue;
        const entry = byModel.get(match[1]) ?? {};
        entry[match[2] as "BPMN" | "TEXT"] = label;
        byModel.set(match[1], entry);
    }
    const models = [...byModel.entries()].filter(([, e]) => e.BPMN && e.TEXT);
    if (models.length === 0) return null;

    const keys = METRIC_KEYS[metric];
    const valuesOf = (modality: "BPMN" | "TEXT") => ({
        mean: models.map(([, e]) => stats[e[modality]!][keys.mean]),
        sd: models.map(([, e]) => stats[e[modality]!][keys.sd]),
    });

    return <TextBarChart
        chartId="modality-comparison-chart"
        title="BPMN vs. Text"
        description={`${metric === "F1" ? "F1-Score" : metric} per model on the BPMN model and on the text description (mean ± SD)`}
        categories={models.map(([model]) => model)}
        yTitle={metric === "F1" ? "F1-Score" : metric}
        yMax={1}
        series={[
            {name: "BPMN", color: textEvaluationColors.bpmn, ...valuesOf("BPMN")},
            {name: "Text", color: textEvaluationColors.text, ...valuesOf("TEXT")},
        ]}
        headerExtra={<div className="flex gap-1">
            {(Object.keys(METRIC_KEYS) as ComparisonMetric[]).map((m) => (
                <Button key={m} size="sm" variant={m === metric ? "default" : "outline"} onClick={() => setMetric(m)}>{m}</Button>
            ))}
        </div>}
    />
}

// All text evaluation views of the aggregated results; renders nothing for BPMN-only runs
export default function TextEvaluationSection({stats}: { stats: AggregatedEvaluationResults }) {
    const textResults: TextResults = Object.entries(stats)
        .filter(([, s]) => s.text)
        .map(([label, s]) => ({label, text: s.text!, f1: s.avgF1Score, sdF1: s.stdF1Score}));
    if (textResults.length === 0) return null;

    return <div className="space-y-6">
        <h2 className="text-2xl font-semibold">Text Evaluation</h2>
        <div className="grid grid-cols-1 gap-8 lg:grid-cols-2">
            <ModalityComparisonChart stats={stats}/>
            <PartialVsStrictChart textResults={textResults}/>
        </div>
        <TextMetricsTable textResults={textResults}/>
        <div className="grid grid-cols-1 gap-8 lg:grid-cols-2">
            <ExtractionQualityChart textResults={textResults}/>
            <FalseNegativeCausesChart textResults={textResults}/>
        </div>
    </div>
}
