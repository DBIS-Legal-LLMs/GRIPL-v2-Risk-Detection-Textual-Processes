"use client"

import {Card, CardContent, CardDescription, CardHeader, CardTitle} from "@/components/ui/card"
import ApexCharts from "react-apexcharts"
import ChartMenu from "@/components/evaluation/charts/common/chart-menu";
import {useEffect, useState} from "react";
import {useChartTheme} from "@/components/evaluation/charts/common/use-chart-theme";

export interface EvaluationReportSummary {
    type: "summary"
    total: number
    passed: number
    failed: number
    error: number
    amountOfRetries: number | null
    precision: number
    recall: number
    f1Score: number
    accuracy: number
    totalTruePositives: number
    totalFalsePositives: number
    totalFalseNegatives: number
    totalTrueNegatives: number
    markdown: string
}

interface PerformanceMetricsSideBySideProps {
    reportSummaries: Array<{ label: string; summary: EvaluationReportSummary }>
}

export function PerformanceMetricsBarsMulti({ reportSummaries }: PerformanceMetricsSideBySideProps) {
    const chartTheme = useChartTheme()
    const [isClient, setIsClient] = useState(false)
    const chartId = "performance-metrics-chart"

    useEffect(() => {
        setIsClient(true)
    }, [])

    const series = [
        {
            name: "Accuracy",
            data: reportSummaries.map((report) => report.summary.accuracy * 100),
        },
        {
            name: "Precision",
            data: reportSummaries.map((report) => report.summary.precision * 100),
        },
        {
            name: "Recall",
            data: reportSummaries.map((report) => report.summary.recall * 100),
        },
        {
            name: "F1-Score",
            data: reportSummaries.map((report) => report.summary.f1Score * 100),
        },
    ]

    const options: ApexCharts.ApexOptions = {
        chart: {
            foreColor: chartTheme.foreColor,
            background: "transparent",
            id: chartId,
            type: "bar",
            toolbar: { show: false },
            animations: { enabled: false}
        },
        plotOptions: {
            bar: {
                horizontal: false,
                columnWidth: "85%",
                dataLabels: {
                    position: "top",
                },
            },
        },
        dataLabels: {
            enabled: false,
        },
        stroke: {
            show: true,
            width: 2,
            colors: ["transparent"],
        },
        grid: { borderColor: chartTheme.gridColor },
        xaxis: {
            categories: reportSummaries.map((report) => report.label),
            labels: {
                rotate: -42,
                rotateAlways: true,
                style: { fontSize: "10px" }
            },
        },
        yaxis: {
            title: {
                text: "Percentage (%)",
                style: { fontSize: "10px", fontWeight: 400 },
            },
            min: 0,
            max: 100,
            labels: {
                style: { fontSize: "10px" },
                formatter: (val) => `${val.toFixed(0)}%`,
            },
        },
        colors: ["#008FFB", "#00E396", "#FEB019", "#FF4560"],
        legend: {
            position: "bottom",
            horizontalAlign: "center",
        },
        tooltip: {
            theme: chartTheme.tooltipTheme,
            y: {
                formatter: (val) => `${val.toFixed(2)}%`,
            },
        },
    }

    if (!isClient) {
        return (
            <Card>
                <CardHeader>
                    <CardTitle>Performance Metrics</CardTitle>
                    <CardDescription>Comparison of key performance metrics across models</CardDescription>
                </CardHeader>
                <CardContent>
                    <div className="h-[300px] flex items-center justify-center">
                        <p className="text-muted-foreground">Loading chart...</p>
                    </div>
                </CardContent>
            </Card>
        )
    }

    return (
        <>
            <Card>
                <CardHeader className="flex flex-row items-center justify-between">
                    <div>
                        <CardTitle>Performance Metrics</CardTitle>
                        <CardDescription>Comparison of key performance metrics across models</CardDescription>
                    </div>
                    <ChartMenu chartId={chartId} />
                </CardHeader>
                <CardContent>
                    <ApexCharts options={options} series={series} type="bar" height={300} />
                </CardContent>
            </Card>
        </>
    )
}
