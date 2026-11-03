import {TextLabel} from "@/models/dto/EvaluationData";

export function createTextLabelId(): string {
    return `text-label-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

// A label is anchored while its offsets still point at its labeled passage
export function isAnchored(text: string, label: TextLabel): boolean {
    return text.slice(label.start, label.end) === label.text;
}

// Re-anchors a label after the text was edited: keeps it if it still matches, otherwise moves it to the
// occurrence of its passage closest to its old position. Labels whose passage no longer exists stay unchanged
// (and are reported as orphaned by isAnchored).
export function reanchorLabel(text: string, label: TextLabel): TextLabel {
    if (isAnchored(text, label) || !label.text) return label;

    let bestStart = -1;
    for (let index = text.indexOf(label.text); index !== -1; index = text.indexOf(label.text, index + 1)) {
        if (bestStart === -1 || Math.abs(index - label.start) < Math.abs(bestStart - label.start)) {
            bestStart = index;
        }
    }
    return bestStart === -1 ? label : {...label, start: bestStart, end: bestStart + label.text.length};
}

export function overlaps(a: { start: number, end: number }, b: { start: number, end: number }): boolean {
    return a.start < b.end && b.start < a.end;
}

// Anchored labels sorted by position, on overlaps the earlier label wins
export function renderableLabels(text: string, labels: TextLabel[]): TextLabel[] {
    const sorted = labels.filter(label => isAnchored(text, label)).sort((a, b) => a.start - b.start);
    const result: TextLabel[] = [];
    for (const label of sorted) {
        const previous = result[result.length - 1];
        if (!previous || label.start >= previous.end) result.push(label);
    }
    return result;
}
