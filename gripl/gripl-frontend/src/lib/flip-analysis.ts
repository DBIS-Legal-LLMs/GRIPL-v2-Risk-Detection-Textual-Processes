import type {TestCaseReport} from "@/models/dto/ReportData";
import type {EvaluationModality} from "@/models/dto/MultiEvaluationRequest";
import {parseRunLabel} from "@/lib/run-labels";

/*
 * Flip analysis: how does the additional representation change the decision per element? Compares the same model,
 * run (same seed) and test case between a single modality and its combination - BPMN -> BPMN+TEXT per BPMN element,
 * TEXT -> TEXT+BPMN per text label. Only the items both runs are scored on can flip; text predictions without a
 * label (unmatched false positives) have no counterpart and are not part of it.
 */

export type FlipKind = "FIXED_MISS" | "REMOVED_FALSE_ALARM" | "NEW_MISS" | "NEW_FALSE_ALARM";

export const FLIP_KINDS: FlipKind[] = ["FIXED_MISS", "REMOVED_FALSE_ALARM", "NEW_MISS", "NEW_FALSE_ALARM"];

export const FLIP_KIND_INFO: Record<FlipKind, { name: string; transition: string; improves: boolean }> = {
    FIXED_MISS: {name: "Fixed misses", transition: "FN → TP", improves: true},
    REMOVED_FALSE_ALARM: {name: "Removed false alarms", transition: "FP → TN", improves: true},
    NEW_MISS: {name: "New misses", transition: "TP → FN", improves: false},
    NEW_FALSE_ALARM: {name: "New false alarms", transition: "TN → FP", improves: false},
};

export const FLIP_COMPARISONS: Array<{ base: EvaluationModality; combined: EvaluationModality }> = [
    {base: "BPMN", combined: "BPMN_WITH_TEXT"},
    {base: "TEXT", combined: "TEXT_WITH_BPMN"},
];

// One scored item of a test case: a BPMN element or a text label
export interface ScoredItem {
    key: string;
    name: string;
    labeledCritical: boolean;
    predictedCritical: boolean;
}

export interface FlippedItem {
    testCaseId: number;
    testCaseName: string;
    key: string;
    name: string;
    kind: FlipKind;
    // runs in which the item flipped this way
    runs: number[];
}

export interface FlipComparison {
    model: string;
    base: EvaluationModality;
    combined: EvaluationModality;
    // runs with at least one test case evaluated in both modalities
    runs: number[];
    // flips per kind and run, in the order of runs
    counts: Record<FlipKind, number[]>;
    flips: FlippedItem[];
}

type RunReports = Map<number, (TestCaseReport & { modelLabel: string })[]>;

// The scored items of a test case report; BPMN elements in none of the id lists are true negatives and left out
export function scoredItems(report: TestCaseReport): Map<string, ScoredItem> {
    const items = new Map<string, ScoredItem>();
    if (report.textResult) {
        for (const label of report.textResult.labels) {
            if (label.outcome === "IGNORED") continue;
            items.set(label.id, {
                key: label.id,
                name: label.text,
                labeledCritical: label.isCritical,
                predictedCritical: label.outcome === "TP" || label.outcome === "FP",
            });
        }
        return items;
    }
    // "Name (id)" of the expected and actual elements
    const nameOf = (id: string) => {
        const entry = [...report.expectedNamesWithIds, ...report.actualNamesWithIds].find((n) => n.endsWith(` (${id})`));
        return entry ? entry.slice(0, -` (${id})`.length) : id;
    };
    const add = (ids: string[], labeledCritical: boolean, predictedCritical: boolean) =>
        ids.forEach((id) => items.set(id, {key: id, name: nameOf(id), labeledCritical, predictedCritical}));
    add(report.correctActivityIds, true, true);
    add(report.falseNegativeIds, true, false);
    add(report.falsePositiveIds, false, true);
    return items;
}

