"""
Generates the textual process descriptions (and their labels) for the BPMN test cases of the GRIPL dataset.

Per test case, an LLM receives a structured summary of the BPMN model - activities, events, gateways, flows, lanes,
data and annotations, but never the labels - and writes an objective process description in the language of the
model, in which it marks every activity as {{A<n>|passage}}. The markings are removed and turned into text labels
(character offsets, UTF-16 code units like the backend/frontend); a label is critical exactly when its activity is
labeled critical in the BPMN test case (expectedValues), and takes over its reason. Text and BPMN labels are
therefore consistent by construction.

Steps (only the standard library is needed; the backend must be running):
    python generate_texts.py preview  --testcase 15             # show the prompt of one test case, no LLM call
    python generate_texts.py generate --dataset 8 9 10 11 12 13  # writes output/<id>.json, existing files are skipped
    python generate_texts.py generate --dataset 8 9 10 11 12 13 --next 10   # batch: the next 10 without a text
    python generate_texts.py upload   --dataset 8 9 10 11 12 13 --next 10   # uploads that batch
    python generate_texts.py report                              # output/summary.csv with checks per test case
    python generate_texts.py upload   --dataset 8 9 10 11 12 13  # stores text + labels in the test cases

The texts are reviewed and corrected afterwards in the labeling tool (/labeling, tab "Text Description").

Expanded subprocesses (containing activities) are described as containers but not labeled, their inner activities
are. The LLM settings (model, temperature) and the prompt (prompt.txt) are stored in every output file.
"""

import argparse
import csv
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
import uuid
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUTPUT_DIR = HERE / "output"
PROMPT_FILE = HERE / "prompt.txt"
BACKEND_ENV_FILE = HERE.parent.parent / "gripl" / "gripl-backend" / ".env"

DEFAULT_BACKEND_URL = "http://localhost:8080"
DEFAULT_LLM_BASE_URL = "https://openrouter.ai/api/v1"
# Not from one of the model families evaluated in the thesis (OpenAI, Google, Mistral, DeepSeek)
DEFAULT_MODEL = "anthropic/claude-sonnet-5.5"
DEFAULT_TEMPERATURE = 0.4
MAX_ATTEMPTS = 3

BPMN_NS = "{http://www.omg.org/spec/BPMN/20100524/MODEL}"
ACTIVITY_TYPES = {
    "task", "userTask", "serviceTask", "sendTask", "receiveTask", "manualTask", "businessRuleTask", "scriptTask",
    "subProcess", "callActivity", "transaction", "adHocSubProcess",
}
EVENT_TYPES = {
    "startEvent", "endEvent", "intermediateCatchEvent", "intermediateThrowEvent", "boundaryEvent",
}
GATEWAY_TYPES = {
    "exclusiveGateway", "parallelGateway", "inclusiveGateway", "eventBasedGateway", "complexGateway",
}
MARKER = re.compile(r"\{\{(A\d+)\|(.*?)\}\}", re.DOTALL)
LANGUAGES = {"de": "German", "en": "English"}


# ---------------------------------------------------------------------------------------------------------------------
# BPMN model -> structured summary for the prompt
# ---------------------------------------------------------------------------------------------------------------------

def local(tag):
    return tag.replace(BPMN_NS, "") if isinstance(tag, str) else ""


def clean(name):
    return " ".join((name or "").split())


