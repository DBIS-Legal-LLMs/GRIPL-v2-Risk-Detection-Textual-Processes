"use client"

import BpmnEditor from "@/components/bpmn-editor";
import {EvaluationData, ExpectedValues, TextLabel} from "@/models/dto/EvaluationData";
import {BpmnToolCard} from "@/models/BpmnToolCard";
import {Card, CardContent, CardHeader} from "@/components/ui/card";
import {Button} from "@/components/ui/button";
import {FileText, Plus, Save, Workflow} from "lucide-react";
import {useState} from "react";
import {Switch} from "@/components/ui/switch";
import {Label} from "@/components/ui/label";
import {BpmnEditorEvent} from "@/models/BpmnEditorEvent";
import LabelingEditorLabelCard from "@/components/labeling/labeling-editor-label-card";
import TextLabelingEditor from "@/components/labeling/text-labeling-editor";
import {Spinner} from "@/components/ui/spinner";
import {useToast} from "@/components/ui/toast";
import {extractErrorDetails, toErrorMessage} from "@/lib/http-error";
import {Tabs, TabsContent, TabsList, TabsTrigger} from "@/components/ui/tabs";
import {reanchorLabel} from "@/lib/text-labels";
import emptyDiagram from "@/data/empty-diagram.bpmn";

interface LabelingEditorProps {
    className?: string;
    evaluationData: EvaluationData;
}

