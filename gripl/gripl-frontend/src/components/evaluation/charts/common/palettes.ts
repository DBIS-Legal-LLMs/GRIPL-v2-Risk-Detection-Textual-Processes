export const defaultColors = {
    passed: "#22c55e",
    failed: "#ef4444",
    error: "#f59e0b",
    truePositive: "#16a34a",
    falsePositive: "#ef4444",
    falseNegative: "#f59e0b",
    trueNegative: "#0ea5e9",
    accuracy: "#2563eb",
    precision: "#8b5cf6",
    recall: "#f59e0b",
    f1Score: "#22c55e",
};

// Series colors of the text evaluation charts; each set is validated for color-vision deficiencies and
// contrast in light and dark mode (dataviz palette validator)
export const textEvaluationColors = {
    // BPMN vs. text per model
    bpmn: "#2563eb",
    text: "#d97706",
    // partial vs. strict matching
    partial: "#7c3aed",
    strict: "#0d9488",
    // false negatives by pipeline step
    fnExtraction: "#d97706",
    fnClassification: "#7c3aed",
    // extraction quality
    extractionPrecision: "#7c3aed",
    extractionRecall: "#d97706",
    localization: "#0d9488",
};

export const modelPalette = [
    "#3b82f6",
    "#ef4444",
    "#fb923c",
    "#22c55e",
    "#a855f7",
    "#eab308",
    "#14b8a6",
    "#ec4899",
    "#8b5cf6",
    "#f97316",
];

export const mergeEvalColors = (getEvaluationColors?: () => any) => {
    try {
        const custom = getEvaluationColors?.();
        return { ...defaultColors, ...(custom ?? {}) };
    } catch {
        return defaultColors;
    }
};
