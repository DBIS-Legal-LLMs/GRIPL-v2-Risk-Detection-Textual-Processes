"use client";

import { AnalysisResponse } from "@/models/dto/AnalysisDto";
import AnalysisToolCard from "@/components/sandbox/analysis-tool-card";
import AnalysisResultCard from "@/components/sandbox/analysis-result-card";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { Eraser, FileText } from "lucide-react";

interface TextSandboxProps {
  processText: string;
  setProcessText: (text: string) => void;
  analysisResult: AnalysisResponse | null;
  setAnalysisResult: (result: AnalysisResponse | null) => void;
}

// Text counterpart of the BPMN sandbox, a central text field for a textual process
// description adn with the same GRIPL Analysis Tool on the right.
export default function TextSandbox({
  processText,
  setProcessText,
  analysisResult,
  setAnalysisResult,
}: TextSandboxProps) {
  return (
    <div className="flex h-full w-full gap-4 p-4">
      <div className="flex flex-1 min-w-0 justify-center overflow-y-auto">
        <div className="flex w-full max-w-3xl flex-col gap-4">
          <Card className="flex flex-1 flex-col min-h-[320px]">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-lg font-semibold">
                <FileText className="h-4 w-4" />
                Process Description
              </CardTitle>
              <CardDescription>
                Describe the business process in natural language. The analysis
                extracts its activities and identifies those that process
                personal data.
              </CardDescription>
            </CardHeader>
            <CardContent className="flex flex-1 flex-col gap-2">
              <div className="flex justify-end">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    setProcessText("");
                    setAnalysisResult(null);
                  }}
                  disabled={!processText}
                  title="Clear the process description"
                >
                  <Eraser className="h-4 w-4 mr-1" />
                  Clear Text
                </Button>
              </div>
              <Textarea
                className="flex-1 resize-none"
                placeholder="e.g. The customer fills out an online registration form with their name, address and email. An employee then verifies the customer's identity..."
                value={processText}
                onChange={(e) => setProcessText(e.target.value)}
              />
            </CardContent>
          </Card>
          {analysisResult && (
            <AnalysisResultCard analysisResult={analysisResult} />
          )}
        </div>
      </div>
      <div className="shrink-0 overflow-y-auto">
        <AnalysisToolCard
          processText={processText}
          analysisResult={analysisResult}
          setAnalysisResult={setAnalysisResult}
        />
      </div>
    </div>
  );
}