class ModelSummary:
    """The parts of a BPMN model the description is written from. Activities get the aliases A1..An (model order)."""

    def __init__(self, bpmn_xml):
        root = ET.fromstring(bpmn_xml.encode("utf-8"))
        self.alias = {}           # element id -> alias (activities A, events E, gateways G, subprocess containers S)
        self.activities = []      # [(alias, element id, type, name)] - the labeled activities
        self.lines = {"Activities": [], "Subprocesses (containers, not marked)": [], "Events": [], "Gateways": []}
        self.element_names = {}

        participants = {p.get("processRef"): clean(p.get("name")) for p in root.iter(BPMN_NS + "participant")}
        participant_by_id = {p.get("id"): clean(p.get("name")) for p in root.iter(BPMN_NS + "participant")}
        lane_of = {}
        for lane in root.iter(BPMN_NS + "lane"):
            for ref in lane.findall(BPMN_NS + "flowNodeRef"):
                lane_of[(ref.text or "").strip()] = clean(lane.get("name"))

        counters = {"A": 0, "E": 0, "G": 0, "S": 0}
        nodes = []  # (prefix, element, pool, parent container alias)

        def walk(container, pool, parent_alias):
            for el in container:
                kind = local(el.tag)
                if kind in ACTIVITY_TYPES:
                    is_container = any(local(c.tag) in ACTIVITY_TYPES for c in el)
                    prefix = "S" if is_container else "A"
                elif kind in EVENT_TYPES:
                    prefix = "E"
                elif kind in GATEWAY_TYPES:
                    prefix = "G"
                else:
                    continue
                counters[prefix] += 1
                self.alias[el.get("id")] = f"{prefix}{counters[prefix]}"
                nodes.append((prefix, el, pool, parent_alias))
                if prefix == "S":
                    walk(el, pool, self.alias[el.get("id")])

        for process in root.iter(BPMN_NS + "process"):
            walk(process, participants.get(process.get("id"), ""), None)

        data_names = {}
        for kind in ("dataObjectReference", "dataStoreReference", "dataObject", "dataStore", "dataInput", "dataOutput"):
            for el in root.iter(BPMN_NS + kind):
                label = "data store" if "Store" in kind else "data object"
                data_names[el.get("id")] = f'{label} "{clean(el.get("name")) or "(unnamed)"}"'

        for prefix, el, pool, parent in nodes:
            alias, kind, name = self.alias[el.get("id")], local(el.tag), clean(el.get("name"))
            self.element_names[alias] = name
            where = ", ".join(x for x in (
                f"pool: {pool}" if pool else "",
                f"lane: {lane_of[el.get('id')]}" if lane_of.get(el.get("id")) else "",
                f"inside {parent}" if parent else "",
            ) if x)
            extras = []
            if kind == "boundaryEvent":
                extras.append(f"attached to {self.alias.get(el.get('attachedToRef'), '?')}")
            definitions = [local(c.tag).replace("EventDefinition", "") for c in el if local(c.tag).endswith("EventDefinition")]
            if definitions:
                extras.append("type: " + "/".join(definitions))
            for doc in el.findall(BPMN_NS + "documentation"):
                if clean(doc.text):
                    extras.append(f'documentation: "{clean(doc.text)}"')
            reads = [data_names.get((ref.text or "").strip()) for a in el.findall(BPMN_NS + "dataInputAssociation")
                     for ref in a.findall(BPMN_NS + "sourceRef")]
            writes = [data_names.get((ref.text or "").strip()) for a in el.findall(BPMN_NS + "dataOutputAssociation")
                      for ref in a.findall(BPMN_NS + "targetRef")]
            if any(reads):
                extras.append("reads " + ", ".join(r for r in reads if r))
            if any(writes):
                extras.append("writes " + ", ".join(w for w in writes if w))
            line = (f'- {alias} [{kind}] "{name or "(unnamed)"}"' + (f" ({where})" if where else "")
                    + ("; " + "; ".join(extras) if extras else ""))

            if prefix == "A":
                self.activities.append((alias, el.get("id"), kind, name))
                self.lines["Activities"].append(line)
            elif prefix == "S":
                self.lines["Subprocesses (containers, not marked)"].append(line)
            elif prefix == "E":
                self.lines["Events"].append(line)
            else:
                self.lines["Gateways"].append(line)

        def ref(element_id):
            return self.alias.get(element_id) or (f'pool "{participant_by_id[element_id]}"'
                                                  if element_id in participant_by_id else None)

        self.lines["Sequence flows"] = []
        for flow in root.iter(BPMN_NS + "sequenceFlow"):
            source, target = ref(flow.get("sourceRef")), ref(flow.get("targetRef"))
            if source and target:
                condition = clean(flow.get("name"))
                self.lines["Sequence flows"].append(f"- {source} -> {target}" + (f' [{condition}]' if condition else ""))

        self.lines["Message flows"] = []
        for flow in root.iter(BPMN_NS + "messageFlow"):
            source, target = ref(flow.get("sourceRef")), ref(flow.get("targetRef"))
            if source and target:
                message = clean(flow.get("name"))
                self.lines["Message flows"].append(f"- {source} -> {target}" + (f' "{message}"' if message else ""))

        annotations = {a.get("id"): clean(a.findtext(BPMN_NS + "text")) for a in root.iter(BPMN_NS + "textAnnotation")}
        self.lines["Annotations"] = []
        for association in root.iter(BPMN_NS + "association"):
            for annotation_id, element_id in ((association.get("targetRef"), association.get("sourceRef")),
                                              (association.get("sourceRef"), association.get("targetRef"))):
                if annotations.get(annotation_id):
                    target = ref(element_id) or data_names.get(element_id) or "the process"
                    self.lines["Annotations"].append(f'- on {target}: "{annotations.pop(annotation_id)}"')
        self.lines["Annotations"] += [f'- "{text}"' for text in annotations.values() if text]

        self.language = detect_language([name for _, _, _, name in self.activities])

    def render(self):
        parts = []
        for title, lines in self.lines.items():
            if lines:
                parts.append(f"## {title}\n" + "\n".join(lines))
        return "\n\n".join(parts)


