"use client";

import { Fragment, ReactNode, useMemo } from "react";
import { CriticalElement } from "@/models/dto/AnalysisDto";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";

interface HighlightedTextProps {
  text: string;
  criticalElements: CriticalElement[];
}

interface HighlightRange {
  start: number;
  end: number;
  element: CriticalElement;
}

// Finds a passage in the text: exact match first, then a lenient match that ignores
// case and whitespace differences (LLM quotes sometimes deviate slightly).
function findPassage(
  text: string,
  passage: string,
): { start: number; end: number } | null {
  const exact = text.indexOf(passage);
  if (exact !== -1) return { start: exact, end: exact + passage.length };

  const words = passage.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return null;
  const escaped = words.map((w) => w.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"));
  const match = new RegExp(escaped.join("\\s+"), "i").exec(text);
  return match
    ? { start: match.index, end: match.index + match[0].length }
    : null;
}

// Locates the passage of every critical element. Passages that cannot be found are skipped
// (they still show up in the reasoning table), on overlaps, the earlier passage wins.
function computeRanges(
  text: string,
  elements: CriticalElement[],
): HighlightRange[] {
  const ranges = elements
    .map((element) => {
      const position = element.exactText
        ? findPassage(text, element.exactText)
        : null;
      return position ? { ...position, element } : null;
    })
    .filter((range): range is HighlightRange => range !== null)
    .sort((a, b) => a.start - b.start);

  const nonOverlapping: HighlightRange[] = [];
  for (const range of ranges) {
    const previous = nonOverlapping[nonOverlapping.length - 1];
    if (!previous || range.start >= previous.end) nonOverlapping.push(range);
  }
  return nonOverlapping;
}

// Read-only view of the process description with GDPR-critical passages highlighted.
// Hovering a passage shows the activity and the reasoning of the LLM.
export default function HighlightedText({
  text,
  criticalElements,
}: HighlightedTextProps) {
  const ranges = useMemo(
    () => computeRanges(text, criticalElements),
    [text, criticalElements],
  );

  const parts: ReactNode[] = [];
  let cursor = 0;
  ranges.forEach((range, index) => {
    if (range.start > cursor) parts.push(text.slice(cursor, range.start));
    parts.push(
      <Tooltip key={`highlight-${index}`}>
        <TooltipTrigger asChild>
          <mark className="cursor-help rounded-sm bg-destructive/20 px-0.5 text-foreground hover:bg-destructive/40 transition-colors">
            {text.slice(range.start, range.end)}
          </mark>
        </TooltipTrigger>
        <TooltipContent
          side="top"
          className="max-w-sm space-y-1 border bg-popover text-left text-popover-foreground shadow-md"
        >
          <p className="font-semibold">{range.element.name}</p>
          <p>{range.element.reason || "No reasoning provided"}</p>
        </TooltipContent>
      </Tooltip>,
    );
    cursor = range.end;
  });
  if (cursor < text.length) parts.push(text.slice(cursor));

  return (
    <TooltipProvider delayDuration={100}>
      <div className="flex-1 min-h-0 overflow-y-auto rounded-md border border-input px-3 py-2 text-base md:text-sm leading-loose whitespace-pre-wrap">
        {parts.map((part, index) => (
          <Fragment key={index}>{part}</Fragment>
        ))}
      </div>
    </TooltipProvider>
  );
}
