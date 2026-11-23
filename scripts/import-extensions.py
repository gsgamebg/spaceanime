"""Read an Anix catalog as data; never execute its payloads."""
import hashlib
import json
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
source = Path(sys.argv[1])
raw = source.read_bytes()
catalog = json.loads(raw)
plugins = catalog["plugins"]
assert isinstance(plugins, list) and len(plugins) <= 500
ids = [p["id"] for p in plugins]
assert len(set(ids)) == len(ids)
native = {"anitube", "animesdigital"}

def quote(value):
    assert isinstance(value, str) and len(value) <= 2000
    return json.dumps(value, ensure_ascii=False).replace("$", "\\$")

lines = ["package app.anim.sources", "", "// Generated from the user-provided Anix catalog. Payloads are not evaluated.", "val extensionCatalog = listOf("]
for p in plugins:
    status = "NATIVE" if p["id"] in native else "EXTERNAL_MANIFEST" if p.get("manifestURI") else "REQUIRES_RUNTIME"
    fields = [p["id"], p["name"], p.get("lang", ""), p.get("version", ""), p.get("author", ""), p.get("website", ""), p.get("manifestURI", "")]
    lines.append("    ExtensionInfo(" + ", ".join(map(quote, fields)) + f", ExtensionSupport.{status}),")
lines.append(")\n")
target = root / "composeApp/src/commonMain/kotlin/app/anim/sources/ExtensionCatalog.kt"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text("\n".join(lines), encoding="utf-8")
metadata = {"name": catalog.get("name"), "generated_at": catalog.get("generated_at"), "sha256": hashlib.sha256(raw).hexdigest(), "entries": [{k: p[k] for k in ("id", "name", "version", "author", "lang", "website", "manifestURI") if k in p} | {"payload_sha256": hashlib.sha256(p.get("payload", "").encode()).hexdigest() if p.get("payload") else None, "payload_chars": len(p.get("payload", ""))} for p in plugins]}
target = root / "references/anix-catalog-metadata.json"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Imported {len(plugins)} metadata entries; 2 native adapters, {len(plugins)-2} pending compatibility. No JavaScript executed.")