def detect_language(names):
    """German or English, from the activity names (the xml:lang of the exports is en-GB even for German models)."""
    german_words = {"und", "der", "die", "das", "des", "dem", "den", "für", "mit", "von", "zur", "zum", "an", "aus",
                    "bei", "nach", "über", "oder", "ein", "eine", "einer", "nicht", "ist", "wird", "werden"}
    english_words = {"and", "the", "of", "for", "with", "to", "from", "a", "an", "or", "is", "be", "patient",
                     "check", "send", "receive", "create", "update", "perform", "prepare", "order"}
    german = english = 0
    for name in names:
        words = re.findall(r"[a-zäöüß]+", name.lower())
        if re.search(r"[äöüß]", name.lower()):
            german += 2
        german += sum(w in german_words for w in words)
        english += sum(w in english_words for w in words)
        if words and re.search(r"(en|ern|eln)$", words[-1]) and words[-1] not in {"open", "screen", "given", "taken", "sent"}:
            german += 1  # German activity labels end with the infinitive ("Antrag prüfen")
    return "de" if german > english else "en"


# ---------------------------------------------------------------------------------------------------------------------
# LLM output -> text + labels
# ---------------------------------------------------------------------------------------------------------------------

def utf16_len(text):
    return len(text.encode("utf-16-le")) // 2


def parse_marked_text(marked, summary):
    """Removes the markings; returns (text, {alias: (start, end, passage)}, errors). Offsets in UTF-16 code units."""
    errors = []
    spans = {}
    text_parts = []
    position = 0       # in UTF-16 code units of the plain text
    last = 0
    for match in MARKER.finditer(marked):
        before = marked[last:match.start()]
        text_parts.append(before)
        position += utf16_len(before)
        alias, passage = match.group(1), match.group(2)
        if not passage.strip():
            errors.append(f"{alias} has an empty passage")
        if alias in spans:
            errors.append(f"{alias} is marked more than once")
        spans.setdefault(alias, (position, position + utf16_len(passage), passage))
        text_parts.append(passage)
        position += utf16_len(passage)
        last = match.end()
    text_parts.append(marked[last:])
    text = "".join(text_parts)

    if "{{" in text or "}}" in text:
        errors.append("the text contains malformed markings ('{{' or '}}' left after parsing)")
    expected = {alias for alias, _, _, _ in summary.activities}
    missing = sorted(expected - spans.keys(), key=lambda a: int(a[1:]))
    unknown = sorted(spans.keys() - expected)
    if missing:
        errors.append("not marked: " + ", ".join(f'{a} "{summary.element_names[a]}"' for a in missing))
    if unknown:
        errors.append("unknown aliases marked (only activities are marked): " + ", ".join(unknown))
    if text_language(text) != summary.language:
        errors.append(f"the text is not written in {LANGUAGES[summary.language]}; write the whole text in "
                      f"{LANGUAGES[summary.language]}")
    return text, spans, errors


