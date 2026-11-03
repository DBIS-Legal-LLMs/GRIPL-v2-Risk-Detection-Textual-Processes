import {Card, CardContent, CardHeader} from "@/components/ui/card";
import {Switch} from "@/components/ui/switch";
import {Label} from "@/components/ui/label";
import {Textarea} from "@/components/ui/textarea";
import {useEffect, useState} from "react";
import {ExpectedValues} from "@/models/dto/EvaluationData";
import {Button} from "@/components/ui/button";
import {Trash2} from "lucide-react";

export interface LabelingEditorProps {
    elementName: string;
    elementId: string;
    criticalActivities: ExpectedValues[];
    onLabelingChange: (elementId: string, isChecked: boolean, reason: string) => void;
    className?: string;
    // When set, a remove icon is shown in the header (used for text labels, BPMN elements cannot be removed here)
    onRemove?: () => void;
}

export default function ({ elementName, elementId, criticalActivities, onLabelingChange, className, onRemove }: LabelingEditorProps) {

    const [criticalChecked, setCriticalChecked] = useState(
        criticalActivities.some(critical => critical.value === elementId)
    )
    const [reason, setReason] = useState(criticalActivities.find(
        critical => critical.value === elementId)?.reason || ""
    )

    useEffect(() => {
        setCriticalChecked(criticalActivities.some(critical => critical.value === elementId));
        const foundReason = criticalActivities.find(critical => critical.value === elementId)?.reason || "";
        setReason(foundReason);
    }, [elementId]);

    return <Card className={className ?? "w-[202px]"}>
        <CardHeader className="flex flex-row items-start justify-between gap-2 space-y-0">
            <h2 className="min-w-0 break-words text-lg font-semibold">{elementName}</h2>
            {onRemove && <Button
                variant="ghost"
                size="icon"
                className="h-7 w-7 shrink-0 text-muted-foreground hover:text-destructive"
                onClick={onRemove}
                title="Remove label"
                aria-label="Remove label"
            >
                <Trash2 className="h-4 w-4"/>
            </Button>}
        </CardHeader>
        <CardContent>
            <div className="flex items-center space-x-2">
                <Switch
                    id={`critical-${elementId}`}
                    checked={criticalChecked}
                    onCheckedChange={(checked) => {
                        setCriticalChecked(checked)
                        onLabelingChange(elementId, checked, reason)
                    }}
                />
                <Label htmlFor={`critical-${elementId}`}>Is GDPR critical</Label>
            </div>
            { criticalChecked && <div className="mt-4 flex flex-col space-y-2">
                <Label htmlFor={`reason-${elementId}`}>Reason for labeling</Label>
                <Textarea
                    id={`reason-${elementId}`}
                    className="p-2 border rounded"
                    placeholder="Enter reason for labeling this activity..."
                    rows={5}
                    value={reason}
                    onChange={(event) => {
                        setReason(event.target.value);
                        onLabelingChange(elementId, criticalChecked, event.target.value)
                    }}
                />
            </div> }
        </CardContent>
    </Card>
}