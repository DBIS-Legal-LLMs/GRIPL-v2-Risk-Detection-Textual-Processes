"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Separator } from "@/components/ui/separator";
import {EndpointChoice} from "@/models/evaluation/Config";
import {GenerateRandomInput} from "@/components/ui/input-generate-random";
import {EVALUATION_MODALITIES, EvaluationModality, isScoredOnText} from "@/models/dto/MultiEvaluationRequest";
import {AlertTriangle} from "lucide-react";

interface EvaluationConfigDefaultSettingsProps {
    availableEvaluationEndpoints: AnalysisEndpoint[];
    defaultEndpointChoice: EndpointChoice;
    defaultPresetEndpoint: string;
    defaultCustomEndpoint: string;
    seed: number | null;
    maxConcurrent: number;
    repetitions: number;
    useRag: boolean;
    ragMode: string;
    evaluateRag: boolean;
    activitiesOnly: boolean;
    modalities: EvaluationModality[];
    pairedOnly: boolean;
    // Models the backend would reject for the text modality (custom endpoints without a known text counterpart)
    textIncompatibleModels: string[];
    setDefaultEndpointChoice: (endpoint: EndpointChoice) => void;
    setDefaultPresetEndpoint: (endpoint: string) => void;
    setDefaultCustomEndpoint: (endpoint: string) => void;
    setSeed: (seed: number | null) => void;
    onMaxConcurrentChange: (v: number) => void;
    onRepetitionsChange: (v: number) => void;
    setUseRag: (v: boolean) => void;
    setRagMode: (v: string) => void;
    setEvaluateRag: (v: boolean) => void;
    setActivitiesOnly: (v: boolean) => void;
    setModalities: (v: EvaluationModality[]) => void;
    setPairedOnly: (v: boolean) => void;
}

const MODALITY_OPTIONS: Record<EvaluationModality, { label: string; description: string }> = {
    BPMN: {
        label: "BPMN Model",
        description: "Analyze the BPMN model and score it against its labeled elements.",
    },
    TEXT: {
        label: "Text Description",
        description: "Analyze the textual process description and score it against its labeled passages.",
    },
    BPMN_WITH_TEXT: {
        label: "BPMN Model + Text (model-centric)",
        description: "Analyze the BPMN model with the text description as additional context; scored like BPMN.",
    },
    TEXT_WITH_BPMN: {
        label: "Text + BPMN Model (text-centric)",
        description: "Analyze the text description with the BPMN model as extraction reference; scored like text.",
    },
};

const RAG_MODES = [
    { value: "hybrid", label: "Hybrid (recommended)" },
    { value: "local", label: "Local" },
    { value: "global", label: "Global" },
    { value: "naive", label: "Naive" },
];