def text_language(text):
    """German or English, from the most frequent function words of a running text."""
    words = re.findall(r"[a-zäöüß]+", text.lower())
    german = sum(w in {"der", "die", "das", "und", "ist", "wird", "werden", "nicht", "mit", "den", "dem", "ein",
                       "eine", "sie", "er", "zu", "auf", "im", "danach", "dann"} for w in words)
    english = sum(w in {"the", "and", "is", "are", "to", "of", "with", "a", "it", "they", "in", "then", "after",
                        "this", "that", "for", "on"} for w in words)
    return "de" if german > english else "en"


def build_labels(text, spans, summary, expected_values):
    reasons = {v["value"]: v.get("reason") for v in expected_values}
    labels = []
    for alias, element_id, _, _ in summary.activities:
        if alias not in spans:
            continue
        start, end, passage = spans[alias]
        is_critical = element_id in reasons
        labels.append({
            "id": f"text-label-{element_id}",
            "start": start,
            "end": end,
            "text": passage,
            "isCritical": is_critical,
            "reason": reasons.get(element_id) if is_critical else None,
        })
    labels.sort(key=lambda label: label["start"])
    # Check against the plain text in UTF-16 code units, exactly as backend and frontend slice it
    units = text.encode("utf-16-le")
    for label in labels:
        assert units[2 * label["start"]:2 * label["end"]].decode("utf-16-le") == label["text"], label
    return labels


def verbatim_share(spans, summary):
    """Share of activities whose label appears verbatim in their passage - a measure of closeness to the model."""
    if not spans:
        return 0.0
    hits = sum(1 for alias, (_, _, passage) in spans.items()
               if summary.element_names.get(alias) and summary.element_names[alias].lower() in passage.lower())
    return hits / len(spans)


STOPWORDS = {
    "the", "and", "for", "with", "from", "into", "all", "der", "die", "das", "den", "dem", "des", "und", "für", "mit",
    "von", "zur", "zum", "aus", "bei", "nach", "über", "ein", "eine", "einen", "einer", "ob", "sind",
}


def stem(word):
    """Crude stem for German and English: lowercase, without the participle prefix "ge", first five letters."""
    word = word.lower()
    if word.startswith("ge") and len(word) > 6:
        word = word[2:]
    return word[:5]


def label_word_overlap(spans, summary):
    """Mean share of the content words of an activity label that reappear (stemmed) in its passage - closeness to
    the model on word level; unlike verbatim_share it also catches inflected labels ("Termin bestätigen" ->
    "der Termin wird bestätigt" = 1.0)."""
    shares = []
    for alias, (_, _, passage) in spans.items():
        words = [w for w in re.findall(r"[^\W\d_]{3,}", summary.element_names.get(alias, "")) if w.lower() not in STOPWORDS]
        if not words:
            continue
        passage_stems = {stem(w) for w in re.findall(r"[^\W\d_]{3,}", passage)}
        shares.append(sum(stem(w) in passage_stems for w in words) / len(words))
    return sum(shares) / len(shares) if shares else 0.0


# ---------------------------------------------------------------------------------------------------------------------
# Backend and LLM access
# ---------------------------------------------------------------------------------------------------------------------

