"use client"

import {ReactNode, useEffect, useState} from "react";
import dynamic from "next/dynamic";
import type ApexCharts from "apexcharts";
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from "@/components/ui/card";
import ChartMenu from "@/components/evaluation/charts/common/chart-menu";
import {useChartTheme} from "@/components/evaluation/charts/common/use-chart-theme";

const Chart = dynamic(() => import("react-apexcharts"), {ssr: false});

export interface TextBarSeries {
    name: string;
    color: string;
    mean: number[];
    sd: number[];
}

interface TextBarChartProps {
    chartId: string;
    title: string;
    description: string;
    categories: string[];
    series: TextBarSeries[];
    yTitle: string;
    stacked?: boolean;
    // fixed axis maximum, e.g. 1 for rates; counts scale automatically
    yMax?: number;
    decimals?: number;
    headerExtra?: ReactNode;
}

// Grouped (or stacked) bars of mean values per run label; the tooltip shows mean ± SD across the runs
export default function TextBarChart({
    chartId, title, description, categories, series, yTitle, stacked = false, yMax, decimals = 3, headerExtra
}: TextBarChartProps) {
    const [isClient, setIsClient] = useState(false);
    useEffect(() => setIsClient(true), []);
    const chartTheme = useChartTheme();

    const options: ApexCharts.ApexOptions = {
        chart: {id: chartId, type: "bar", stacked, toolbar: {show: false}, animations: {enabled: false}, foreColor: chartTheme.foreColor, background: "transparent"},
        plotOptions: {
            bar: {
                horizontal: false,
                columnWidth: series.length > 1 && !stacked ? "70%" : "45%",
                borderRadius: 4,
                borderRadiusApplication: "end",
                borderRadiusWhenStacked: "last",
            },
        },
        dataLabels: {enabled: false},
        // 2px gap between adjacent bars and stacked segments
        stroke: {show: true, width: 2, colors: ["transparent"]},
        colors: series.map((s) => s.color),
        xaxis: {
            categories,
            labels: {rotate: -42, rotateAlways: categories.length > 3, style: {fontSize: "10px"}, trim: true},
        },
        yaxis: {
            min: 0,
            max: yMax,
            title: {text: yTitle, style: {fontSize: "10px", fontWeight: 400}},
            labels: {style: {fontSize: "10px"}, formatter: (v) => v.toFixed(yMax === 1 ? 1 : 0)},
        },
        grid: {borderColor: chartTheme.gridColor, strokeDashArray: 4},
        legend: {show: series.length > 1, position: "bottom", horizontalAlign: "center"},
        tooltip: {
            theme: chartTheme.tooltipTheme,
            shared: true,
            intersect: false,
            y: {
                formatter: (value, {seriesIndex, dataPointIndex}) => {
                    const sd = series[seriesIndex]?.sd[dataPointIndex] ?? 0;
                    return `${value.toFixed(decimals)} ± ${sd.toFixed(decimals)}`;
                },
            },
        },
    };

    return <Card>
        <CardHeader className="flex flex-row items-start justify-between gap-4">
            <div>
                <CardTitle>{title}</CardTitle>
                <CardDescription>{description}</CardDescription>
            </div>
            <div className="flex items-center gap-2">
                {headerExtra}
                <ChartMenu chartId={chartId}/>
            </div>
        </CardHeader>
        <CardContent>
            {isClient
                ? <Chart options={options} series={series.map((s) => ({name: s.name, data: s.mean}))} type="bar" height={300}/>
                : <div className="flex h-[300px] items-center justify-center"><p className="text-muted-foreground">Loading chart...</p></div>}
        </CardContent>
    </Card>
}
