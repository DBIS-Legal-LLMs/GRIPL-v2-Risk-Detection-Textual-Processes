#!/usr/bin/env python3
"""Minimal, unprocessed-text GDPR test.

Purpose: see how an LLM handles a *raw* textual process description when given
nothing but one elaborate system prompt — no BPMN parsing, no chunking, no RAG,
no structured extraction. Pure prompt-in / text-out, for exploratory testing
only (not part of the GRIPL analysis pipeline).

Usage:
    python scripts/simple_text_gdpr_test.py                       # uses the bundled sample text
    python scripts/simple_text_gdpr_test.py path/to/process.txt   # analyze your own file
    python scripts/simple_text_gdpr_test.py --text "Der Kunde..." # inline text
    echo "..." | python scripts/simple_text_gdpr_test.py -        # read from stdin

LLM endpoint config (env vars, all optional — sensible defaults are picked up
from .env.local / gripl-backend/.env so this reuses the same LLM the app uses).
Note: these are deliberately NOT named LLM_API_KEY/LLM_MODEL/LLM_BASE_URL —
those names are already used by gripl-rag's own LLM binding in .env.local.
    TEXT_TEST_BASE_URL   e.g. https://openrouter.ai/api/v1 or https://api.openai.com/v1
    TEXT_TEST_MODEL      e.g. google/gemini-2.5-flash or gpt-4o-mini
    TEXT_TEST_API_KEY    falls back to OPEN_ROUTER_API_KEY, then OPENAI_API_KEY
Or override per run with --base-url / --model / --api-key.
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
PROMPT_FILE = Path(__file__).resolve().parent / "prompts" / "system-prompt-text-simple.txt"
SAMPLE_FILE = Path(__file__).resolve().parent / "sample-process-description.txt"
OUTPUT_DIR = Path(__file__).resolve().parent / "output"

DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"
DEFAULT_MODEL = "google/gemini-2.5-flash"


def load_dotenv_file(path: Path) -> None:
    """Tiny .env loader (KEY=VALUE per line) — does not override already-set env vars."""
    if not path.exists():
        return
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


def resolve_input_text(args: argparse.Namespace) -> str:
    if args.text is not None:
        return args.text
    if args.input == "-":
        return sys.stdin.read()
    if args.input is not None:
        return Path(args.input).read_text(encoding="utf-8")
    return SAMPLE_FILE.read_text(encoding="utf-8")


def call_llm(base_url: str, model: str, api_key: str, system_prompt: str, user_text: str, timeout: int) -> str:
    url = base_url.rstrip("/") + "/chat/completions"
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_text},
        ],
        "temperature": 0.2,
    }
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", errors="replace")
        raise SystemExit(f"LLM request failed ({e.code} {e.reason}):\n{detail}")
    except urllib.error.URLError as e:
        raise SystemExit(f"Could not reach LLM endpoint at {url}: {e.reason}")

    try:
        return body["choices"][0]["message"]["content"]
    except (KeyError, IndexError):
        raise SystemExit(f"Unexpected LLM response shape:\n{json.dumps(body, indent=2)}")


def main() -> None:
    # Windows terminals often default stdout/stderr to a non-UTF-8 codepage, which mangles
    # German umlauts etc. in the printed result. Force UTF-8 where supported (Python 3.7+).
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")

    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("input", nargs="?", help="Path to a text file (or '-' for stdin). Defaults to the bundled sample.")
    parser.add_argument("--text", help="Inline process description text (overrides input file).")
    parser.add_argument("--base-url", default=None, help=f"OpenAI-compatible base URL (default: {DEFAULT_BASE_URL}).")
    parser.add_argument("--model", default=None, help=f"Model name (default: {DEFAULT_MODEL}).")
    parser.add_argument("--api-key", default=None, help="API key (default: from env, see module docstring).")
    parser.add_argument("--timeout", type=int, default=120, help="Request timeout in seconds (default: 120).")
    parser.add_argument("--no-save", action="store_true", help="Don't write the result to scripts/output/.")
    args = parser.parse_args()

    load_dotenv_file(REPO_ROOT / ".env.local")
    load_dotenv_file(REPO_ROOT / "gripl" / "gripl-backend" / ".env")

    base_url = args.base_url or os.getenv("TEXT_TEST_BASE_URL", DEFAULT_BASE_URL)
    model = args.model or os.getenv("TEXT_TEST_MODEL", DEFAULT_MODEL)
    api_key = (
        args.api_key
        or os.getenv("TEXT_TEST_API_KEY")
        or os.getenv("OPEN_ROUTER_API_KEY")
        or os.getenv("OPENAI_API_KEY")
    )

    if not api_key:
        raise SystemExit(
            "No API key found. Set TEXT_TEST_API_KEY / OPEN_ROUTER_API_KEY / OPENAI_API_KEY in .env.local, "
            "or pass --api-key."
        )

    system_prompt = PROMPT_FILE.read_text(encoding="utf-8")
    user_text = resolve_input_text(args)

    print(f"→ Calling {model} @ {base_url} ...", file=sys.stderr)
    result = call_llm(base_url, model, api_key, system_prompt, user_text, args.timeout)

    print(result)

    if not args.no_save:
        OUTPUT_DIR.mkdir(exist_ok=True)
        stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
        out_path = OUTPUT_DIR / f"{stamp}-gdpr-text-analysis.md"
        out_path.write_text(
            f"<!-- model={model} base_url={base_url} timestamp={stamp}Z -->\n\n"
            f"## Input\n\n```\n{user_text}\n```\n\n## LLM response\n\n{result}\n",
            encoding="utf-8",
        )
        print(f"\n→ Saved to {out_path.relative_to(REPO_ROOT)}", file=sys.stderr)


if __name__ == "__main__":
    main()
