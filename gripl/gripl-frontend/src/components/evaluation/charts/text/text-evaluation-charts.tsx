"use client"

import {useState} from "react";
import {Button} from "@/components/ui/button";
import TextBarChart from "@/components/evaluation/charts/text/text-bar-chart";
import {textEvaluationColors} from "@/components/evaluation/charts/common/palettes";
import TextMetricsTable from "@/components/evaluation/text/text-metrics-table";
import type {AggregatedEvaluationResults, AggregatedTextResult} from "@/models/evaluation/AggregatedEvaluationResult";
import {EVALUATION_MODALITIES, EvaluationModality, MODALITY_LABELS} from "@/models/dto/MultiEvaluationRequest";

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

const MODALITY_SERIES: Array<{ modality: EvaluationModality; name: string; color: string }> = [
    {modality: "BPMN", name: "BPMN", color: textEvaluationColors.bpmn},
    {modality: "TEXT", name: "Text", color: textEvaluationColors.text},
    {modality: "BPMN_WITH_TEXT", name: "BPMN + Text", color: textEvaluationColors.bpmnWithText},
    {modality: "TEXT_WITH_BPMN", name: "Text + BPMN", color: textEvaluationColors.textWithBpmn},
];

const MODALITY_BY_LABEL = new Map(EVALUATION_MODALITIES.map((m) => [MODALITY_LABELS[m], m]));
const MODALITY_SUFFIX = /^(.*) \[([A-Z+]+)]$/;

// The core comparison of the thesis: the same model on the BPMN model, the text and their combinations of the same
// processes. Renders nothing unless the models were evaluated with at least two modalities.
export function ModalityComparisonChart({stats}: { stats: AggregatedEvaluationResults }) {
    const [metric, setMetric] = useState<ComparisonMetric>("F1");

    // Group the run labels "<model> [BPMN]", "<model> [TEXT+BPMN]", ... by model
    const byModel = new Map<string, Partial<Record<EvaluationModality, string>>>();
    for (const label of Object.keys(stats)) {
        const match = MODALITY_SUFFIX.exec(label);
        const modality = match && MODALITY_BY_LABEL.get(match[2]);
        if (!match || !modality) continue;
        byModel.set(match[1], {...byModel.get(match[1]), [modality]: label});
    }
    const series = MODALITY_SERIES.filter((s) => [...byModel.values()].some((e) => e[s.modality]));
    const models = [...byModel.entries()].filter(([, e]) => series.every((s) => e[s.modality]));
    if (series.length < 2 || models.length === 0) return null;

    const keys = METRIC_KEYS[metric];
    const metricName = metric === "F1" ? "F1-Score" : metric;

    return <TextBarChart
        chartId="modality-comparison-chart"
        title="Modalities per Model"
        description={`${metricName} per model and modality (mean ± SD). BPMN and BPMN + Text are scored on the ` +
            "labeled BPMN elements, Text and Text + BPMN on the labeled text passages."}
        categories={models.map(([model]) => model)}
        yTitle={metricName}
        yMax={1}
        series={series.map((s) => ({
            name: s.name,
            color: s.color,
            mean: models.map(([, e]) => stats[e[s.modality]!][keys.mean]),
            sd: models.map(([, e]) => stats[e[s.modality]!][keys.sd]),
        }))}
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
        <TextMetricsTable textResults={textResults}/>
        <div className="grid grid-cols-1 gap-8 lg:grid-cols-2">
            <PartialVsStrictChart textResults={textResults}/>
            <ExtractionQualityChart textResults={textResults}/>
            <FalseNegativeCausesChart textResults={textResults}/>
        </div>
    </div>
}