def http_json(url, data=None, headers=None, timeout=300):
    body = json.dumps(data).encode("utf-8") if data is not None else None
    request = urllib.request.Request(url, data=body, headers={"Content-Type": "application/json", **(headers or {})})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def get_test_cases(args):
    ids = list(args.testcase or [])
    without_text = set()
    for dataset_id in args.dataset or []:
        metas = http_json(f"{args.backend_url}/dataset/testcase?datasetId={dataset_id}")
        ids += [meta["id"] for meta in metas]
        without_text |= {meta["id"] for meta in metas if not meta["hasText"]}
    if not ids:
        sys.exit("No test cases selected: use --dataset and/or --testcase")
    ids = sorted(set(ids))
    if args.next:
        # Batch: the next test cases (by id) without a text in the DB - for generate also without an output file,
        # for upload only those with one
        pending = [i for i in ids if i in without_text
                   and output_file(i).exists() == (args.command == "upload")]
        ids = pending[:args.next]
        if not ids:
            sys.exit("No pending test cases left")
    return ids


def get_test_case(args, testcase_id):
    return http_json(f"{args.backend_url}/dataset/testcase/{testcase_id}")


def api_key():
    if os.environ.get("OPENAI_API_KEY"):
        return os.environ["OPENAI_API_KEY"]
    if BACKEND_ENV_FILE.exists():
        for line in BACKEND_ENV_FILE.read_text(encoding="utf-8").splitlines():
            if line.startswith("OPENAI_API_KEY="):
                return line.split("=", 1)[1].strip()
    sys.exit("No API key: set OPENAI_API_KEY (or put it into gripl/gripl-backend/.env)")


def chat(args, messages):
    response = http_json(
        f"{args.llm_base_url}/chat/completions",
        {"model": args.model, "messages": messages, "temperature": args.temperature},
        headers={"Authorization": f"Bearer {api_key()}"},
    )
    return response["choices"][0]["message"]["content"]


def build_messages(summary, name):
    system = PROMPT_FILE.read_text(encoding="utf-8").replace("{language}", LANGUAGES[summary.language])
    user = (f"Process: {name}\n\n{summary.render()}\n\n"
            f"Write the process description now and mark each of the {len(summary.activities)} activities "
            f"({', '.join(a for a, _, _, _ in summary.activities)}) exactly once. "
            f"Write the text in {LANGUAGES[summary.language]}.")
    return [{"role": "system", "content": system}, {"role": "user", "content": user}]


# ---------------------------------------------------------------------------------------------------------------------
# Commands
# ---------------------------------------------------------------------------------------------------------------------

def output_file(testcase_id):
    return OUTPUT_DIR / f"{testcase_id}.json"


def generate_one(args, testcase_id):
    if output_file(testcase_id).exists() and not args.force:
        return f"{testcase_id}: skipped (output exists, --force to regenerate)"
    test_case = get_test_case(args, testcase_id)
    if not test_case.get("bpmnXml"):
        return f"{testcase_id}: skipped (no BPMN model)"
    summary = ModelSummary(test_case["bpmnXml"])
    if args.language:
        summary.language = args.language
    if not summary.activities:
        return f"{testcase_id}: skipped (no activities)"

    messages = build_messages(summary, test_case["name"])
    attempts = []
    for attempt in range(1, MAX_ATTEMPTS + 1):
        try:
            marked = chat(args, messages).strip()
        except (urllib.error.URLError, TimeoutError, KeyError) as error:
            attempts.append({"error": str(error)})
            time.sleep(5 * attempt)
            continue
        text, spans, errors = parse_marked_text(marked, summary)
        attempts.append({"output": marked, "errors": errors})
        if not errors:
            break
        # Let the model correct its own output instead of starting over
        messages += [{"role": "assistant", "content": marked},
                     {"role": "user", "content": "Your text has these problems:\n- " + "\n- ".join(errors) +
                      "\n\nReturn the complete corrected text with every listed activity marked exactly once."}]

    last = attempts[-1]
    if "output" not in last:
        return f"{testcase_id}: FAILED ({last['error']})"
    text, spans, errors = parse_marked_text(last["output"], summary)
    labels = build_labels(text, spans, summary, test_case["expectedValues"])
    activity_ids = {element_id for _, element_id, _, _ in summary.activities}
    result = {
        "testcaseId": testcase_id,
        "name": test_case["name"],
        "datasetId": test_case.get("datasetId"),
        "language": summary.language,
        "valid": not errors,
        "errors": errors,
        "processText": text,
        "textLabels": labels,
        "checks": {
            "activities": len(summary.activities),
            "labels": len(labels),
            "criticalLabels": sum(label["isCritical"] for label in labels),
            "criticalBpmnLabels": len(test_case["expectedValues"]),
            # BPMN labels that cannot become text labels (not a markable activity of the model)
            "unmappedBpmnLabels": [v["value"] for v in test_case["expectedValues"] if v["value"] not in activity_ids],
            "verbatimShare": round(verbatim_share(spans, summary), 3),
            "labelWordOverlap": round(label_word_overlap(spans, summary), 3),
            "attempts": len(attempts),
        },
        "generation": {
            "model": args.model,
            "baseUrl": args.llm_base_url,
            "temperature": args.temperature,
            "prompt": messages[0]["content"],
            "modelSummary": messages[1]["content"],
            "attempts": attempts,
            "createdAt": time.strftime("%Y-%m-%dT%H:%M:%S"),
            "runId": str(uuid.uuid4()),
        },
    }
    OUTPUT_DIR.mkdir(exist_ok=True)
    output_file(testcase_id).write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    status = "ok" if not errors else "INVALID: " + "; ".join(errors)
    return (f"{testcase_id}: {status} ({summary.language}, {len(labels)}/{len(summary.activities)} labels, "
            f"{result['checks']['criticalLabels']} critical, {len(attempts)} attempt(s)) {test_case['name']}")


