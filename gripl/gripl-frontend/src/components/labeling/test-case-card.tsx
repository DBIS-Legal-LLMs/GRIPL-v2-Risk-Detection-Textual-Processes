"use client"

import {Card} from "@/components/ui/card";
import Link from "next/link";
import DeleteTestCaseButton from "@/components/labeling/delete-test-case-button";
import {EvaluationDataMeta} from "@/models/dto/EvaluationData";
import {Skeleton} from "@/components/ui/skeleton";
import useLoadPreviewImage from "@/hooks/use-load-preview-image";
import {Badge} from "@/components/ui/badge";
import {FileText} from "lucide-react";

interface TestCaseCardProps {
    metadata: EvaluationDataMeta
}

function BpmnPreview({ testCaseId }: { testCaseId: number }) {
    const { previewImage, isLoading} = useLoadPreviewImage({
        testCaseId,
        imageClassName: "w-auto h-28",
    })

    return <>
        { isLoading && !previewImage && <Skeleton className="h-28 w-full" /> }
        { previewImage }
    </>
}

export default function TestCaseCard({ metadata } : TestCaseCardProps) {

    return <div className="flex flex-row items-center justify-between space-x-2 h-min">
        <Card className="flex-1 relative hover:bg-card/40">
            <Link href={`/labeling/${metadata.id}`}>
                <div className="p-4">
                    <h2 className="text-lg font-bold mb-2 mr-12">{metadata.name || "Test Case"} ({metadata.id})</h2>
                    <div className="flex gap-1 mb-4">
                        { metadata.hasBpmn && <Badge variant="secondary">BPMN</Badge> }
                        { metadata.hasText && <Badge variant="secondary">Text</Badge> }
                    </div>
                    { metadata.hasBpmn
                        ? <BpmnPreview testCaseId={metadata.id} />
                        : <div className="flex h-28 items-center justify-center text-muted-foreground">
                            <FileText className="h-10 w-10" />
                        </div> }
                </div>
            </Link>
            <div className="absolute right-4 top-4">
                <DeleteTestCaseButton testCaseId={metadata.id} testCaseName={metadata.name}/>
            </div>
        </Card>
    </div>
}
