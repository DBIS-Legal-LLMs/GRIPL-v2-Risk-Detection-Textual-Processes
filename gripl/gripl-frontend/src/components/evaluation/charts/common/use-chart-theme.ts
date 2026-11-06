"use client"

import {useTheme} from "next-themes";

/**
 * Theme-dependent ink for chart text, axes and grids. The light values are the previous defaults (ApexCharts and
 * Recharts), so light mode looks unchanged; the dark values keep labels and legends readable on the dark surface.
 */
export function useChartTheme() {
    const isDark = useTheme().resolvedTheme === "dark";
    return {
        isDark,
        // axis labels, legends, titles (ApexCharts foreColor)
        foreColor: isDark ? "#a1a1aa" : "#373d3f",
        // values written next to bars
        strongColor: isDark ? "#e4e4e7" : "#000000",
        axisColor: isDark ? "#71717a" : "#000000",
        gridColor: isDark ? "#3f3f46" : "#e0e0e0",
        // Recharts defaults: tick #666, grid #ccc
        tickColor: isDark ? "#a1a1aa" : "#666666",
        rechartsGridColor: isDark ? "#3f3f46" : "#cccccc",
        // ApexCharts tooltip style; the light one would show the inherited white text on a white box in dark mode
        tooltipTheme: isDark ? "dark" : "light",
    };
}
