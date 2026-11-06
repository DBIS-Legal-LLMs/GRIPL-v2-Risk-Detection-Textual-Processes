import {Card, CardContent, CardDescription, CardHeader, CardTitle} from "@/components/ui/card";
import {Progress} from "@/components/ui/progress";
import type {TextSummaryMetrics} from "@/models/dto/ReportData";

interface TextMetricsCardProps {
    metrics: TextSummaryMetrics;
}

const formatDecimal = (value: number) => value.toFixed(3);

// Text-specific metrics of one text run: extraction quality, quote faithfulness and the result with strict matching
export default function TextMetricsCard({metrics}: TextMetricsCardProps) {
    const tiles = [
        {label: "Extraction Precision", value: metrics.extractionPrecision},
        {label: "Extraction Recall", value: metrics.extractionRecall},
        {label: "Extraction F1", value: metrics.extractionF1},
        {label: "Localized Quotes", value: metrics.localizationRate},
    ];

    return <Card>
        <CardHeader>
            <CardTitle>Text Scoring</CardTitle>
            <CardDescription>
                {metrics.evaluatedTestCases} test case(s), {metrics.labelCount} labeled activities
                {metrics.ignoredLabelCount > 0 && `, ${metrics.ignoredLabelCount} orphaned label(s) ignored`}.
                The main metrics above use partial matching.
            </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
            <div className="grid grid-cols-2 gap-6 md:grid-cols-4">
                {tiles.map((tile) => (
                    <div key={tile.label} className="text-center">
                        <div className="text-3xl font-bold">{formatDecimal(tile.value)}</div>
                        <div className="text-sm text-muted-foreground">{tile.label}</div>
                        <Progress value={tile.value * 100} className="mt-2 h-2"/>
                    </div>
                ))}
            </div>
            <div className="grid grid-cols-1 gap-4 text-sm md:grid-cols-3">
                <div className="rounded-md bg-muted/50 p-3">
                    <div className="text-muted-foreground">Mean IoU of matches</div>
                    <div className="text-lg font-semibold">{formatDecimal(metrics.meanIou)}</div>
                </div>
                <div className="rounded-md bg-muted/50 p-3">
                    <div className="text-muted-foreground">False negatives by extraction / classification</div>
                    <div className="text-lg font-semibold">
                        {metrics.falseNegativesByExtraction} / {metrics.falseNegativesByClassification}
                    </div>
                </div>
                <div className="rounded-md bg-muted/50 p-3">
                    <div className="text-muted-foreground">Strict matching (IoU ≥ 0.5): P / R / F1</div>
                    <div className="text-lg font-semibold">
                        {formatDecimal(metrics.strict.precision)} / {formatDecimal(metrics.strict.recall)} / {formatDecimal(metrics.strict.f1Score)}
                    </div>
                </div>
            </div>
        </CardContent>
    </Card>
}