def cmd_generate(args):
    ids = get_test_cases(args)
    print(f"Generating {len(ids)} test case(s) with {args.model} ...")
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        for line in pool.map(lambda i: generate_one(args, i), ids):
            print(line, flush=True)


def cmd_preview(args):
    for testcase_id in get_test_cases(args):
        test_case = get_test_case(args, testcase_id)
        summary = ModelSummary(test_case["bpmnXml"])
        if args.language:
            summary.language = args.language
        system, user = build_messages(summary, test_case["name"])
        print(f"===== {testcase_id}: {test_case['name']} (language: {summary.language}) =====")
        if args.full:
            print(system["content"] + "\n-----")
        print(user["content"] + "\n")


def cmd_report(args):
    rows = []
    for path in sorted(OUTPUT_DIR.glob("*.json"), key=lambda p: int(p.stem)):
        result = json.loads(path.read_text(encoding="utf-8"))
        checks = result["checks"]
        rows.append({
            "testcaseId": result["testcaseId"], "datasetId": result["datasetId"], "name": result["name"],
            "language": result["language"], "valid": result["valid"], "activities": checks["activities"],
            "labels": checks["labels"], "criticalLabels": checks["criticalLabels"],
            "criticalBpmnLabels": checks["criticalBpmnLabels"], "unmappedBpmnLabels": len(checks["unmappedBpmnLabels"]),
            "verbatimShare": checks["verbatimShare"], "labelWordOverlap": checks.get("labelWordOverlap"),
            "attempts": checks["attempts"],
            "textLength": len(result["processText"]), "errors": " | ".join(result["errors"]),
        })
    if not rows:
        sys.exit("No output files yet")
    with open(OUTPUT_DIR / "summary.csv", "w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    invalid = [r for r in rows if not r["valid"]]
    unmapped = [r for r in rows if r["unmappedBpmnLabels"]]
    print(f"{len(rows)} test cases, {len(invalid)} invalid, "
          f"{sum(r['labels'] for r in rows)} labels ({sum(r['criticalLabels'] for r in rows)} critical), "
          f"mean verbatim share {sum(r['verbatimShare'] for r in rows) / len(rows):.2f}, "
          f"mean label word overlap {sum(r['labelWordOverlap'] or 0 for r in rows) / len(rows):.2f}")
    for r in invalid:
        print(f"  INVALID {r['testcaseId']} {r['name']}: {r['errors']}")
    for r in unmapped:
        print(f"  {r['testcaseId']} {r['name']}: {r['unmappedBpmnLabels']} BPMN label(s) without a text label")
    print(f"Written to {OUTPUT_DIR / 'summary.csv'}")


def multipart(parts):
    boundary = uuid.uuid4().hex
    body = b""
    for name, (filename, content_type, content) in parts.items():
        disposition = f'form-data; name="{name}"' + (f'; filename="{filename}"' if filename else "")
        body += (f"--{boundary}\r\nContent-Disposition: {disposition}\r\nContent-Type: {content_type}\r\n\r\n"
                 .encode("utf-8") + content + b"\r\n")
    return body + f"--{boundary}--\r\n".encode("utf-8"), f"multipart/form-data; boundary={boundary}"


def cmd_upload(args):
    for testcase_id in get_test_cases(args):
        path = output_file(testcase_id)
        if not path.exists():
            print(f"{testcase_id}: no output file")
            continue
        result = json.loads(path.read_text(encoding="utf-8"))
        if not result["valid"] and not args.force:
            print(f"{testcase_id}: skipped, invalid ({'; '.join(result['errors'])}); --force uploads it anyway")
            continue
        test_case = get_test_case(args, testcase_id)
        if test_case.get("processText") and not args.force:
            print(f"{testcase_id}: skipped, the test case already has a text; --force overwrites it")
            continue
        # Same parts as the labeling editor. expectedValues must be sent again: an omitted list part arrives in the
        # backend as an empty list, not as null, and would delete the BPMN labels. bpmnFile can be omitted.
        text_description = json.dumps({"processText": result["processText"], "textLabels": result["textLabels"]},
                                      ensure_ascii=False).encode("utf-8")
        expected_values = json.dumps(test_case["expectedValues"], ensure_ascii=False).encode("utf-8")
        body, content_type = multipart({
            "name": (None, "text/plain; charset=utf-8", (test_case["name"] or "").encode("utf-8")),
            "expectedValues": ("expectedValues.json", "application/json", expected_values),
            "textDescription": ("textDescription.json", "application/json", text_description),
        })
        with urllib.request.urlopen(urllib.request.Request(
                f"{args.backend_url}/dataset/testcase/{testcase_id}", data=body,
                headers={"Content-Type": content_type, "Accept": "application/json"}), timeout=60) as response:
            status = response.status
        # Verify that the BPMN labels are unchanged and the text labels arrived
        stored = get_test_case(args, testcase_id)
        if stored["expectedValues"] != test_case["expectedValues"] or len(stored["textLabels"]) != len(result["textLabels"]):
            sys.exit(f"{testcase_id}: stored test case does not match after the upload - stopping")
        print(f"{testcase_id}: {status} {test_case['name']} "
              f"({len(stored['textLabels'])} text labels, {len(stored['expectedValues'])} BPMN labels unchanged)")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["preview", "generate", "report", "upload"])
    parser.add_argument("--dataset", type=int, nargs="*", help="dataset ids (all their test cases)")
    parser.add_argument("--testcase", type=int, nargs="*", help="single test case ids")
    parser.add_argument("--next", type=int, help="batch: only the next N test cases of --dataset without a text "
                                                 "(generate: without output file yet, upload: with output file)")
    parser.add_argument("--backend-url", default=os.environ.get("GRIPL_BACKEND_URL", DEFAULT_BACKEND_URL))
    parser.add_argument("--llm-base-url", default=DEFAULT_LLM_BASE_URL)
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--temperature", type=float, default=DEFAULT_TEMPERATURE)
    parser.add_argument("--language", choices=list(LANGUAGES), help="override the detected language")
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--force", action="store_true", help="regenerate existing output / upload invalid or over existing text")
    parser.add_argument("--full", action="store_true", help="preview: also print the system prompt")
    args = parser.parse_args()
    {"preview": cmd_preview, "generate": cmd_generate, "report": cmd_report, "upload": cmd_upload}[args.command](args)


if __name__ == "__main__":
    main()