export default function EvaluationConfigDefaultSettings(props: EvaluationConfigDefaultSettingsProps) {
    const {
        availableEvaluationEndpoints,
        defaultEndpointChoice,
        defaultPresetEndpoint,
        defaultCustomEndpoint,
        seed,
        maxConcurrent,
        repetitions,
        useRag,
        ragMode,
        evaluateRag,
        activitiesOnly,
        modalities,
        pairedOnly,
        textIncompatibleModels,
        setDefaultEndpointChoice,
        setDefaultPresetEndpoint,
        setDefaultCustomEndpoint,
        setSeed,
        onMaxConcurrentChange,
        onRepetitionsChange,
        setUseRag,
        setRagMode,
        setEvaluateRag,
        setActivitiesOnly,
        setModalities,
        setPairedOnly,
    } = props;

    // Keeps the canonical order (BPMN, TEXT, BPMN+TEXT, TEXT+BPMN) and at least one modality selected
    function toggleModality(modality: EvaluationModality, checked: boolean) {
        const next = EVALUATION_MODALITIES.filter((m) => (m === modality ? checked : modalities.includes(m)));
        if (next.length > 0) setModalities(next);
    }

    const comparesModalities = modalities.length > 1;
    // Text-scored modalities consist of activities only, BPMN-scored ones also score other element types
    const mixesScoringTargets = modalities.some(isScoredOnText) && modalities.some((m) => !isScoredOnText(m));

    return (
        <Card>
            <CardHeader>
                <CardTitle className="text-lg">Default Settings</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
                <div className="space-y-2">
                    <Label>Default Evaluation Endpoint</Label>
                    <Select value={defaultEndpointChoice}
                            onValueChange={(v: EndpointChoice) => setDefaultEndpointChoice(v)}>
                        <SelectTrigger>
                            <SelectValue/>
                        </SelectTrigger>
                        <SelectContent>
                            <SelectItem value="preset">Use preset</SelectItem>
                            <SelectItem value="custom">Custom endpoint</SelectItem>
                        </SelectContent>
                    </Select>
                </div>

                <>{defaultEndpointChoice === "preset" && (
                    <div className="space-y-2">
                        <Label>Preset Endpoint</Label>
                        <Select value={defaultPresetEndpoint} onValueChange={setDefaultPresetEndpoint}>
                            <SelectTrigger>
                                <SelectValue placeholder="Select preset endpoint"/>
                            </SelectTrigger>
                            <SelectContent>
                                <>{availableEvaluationEndpoints.map((ep) => (
                                    <SelectItem key={ep.endpoint} value={ep.endpoint}>
                                        {ep.name}
                                    </SelectItem>
                                ))}</>
                            </SelectContent>
                        </Select>
                    </div>
                )}</>

                <>{defaultEndpointChoice === "custom" && (
                    <div className="space-y-2">
                        <Label>Custom Endpoint</Label>
                        <Input type="text" placeholder="https://example.com/analysis/v1"
                               value={defaultCustomEndpoint}
                               onChange={(e) => setDefaultCustomEndpoint(e.target.value)}/>
                    </div>
                )}</>

                <div className="space-y-2">
                    <Label>Max Concurrent LLM Requests</Label>
                    <Input id="max-requests" type="number" min={1} placeholder="4"
                           value={Number.isFinite(maxConcurrent) ? maxConcurrent : ""}
                           onChange={(e) => onMaxConcurrentChange(parseInt(e.target.value, 10) || 1)}
                           className="w-full"/>
                </div>
                <div className="space-y-2">
                    <Label>Number of Repetitions</Label>
                    <Input id="repetitions" type="number" min={1} placeholder="1"
                           value={Number.isFinite(repetitions) ? repetitions : 1}
                           onChange={(e) => onRepetitionsChange(parseInt(e.target.value, 10) || 1)}
                           className="w-full"/>
                    <p className="text-sm text-muted-foreground">The evaluation will be repeated n times to gather statistics.</p>
                </div>
                <div className="space-y-2">
                    <Label>Seed</Label>
                    <GenerateRandomInput id="seed" placeholder="Optional seed for reproducibility"
                           length={8}
                           value={seed || ""}
                           alphabet={"0123456789"}
                           onChange={(e) => setSeed(parseInt(e.target.value))}
                           className="w-full"/>
                    <p className="text-sm text-muted-foreground">Warning: Not all models support a seed, but it will be used for models that support them.</p>
                </div>

                <Separator />

                {/* ── Modalities ────────────────────────────────────── */}
                <div className="space-y-4">
                    <div>
                        <p className="text-sm font-medium">Modalities</p>
                        <p className="text-xs text-muted-foreground mt-0.5">
                            Which representation of each process is analyzed. With several modalities, every model
                            is evaluated once per modality and appears as &quot;Model [BPMN]&quot;, &quot;Model [TEXT]&quot;,
                            &quot;Model [BPMN+TEXT]&quot; or &quot;Model [TEXT+BPMN]&quot;.
                        </p>
                    </div>

                    {EVALUATION_MODALITIES.map((modality) => {
                        const checked = modalities.includes(modality);
                        return (
                            <div key={modality} className="flex items-center justify-between">
                                <div>
                                    <Label htmlFor={`modality-${modality}-toggle`}>{MODALITY_OPTIONS[modality].label}</Label>
                                    <p className="text-xs text-muted-foreground">{MODALITY_OPTIONS[modality].description}</p>
                                </div>
                                <Switch
                                    id={`modality-${modality}-toggle`}
                                    checked={checked}
                                    // the last selected modality cannot be deselected
                                    disabled={checked && modalities.length === 1}
                                    onCheckedChange={(value) => toggleModality(modality, value)}
                                />
                            </div>
                        );
                    })}

                    {comparesModalities && (
                        <div className="flex items-center justify-between">
                            <div>
                                <Label htmlFor="paired-only-toggle">Paired Test Cases Only</Label>
                                <p className="text-xs text-muted-foreground">
                                    Evaluate only test cases providing every selected modality, so the
                                    modalities are compared on exactly the same processes.
                                </p>
                            </div>
                            <Switch
                                id="paired-only-toggle"
                                checked={pairedOnly}
                                onCheckedChange={setPairedOnly}
                            />
                        </div>
                    )}

                    {mixesScoringTargets && !activitiesOnly && (
                        <div className="flex gap-2 rounded-md border border-amber-500/50 bg-amber-500/10 p-3 text-xs">
                            <AlertTriangle className="h-4 w-4 shrink-0 text-amber-500"/>
                            <p>
                                Text consists of activities only, while BPMN also scores events, gateways and data
                                objects. Enable &quot;Evaluate Activities Only&quot; for a fair comparison.
                            </p>
                        </div>
                    )}

                    {textIncompatibleModels.length > 0 && (
                        <div className="flex gap-2 rounded-md border border-destructive/50 bg-destructive/10 p-3 text-xs">
                            <AlertTriangle className="h-4 w-4 shrink-0 text-destructive"/>
                            <p>
                                The text and combined modalities are only available for the prompt-engineering
                                analysis and the baseline. The run will be rejected, as these models use a custom endpoint:{" "}
                                {textIncompatibleModels.join(", ")}.
                            </p>
                        </div>
                    )}
                </div>

                <Separator />

                {/* ── Evaluation Scope ──────────────────────────────── */}
                <div className="flex items-center justify-between">
                    <div>
                        <Label htmlFor="activities-only-toggle">Evaluate Activities Only</Label>
                        <p className="text-xs text-muted-foreground">
                            Score only activities; events, gateways and data
                            objects/stores are not evaluated.
                        </p>
                    </div>
                    <Switch
                        id="activities-only-toggle"
                        checked={activitiesOnly}
                        onCheckedChange={setActivitiesOnly}
                    />
                </div>

                <Separator />

                {/* ── RAG Configuration ─────────────────────────────── */}
                <div className="space-y-4">
                    <div>
                        <p className="text-sm font-medium">RAG Configuration</p>
                        <p className="text-xs text-muted-foreground mt-0.5">
                            Enable to evaluate with retrieval-augmented generation.
                        </p>
                    </div>

                    <div className="flex items-center justify-between">
                        <div>
                            <Label htmlFor="use-rag-toggle">Use RAG</Label>
                            <p className="text-xs text-muted-foreground">
                                The LLM receives GDPR knowledge retrieved from the graph.
                            </p>
                        </div>
                        <Switch
                            id="use-rag-toggle"
                            checked={useRag}
                            onCheckedChange={setUseRag}
                        />
                    </div>

                    {useRag && (
                        <>
                            <div className="space-y-2">
                                <Label>RAG Mode</Label>
                                <Select value={ragMode} onValueChange={setRagMode}>
                                    <SelectTrigger>
                                        <SelectValue />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {RAG_MODES.map((m) => (
                                            <SelectItem key={m.value} value={m.value}>
                                                {m.label}
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                                <p className="text-xs text-muted-foreground">
                                    Hybrid uses both local (entity) and global (community) graph search.
                                </p>
                            </div>

                            <div className="flex items-center justify-between">
                                <div>
                                    <Label htmlFor="evaluate-rag-toggle">Evaluate RAG Quality (Ragas)</Label>
                                    <p className="text-xs text-muted-foreground">
                                        Scores context utilization and faithfulness using an LLM judge.
                                        Adds latency per test case.
                                    </p>
                                </div>
                                <Switch
                                    id="evaluate-rag-toggle"
                                    checked={evaluateRag}
                                    onCheckedChange={setEvaluateRag}
                                />
                            </div>
                        </>
                    )}
                </div>
            </CardContent>
        </Card>
    );
}
