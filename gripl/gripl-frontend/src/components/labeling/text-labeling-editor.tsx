"use client"

import {ReactNode, useState} from "react";
import {TextLabel} from "@/models/dto/EvaluationData";
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from "@/components/ui/card";
import {Textarea} from "@/components/ui/textarea";
import {Switch} from "@/components/ui/switch";
import {Label} from "@/components/ui/label";
import {Button} from "@/components/ui/button";
import {AlertTriangle, FileText, Trash2} from "lucide-react";
import TextLabelView from "@/components/labeling/text-label-view";
import LabelingEditorLabelCard from "@/components/labeling/labeling-editor-label-card";
import {createTextLabelId, isAnchored, overlaps} from "@/lib/text-labels";
import {useToast} from "@/components/ui/toast";

interface TextLabelingEditorProps {
    title: string;
    processText: string;
    labels: TextLabel[];
    onTextChange: (text: string) => void;
    onLabelsChange: (labels: TextLabel[]) => void;
    saveButton: ReactNode;
}

function excerpt(text: string, maxLength = 60): string {
    return text.length > maxLength ? `${text.slice(0, maxLength)}…` : text;
}

// Text counterpart of the BPMN labeling editor. In edit mode the process text is written, in label mode
// every activity is marked as a passage of the text and the GDPR-critical ones are flagged with a reason.
export default function TextLabelingEditor({title, processText, labels, onTextChange, onLabelsChange, saveButton}: TextLabelingEditorProps) {
    const [isLabelMode, setIsLabelMode] = useState<boolean>(false);
    const [selectedLabelId, setSelectedLabelId] = useState<string | null>(null);
    const {showToast} = useToast();

    const selectedLabel = labels.find(label => label.id === selectedLabelId) ?? null;
    const sortedLabels = [...labels].sort((a, b) => a.start - b.start);
    const criticalCount = labels.filter(label => label.isCritical).length;

    function handleSelectRange(start: number, end: number) {
        if (labels.some(label => isAnchored(processText, label) && overlaps(label, {start, end}))) {
            showToast({title: "The selection overlaps an existing label.", variant: "info"});
            return;
        }
        const label: TextLabel = {
            id: createTextLabelId(),
            start,
            end,
            text: processText.slice(start, end),
            isCritical: false
        };
        onLabelsChange([...labels, label]);
        setSelectedLabelId(label.id);
    }

    function handleLabelingChange(labelId: string, isChecked: boolean, reason: string) {
        onLabelsChange(labels.map(label => label.id === labelId
            ? {...label, isCritical: isChecked, reason: isChecked ? reason : undefined}
            : label
        ));
    }

    function handleDeleteLabel(labelId: string) {
        onLabelsChange(labels.filter(label => label.id !== labelId));
        if (selectedLabelId === labelId) setSelectedLabelId(null);
    }

    return <div className="flex h-full w-full gap-4 p-4">
        <div className="flex flex-1 min-w-0 justify-center overflow-y-auto">
            <div className="flex h-full w-full max-w-3xl flex-col gap-4">
                <Card className="flex flex-1 flex-col min-h-[240px]">
                    <CardHeader>
                        <CardTitle className="flex items-center gap-2 text-lg font-semibold">
                            <FileText className="h-4 w-4"/>
                            {title || "Process Description"}
                        </CardTitle>
                        <CardDescription>
                            {isLabelMode
                                ? "Select a passage with the mouse to label it as an activity. Click a highlighted passage to mark it as GDPR critical."
                                : "Describe the business process in natural language. Switch to label mode to label its activities."}
                        </CardDescription>
                    </CardHeader>
                    <CardContent className="flex flex-1 min-h-0 flex-col gap-2">
                        {isLabelMode ? (
                            <TextLabelView
                                text={processText}
                                labels={labels}
                                selectedLabelId={selectedLabelId}
                                onSelectLabel={setSelectedLabelId}
                                onSelectRange={handleSelectRange}
                            />
                        ) : (
                            <Textarea
                                className="flex-1 resize-none"
                                placeholder="e.g. The customer fills out an online registration form with their name, address and email. An employee then verifies the customer's identity..."
                                value={processText}
                                onChange={(event) => onTextChange(event.target.value)}
                            />
                        )}
                    </CardContent>
                </Card>
            </div>
        </div>
        <div className="flex w-[260px] shrink-0 flex-col gap-4 overflow-y-auto">
            <Card>
                <CardHeader>
                    <h2 className="text-lg font-semibold">Labeltools</h2>
                </CardHeader>
                <CardContent className="flex flex-col space-y-4">
                    {saveButton}
                    <div className="flex items-center space-x-2">
                        <Switch id="text-label-mode" checked={isLabelMode} onCheckedChange={setIsLabelMode}/>
                        <Label htmlFor="text-label-mode">Label Mode</Label>
                    </div>
                    <p className="text-xs text-muted-foreground">
                        {labels.length} activities labeled, {criticalCount} GDPR critical
                    </p>
                </CardContent>
            </Card>
            {isLabelMode && selectedLabel && <div className="flex flex-col gap-2">
                <LabelingEditorLabelCard
                    elementName={excerpt(selectedLabel.text)}
                    elementId={selectedLabel.id}
                    criticalActivities={labels
                        .filter(label => label.isCritical)
                        .map(label => ({value: label.id, reason: label.reason}))}
                    onLabelingChange={handleLabelingChange}
                    className="w-full"
                />
                <Button variant="outline" size="sm" onClick={() => handleDeleteLabel(selectedLabel.id)}>
                    <Trash2 className="h-4 w-4 mr-1"/>
                    Remove Label
                </Button>
            </div>}
            {labels.length > 0 && <Card>
                <CardHeader>
                    <h2 className="text-sm font-semibold">Labeled Activities</h2>
                    <div className="flex gap-3 text-xs text-muted-foreground">
                        <span className="flex items-center gap-1.5">
                            <span className="inline-block h-2 w-2 rounded-full bg-destructive"/>
                            GDPR critical
                        </span>
                        <span className="flex items-center gap-1.5">
                            <span className="inline-block h-2 w-2 rounded-full bg-sky-500"/>
                            Activity
                        </span>
                    </div>
                </CardHeader>
                <CardContent className="flex flex-col gap-1">
                    {sortedLabels.map(label => {
                        const anchored = isAnchored(processText, label);
                        return <div key={label.id} className="flex items-start gap-1">
                            <button
                                className={`flex-1 rounded-sm px-2 py-1 text-left text-xs transition-colors hover:bg-muted ${label.id === selectedLabelId ? "bg-muted" : ""}`}
                                onClick={() => {
                                    setIsLabelMode(true);
                                    setSelectedLabelId(label.id);
                                }}
                                title={anchored ? label.text : "The labeled passage no longer exists in the text"}
                            >
                                <span className={`mr-1.5 inline-block h-2 w-2 rounded-full ${label.isCritical ? "bg-destructive" : "bg-sky-500"}`}/>
                                {!anchored && <AlertTriangle className="mr-1 inline h-3 w-3 text-destructive"/>}
                                <span className={anchored ? "" : "line-through text-muted-foreground"}>{excerpt(label.text, 45)}</span>
                            </button>
                            {!anchored && <Button
                                variant="ghost"
                                size="icon"
                                className="h-6 w-6"
                                onClick={() => handleDeleteLabel(label.id)}
                                title="Remove orphaned label"
                            >
                                <Trash2 className="h-3 w-3"/>
                            </Button>}
                        </div>
                    })}
                </CardContent>
            </Card>}
        </div>
    </div>
}
