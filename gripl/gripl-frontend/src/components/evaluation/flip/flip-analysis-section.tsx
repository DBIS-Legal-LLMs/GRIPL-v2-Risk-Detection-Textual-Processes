"use client"

import {useMemo, useState} from "react";
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from "@/components/ui/card";
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from "@/components/ui/table";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select";
import TextBarChart from "@/components/evaluation/charts/text/text-bar-chart";
import {flipColors} from "@/components/evaluation/charts/common/palettes";
import {computeFlipAnalysis, FLIP_KIND_INFO, FLIP_KINDS, FlipComparison, FlipKind} from "@/lib/flip-analysis";
import {MODALITY_LABELS} from "@/models/dto/MultiEvaluationRequest";
import type {TestCaseReport} from "@/models/dto/ReportData";

interface FlipAnalysisSectionProps {
    testCasesByRun: Map<number, (TestCaseReport & { modelLabel: string })[]>;
}

const mean = (values: number[]) => values.length ? values.reduce((a, b) => a + b, 0) / values.length : 0;
const sd = (values: number[]) => {
    const m = mean(values);
    return values.length ? Math.sqrt(values.reduce((sum, v) => sum + (v - m) ** 2, 0) / values.length) : 0;
};
const comparisonName = (c: FlipComparison) => `${MODALITY_LABELS[c.base]} → ${MODALITY_LABELS[c.combined]}`;
const comparisonId = (c: FlipComparison) => `${c.model}|${c.base}`;
// net effect per run: improvements minus deteriorations
const netPerRun = (c: FlipComparison) => c.runs.map((_, i) =>
    FLIP_KINDS.reduce((sum, kind) => sum + (FLIP_KIND_INFO[kind].improves ? 1 : -1) * c.counts[kind][i], 0));

// Which decisions change when the other representation is added: per model BPMN -> BPMN+TEXT (per BPMN element) and
// TEXT -> TEXT+BPMN (per text label), same run and test case. Renders nothing without such pairs.
export default function FlipAnalysisSection({testCasesByRun}: FlipAnalysisSectionProps) {
    const comparisons = useMemo(() => computeFlipAnalysis(testCasesByRun), [testCasesByRun]);
    const [selectedId, setSelectedId] = useState<string | undefined>(undefined);
    if (comparisons.length === 0) return null;

    const selected = comparisons.find((c) => comparisonId(c) === selectedId) ?? comparisons[0];
    const signed = (value: number) => `${value > 0 ? "+" : ""}${value.toFixed(1)}`;

    return <div className="space-y-6">
        <h2 className="text-2xl font-semibold">Flip Analysis</h2>

        <Card>
            <CardHeader>
                <CardTitle>Flips per Model</CardTitle>
                <CardDescription>
                    Decisions that change when the other representation is added, compared for the same model, run
                    and test case (mean ± SD per run). Net = improvements minus deteriorations. Text predictions
                    without a labeled passage have no counterpart and are not compared.
                </CardDescription>
            </CardHeader>
            <CardContent className="overflow-x-auto">
                <Table>
                    <TableHeader>
                        <TableRow>
                            <TableHead>Model</TableHead>
                            <TableHead>Comparison</TableHead>
                            <TableHead className="text-right">Runs</TableHead>
                            {FLIP_KINDS.map((kind) => (
                                <TableHead key={kind} className="whitespace-nowrap text-right">
                                    {FLIP_KIND_INFO[kind].name}
                                    <span className="block text-xs font-normal">{FLIP_KIND_INFO[kind].transition}</span>
                                </TableHead>
                            ))}
                            <TableHead className="text-right">Net</TableHead>
                        </TableRow>
                    </TableHeader>
                    <TableBody>
                        {comparisons.map((c) => {
                            const net = netPerRun(c);
                            return <TableRow key={comparisonId(c)}>
                                <TableCell className="font-medium">{c.model}</TableCell>
                                <TableCell className="whitespace-nowrap">{comparisonName(c)}</TableCell>
                                <TableCell className="text-right tabular-nums">{c.runs.length}</TableCell>
                                {FLIP_KINDS.map((kind) => (
                                    <TableCell key={kind} className="whitespace-nowrap text-right tabular-nums">
                                        {mean(c.counts[kind]).toFixed(1)} ± {sd(c.counts[kind]).toFixed(1)}
                                    </TableCell>
                                ))}
                                <TableCell className="whitespace-nowrap text-right font-medium tabular-nums">
                                    {signed(mean(net))} ± {sd(net).toFixed(1)}
                                </TableCell>
                            </TableRow>;
                        })}
                    </TableBody>
                </Table>
            </CardContent>
        </Card>

        <TextBarChart
            chartId="flip-analysis-chart"
            title="Flips by Kind"
            description="Changed decisions per model and comparison, improvements (cool) and deteriorations (warm), mean ± SD per run"
            categories={comparisons.map((c) => `${c.model} (${comparisonName(c)})`)}
            yTitle="Flips per run"
            decimals={1}
            series={FLIP_KINDS.map((kind) => ({
                name: `${FLIP_KIND_INFO[kind].name} (${FLIP_KIND_INFO[kind].transition})`,
                color: flipColors[kind],
                mean: comparisons.map((c) => mean(c.counts[kind])),
                sd: comparisons.map((c) => sd(c.counts[kind])),
            }))}
        />

        <Card>
            <CardHeader className="flex flex-row items-start justify-between gap-4 space-y-0">
                <div className="space-y-1.5">
                    <CardTitle>Flipped Elements</CardTitle>
                    <CardDescription>
                        Every element or labeled passage whose decision changed, with the number of runs it changed
                        in. Flips in all runs are systematic, flips in single runs may be noise of the model.
                    </CardDescription>
                </div>
                <Select value={comparisonId(selected)} onValueChange={setSelectedId}>
                    <SelectTrigger className="w-72 shrink-0"><SelectValue/></SelectTrigger>
                    <SelectContent>
                        {comparisons.map((c) => (
                            <SelectItem key={comparisonId(c)} value={comparisonId(c)}>
                                {c.model} ({comparisonName(c)})
                            </SelectItem>
                        ))}
                    </SelectContent>
                </Select>
            </CardHeader>
            <CardContent className="overflow-x-auto">
                {selected.flips.length === 0
                    ? <p className="text-sm text-muted-foreground">No decision changed.</p>
                    : <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead>Test Case</TableHead>
                                <TableHead>{selected.base === "TEXT" ? "Labeled Passage" : "Element"}</TableHead>
                                <TableHead>Flip</TableHead>
                                <TableHead className="text-right">Runs</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {selected.flips.map((flip) => (
                                <TableRow key={`${flip.testCaseId}|${flip.key}|${flip.kind}`}>
                                    <TableCell className="whitespace-nowrap">{flip.testCaseName}</TableCell>
                                    <TableCell className="max-w-md">{flip.name}</TableCell>
                                    <TableCell className="whitespace-nowrap">
                                        <FlipBadge kind={flip.kind}/>
                                    </TableCell>
                                    <TableCell className="text-right tabular-nums">
                                        {flip.runs.length}/{selected.runs.length}
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>}
            </CardContent>
        </Card>
    </div>;
}

function FlipBadge({kind}: { kind: FlipKind }) {
    return <span className="inline-flex items-center gap-1.5 text-sm">
        <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{backgroundColor: flipColors[kind]}}/>
        {FLIP_KIND_INFO[kind].name} <span className="text-muted-foreground">({FLIP_KIND_INFO[kind].transition})</span>
    </span>;
}