// A test case represents one process: the BPMN model and the textual description are labeled in separate tabs
// and saved together. A missing part can be added to pair it with the existing one.
export default function LabelingEditor({ className, evaluationData }: LabelingEditorProps) {
    const [diagram, setDiagram] = useState<string | null>(evaluationData.bpmnXml)
    const [isLabelMode, setIsLabelMode] = useState<boolean>(false);
    const [criticalActivities, setCriticalActivities] = useState<ExpectedValues[]>(evaluationData.expectedValues);
    const [processText, setProcessText] = useState<string | null>(evaluationData.processText)
    const [textLabels, setTextLabels] = useState<TextLabel[]>(evaluationData.textLabels ?? [])
    const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);
    const [selectedElement, setSelectedElement] = useState<any | null>(null);
    const [isSaveLoading, setIsSaveLoading] = useState(false);

    const {showToast, showError} = useToast();

    function onSave() {

        setIsSaveLoading(true);

        const formData = new FormData();
        formData.append("name", evaluationData.name);
        if (diagram !== null) {
            const xmlBlob = new Blob([diagram], { type: "application/xml" });
            formData.append("bpmnFile", xmlBlob, "diagram.bpmn");
        }
        const expectedValuesBlob = new Blob(
            [JSON.stringify(criticalActivities)],
            { type: 'application/json' }
        );
        formData.append('expectedValues', expectedValuesBlob, 'expectedValues.json');
        if (processText !== null) {
            // Text and labels go as one JSON part: keeps line breaks exactly (label offsets stay valid)
            // and is never empty, so an empty text can be saved as well
            const textDescriptionBlob = new Blob(
                [JSON.stringify({processText, textLabels})],
                { type: 'application/json' }
            );
            formData.append('textDescription', textDescriptionBlob, 'textDescription.json');
        }

        fetch(`/api/dataset/testcase/${evaluationData.id}`, {
            method: "POST",
            headers: {
                Accept: "application/json",
            },
            body: formData,
        }).then(async response => {
            if (!response.ok) {
                const details = await extractErrorDetails(response);
                console.error("Error while saving test case:", details);
                throw new Error(details);
            }
            setHasUnsavedChanges(false)
            setIsSaveLoading(false);
            showToast({title: "Test case saved successfully", variant: "success"});
        }).catch(error => {
            console.error("Error while saving test case:", error);
            setIsSaveLoading(false);
            showError("Failed to save the test case", toErrorMessage(error));
        })
    }

    function handleDiagramChanged(xml: string) {
        setDiagram(xml)
        setHasUnsavedChanges(true)
    }

    function handleTextChanged(text: string) {
        setProcessText(text)
        // Keep labels on their passages when text before them is inserted or removed
        setTextLabels(labels => labels.map(label => reanchorLabel(text, label)))
        setHasUnsavedChanges(true)
    }

    function handleTextLabelsChanged(labels: TextLabel[]) {
        setTextLabels(labels)
        setHasUnsavedChanges(true)
    }

    function handleElementLabelingChange(elementId: string, isChecked: boolean, reason: string) {
        if (isChecked) {
            if (!criticalActivities.some(critical => critical.value === elementId)) {
                setCriticalActivities([...criticalActivities, { value: elementId, reason: reason }]);
            } else {
                setCriticalActivities(criticalActivities.map(critical =>
                    critical.value === elementId ? { ...critical, reason: reason } : critical
                ));
            }
        } else {
            setCriticalActivities(criticalActivities.filter(critical => critical.value !== elementId))
        }
        setHasUnsavedChanges(true)
    }

    function handleEditorEvent(type: BpmnEditorEvent, event: any) {
        if (type === BpmnEditorEvent.SelectionChanged) {
            setSelectedElement(event.newSelection.length === 1 ? event.newSelection[0] : null);
        }
    }

    const saveButton = <Button
        onClick={onSave}
        variant="default"
        disabled={!hasUnsavedChanges || isSaveLoading}
        className={"min-w-[153px]"}
    >
        { isSaveLoading ? <>
            <Spinner className="h-4 w-4 text-foreground" />
            Saving...
        </> : <>
            <Save className="mr-2 h-4 w-4"/>
            Save Test Case
        </>}
    </Button>

    const cards: BpmnToolCard[] = [
        {
            position: "top-right",
            content: <Card>
                <CardHeader>
                    <h2 className="text-lg font-semibold">Labeltools</h2>
                </CardHeader>
                <CardContent className="flex flex-col space-y-4">
                    {saveButton}
                    <div className="flex items-center space-x-2">
                        <Switch id="label-mode" onCheckedChange={setIsLabelMode}/>
                        <Label htmlFor="label-mode">Label Mode</Label>
                    </div>
                </CardContent>
            </Card>
        },
        {
            position: "top-right",
            content: selectedElement && isLabelMode && <LabelingEditorLabelCard
                elementName={selectedElement.businessObject.name || "No Name"}
                elementId={selectedElement.id}
                criticalActivities={criticalActivities}
                onLabelingChange={handleElementLabelingChange}
            />
        }
    ]

    function addPartPlaceholder(description: string, buttonLabel: string, onAdd: () => void) {
        return <div className="flex h-full w-full flex-col items-center justify-center gap-4 p-4">
            <p className="text-muted-foreground">{description}</p>
            <Button onClick={onAdd}>
                <Plus className="mr-2 h-4 w-4"/>
                {buttonLabel}
            </Button>
        </div>
    }

    return (
        <div className={`h-full min-h-0 ${className}`}>
            <Tabs defaultValue={diagram !== null ? "bpmn" : "text"} className="h-full w-full gap-0 min-h-0">
                <div className="px-2 py-2 bg-card border-b">
                    <TabsList>
                        <TabsTrigger value="bpmn">
                            <Workflow className="h-4 w-4" />
                            BPMN Model
                        </TabsTrigger>
                        <TabsTrigger value="text">
                            <FileText className="h-4 w-4" />
                            Text Description
                        </TabsTrigger>
                    </TabsList>
                </div>
                {/* forceMount keeps both views mounted, so the diagram (incl. zoom/undo) survives tab switches */}
                <TabsContent value="bpmn" forceMount className="min-h-0 data-[state=inactive]:hidden">
                    {diagram !== null ? <BpmnEditor
                        title={evaluationData.name || ""}
                        bpmnXml={diagram}
                        cards={cards}
                        highlightedActivityIds={criticalActivities.map(critical => critical.value)}
                        onDiagramChanged={handleDiagramChanged}
                        editorClassName={isLabelMode ? "border border-destructive" : ""}
                        disableEditing={isLabelMode}
                        onEvent={handleEditorEvent}
                    /> : addPartPlaceholder(
                        "This test case has no BPMN model yet.",
                        "Add BPMN Model",
                        () => handleDiagramChanged(emptyDiagram as string)
                    )}
                </TabsContent>
                <TabsContent value="text" forceMount className="min-h-0 data-[state=inactive]:hidden">
                    {processText !== null ? <TextLabelingEditor
                        title={evaluationData.name || ""}
                        processText={processText}
                        labels={textLabels}
                        onTextChange={handleTextChanged}
                        onLabelsChange={handleTextLabelsChanged}
                        saveButton={saveButton}
                    /> : addPartPlaceholder(
                        "This test case has no textual process description yet.",
                        "Add Text Description",
                        () => handleTextChanged("")
                    )}
                </TabsContent>
            </Tabs>
        </div>
    );
}
