import {Card, CardContent, CardDescription, CardHeader, CardTitle} from "@/components/ui/card";
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from "@/components/ui/table";
import type {AggregatedTextResult} from "@/models/evaluation/AggregatedEvaluationResult";

interface TextMetricsTableProps {
    // text run labels with their aggregated text metrics
    textResults: Array<{ label: string; text: AggregatedTextResult }>;
}

const meanSd = (mean: number, sd: number, digits = 3) => `${mean.toFixed(digits)} ± ${sd.toFixed(digits)}`;

// Text-specific metrics of every text run label, mean ± SD across the repetitions
export default function TextMetricsTable({textResults}: TextMetricsTableProps) {
    return <Card>
        <CardHeader>
            <CardTitle>Text Scoring Metrics</CardTitle>
            <CardDescription>
                Extraction quality and results with strict matching (IoU ≥ 0.5), mean ± SD across runs
            </CardDescription>
        </CardHeader>
        <CardContent className="overflow-x-auto">
            <Table>
                <TableHeader>
                    <TableRow>
                        <TableHead>Model</TableHead>
                        <TableHead className="whitespace-nowrap text-right">Extraction P</TableHead>
                        <TableHead className="whitespace-nowrap text-right">Extraction R</TableHead>
                        <TableHead className="whitespace-nowrap text-right">Extraction F1</TableHead>
                        <TableHead className="whitespace-nowrap text-right">Localized</TableHead>
                        <TableHead className="whitespace-nowrap text-right">Mean IoU</TableHead>
                        <TableHead className="whitespace-nowrap text-right">FN Extraction</TableHead>
                        <TableHead className="whitespace-nowrap text-right">FN Classification</TableHead>
                        <TableHead className="whitespace-nowrap text-right">Strict P</TableHead>
                        <TableHead className="whitespace-nowrap text-right">Strict R</TableHead>
                        <TableHead className="whitespace-nowrap text-right">Strict F1</TableHead>
                    </TableRow>
                </TableHeader>
                <TableBody>
                    {textResults.map(({label, text}) => (
                        <TableRow key={label}>
                            <TableCell className="whitespace-nowrap font-medium">{label}</TableCell>
                            <TableCell className="whitespace-nowrap text-right">{meanSd(text.avgExtractionPrecision, text.stdExtractionPrecision)}</TableCell>
                            <TableCell className="whitespace-nowrap text-right">{meanSd(text.avgExtractionRecall, text.stdExtractionRecall)}</TableCell>
                            <TableCell className="whitespace-nowrap text-right">{meanSd(text.avgExtractionF1, text.stdExtractionF1)}</TableCell>
                            <TableCell className="whitespace-nowrap text-right">{meanSd(text.avgLocalizationRate, text.stdLocalizationRate)}</TableCell>
                            <TableCell className="whitespace-nowrap text-right">{meanSd(text.avgMeanIou, text.stdMeanIou)}</TableCell>
                            <TableCell className="whitespace-nowrap text-right">{meanSd(text.avgFalseNegativesByExtraction, text.stdFalseNegativesByExtraction, 1)}</TableCell>
                            <TableCell className="whitespace-nowrap text-right">{meanSd(text.avgFalseNegativesByClassification, text.stdFalseNegativesByClassification, 1)}</TableCell>
                            <TableCell className="whitespace-nowrap text-right">{meanSd(text.avgStrictPrecision, text.stdStrictPrecision)}</TableCell>
                            <TableCell className="whitespace-nowrap text-right">{meanSd(text.avgStrictRecall, text.stdStrictRecall)}</TableCell>
                            <TableCell className="whitespace-nowrap text-right">{meanSd(text.avgStrictF1Score, text.stdStrictF1Score)}</TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </CardContent>
    </Card>
}