// The flip of one item between the single modality (base) and the combination; null if the decision is the same.
// A missing BPMN element is a true negative on that side.
export function flipOf(base: ScoredItem | undefined, combined: ScoredItem | undefined): FlipKind | null {
    const reference = base ?? combined;
    if (!reference) return null;
    const before = base?.predictedCritical ?? false;
    const after = combined?.predictedCritical ?? false;
    if (before === after) return null;
    if (reference.labeledCritical) return after ? "FIXED_MISS" : "NEW_MISS";
    return after ? "NEW_FALSE_ALARM" : "REMOVED_FALSE_ALARM";
}

// Flip analysis of all models and comparisons present in the results
export function computeFlipAnalysis(testCasesByRun: RunReports): FlipComparison[] {
    // run -> "model|modality" -> testCaseId -> report
    const index = new Map<number, Map<string, Map<number, TestCaseReport>>>();
    const models = new Set<string>();
    for (const [run, reports] of testCasesByRun) {
        const byRunLabel = new Map<string, Map<number, TestCaseReport>>();
        for (const report of reports) {
            const parsed = parseRunLabel(report.modelLabel);
            if (!parsed) continue;
            models.add(parsed.model);
            const key = `${parsed.model}|${parsed.modality}`;
            if (!byRunLabel.has(key)) byRunLabel.set(key, new Map());
            byRunLabel.get(key)!.set(report.testCaseId, report);
        }
        index.set(run, byRunLabel);
    }

    const result: FlipComparison[] = [];
    for (const model of models) {
        for (const {base, combined} of FLIP_COMPARISONS) {
            const comparison: FlipComparison = {
                model, base, combined, runs: [],
                counts: {FIXED_MISS: [], REMOVED_FALSE_ALARM: [], NEW_MISS: [], NEW_FALSE_ALARM: []},
                flips: [],
            };
            const flipsByKey = new Map<string, FlippedItem>();

            for (const run of [...index.keys()].sort((a, b) => a - b)) {
                const baseReports = index.get(run)!.get(`${model}|${base}`);
                const combinedReports = index.get(run)!.get(`${model}|${combined}`);
                if (!baseReports || !combinedReports) continue;
                const pairs = [...baseReports.entries()]
                    .filter(([id]) => combinedReports.has(id))
                    .map(([id, report]) => [report, combinedReports.get(id)!] as const);
                if (pairs.length === 0) continue;

                const counts: Record<FlipKind, number> = {FIXED_MISS: 0, REMOVED_FALSE_ALARM: 0, NEW_MISS: 0, NEW_FALSE_ALARM: 0};
                for (const [baseReport, combinedReport] of pairs) {
                    const baseItems = scoredItems(baseReport);
                    const combinedItems = scoredItems(combinedReport);
                    // text labels ignored in one of the runs are not comparable
                    const keys = baseReport.textResult
                        ? [...baseItems.keys()].filter((k) => combinedItems.has(k))
                        : [...new Set([...baseItems.keys(), ...combinedItems.keys()])];
                    for (const key of keys) {
                        const kind = flipOf(baseItems.get(key), combinedItems.get(key));
                        if (!kind) continue;
                        counts[kind]++;
                        const flipKey = `${baseReport.testCaseId}|${key}|${kind}`;
                        const item = (baseItems.get(key) ?? combinedItems.get(key))!;
                        const flipped = flipsByKey.get(flipKey) ?? {
                            testCaseId: baseReport.testCaseId,
                            testCaseName: baseReport.testCaseName ?? `Test case ${baseReport.testCaseId}`,
                            key, name: item.name, kind, runs: [],
                        };
                        flipped.runs.push(run);
                        flipsByKey.set(flipKey, flipped);
                    }
                }
                comparison.runs.push(run);
                FLIP_KINDS.forEach((kind) => comparison.counts[kind].push(counts[kind]));
            }

            if (comparison.runs.length === 0) continue;
            comparison.flips = [...flipsByKey.values()].sort((a, b) =>
                b.runs.length - a.runs.length || a.testCaseName.localeCompare(b.testCaseName) || a.name.localeCompare(b.name));
            result.push(comparison);
        }
    }
    return result;
}
