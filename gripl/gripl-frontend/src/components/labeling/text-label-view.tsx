"use client"

import {ReactNode, useMemo, useRef} from "react";
import {TextLabel} from "@/models/dto/EvaluationData";
import {renderableLabels} from "@/lib/text-labels";

interface TextLabelViewProps {
    text: string;
    labels: TextLabel[];
    selectedLabelId: string | null;
    onSelectLabel: (labelId: string) => void;
    // Called with the character offsets of a passage the user selected with the mouse
    onSelectRange: (start: number, end: number) => void;
}

// Character offset of a DOM position within the container. Works because the container renders the
// process text verbatim (plain text nodes and <mark>s only, nothing else that contributes text).
function offsetWithin(container: HTMLElement, node: Node, offset: number): number {
    const range = document.createRange();
    range.selectNodeContents(container);
    range.setEnd(node, offset);
    return range.toString().length;
}

// Read-only view of the process text for labeling: labeled activities are highlighted (critical ones in red),
// selecting a passage with the mouse creates a new label, clicking a highlighted passage selects its label.
export default function TextLabelView({text, labels, selectedLabelId, onSelectLabel, onSelectRange}: TextLabelViewProps) {
    const containerRef = useRef<HTMLDivElement | null>(null);
    const ranges = useMemo(() => renderableLabels(text, labels), [text, labels]);

    function handleMouseUp() {
        const container = containerRef.current;
        const selection = window.getSelection();
        if (!container || !selection || selection.rangeCount === 0 || selection.isCollapsed) return;

        const range = selection.getRangeAt(0);
        if (!container.contains(range.startContainer) || !container.contains(range.endContainer)) return;

        let start = offsetWithin(container, range.startContainer, range.startOffset);
        let end = offsetWithin(container, range.endContainer, range.endOffset);
        // Trim surrounding whitespace, so the label covers exactly the selected words
        while (start < end && /\s/.test(text[start])) start++;
        while (end > start && /\s/.test(text[end - 1])) end--;

        selection.removeAllRanges();
        if (end > start) onSelectRange(start, end);
    }

    const parts: ReactNode[] = [];
    let cursor = 0;
    ranges.forEach(label => {
        if (label.start > cursor) parts.push(text.slice(cursor, label.start));
        const color = label.isCritical
            ? "bg-destructive/25 hover:bg-destructive/40"
            : "bg-sky-500/20 hover:bg-sky-500/35";
        const selected = label.id === selectedLabelId ? "ring-2 ring-primary" : "";
        parts.push(
            <mark
                key={label.id}
                className={`cursor-pointer rounded-sm text-foreground transition-colors ${color} ${selected}`}
                onClick={() => onSelectLabel(label.id)}
            >
                {text.slice(label.start, label.end)}
            </mark>
        );
        cursor = label.end;
    });
    if (cursor < text.length) parts.push(text.slice(cursor));

    return <div
        ref={containerRef}
        onMouseUp={handleMouseUp}
        className="flex-1 min-h-0 overflow-y-auto rounded-md border border-destructive px-3 py-2 text-base md:text-sm leading-loose whitespace-pre-wrap"
    >
        {parts}
    </div>
}
