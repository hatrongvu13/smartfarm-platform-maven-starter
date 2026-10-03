#!/usr/bin/env python3
"""Read-only graph slicer for the SmartFarm cleanup audit.

Extracts a module-scoped view of graphify-out/graph.json so an auditor can
reason about one module's nodes/edges without loading the whole 10.8 MB graph.

Usage:
  python3 docs/cleanup/tools/graphslice.py --module services/smartfarm-order
  python3 docs/cleanup/tools/graphslice.py --module services/smartfarm-order --summary
  python3 docs/cleanup/tools/graphslice.py --symbol OrderSagaOrchestrator
  python3 docs/cleanup/tools/graphslice.py --orphans services/smartfarm-order   # nodes w/ no inbound CALLS/REFERENCES
  python3 docs/cleanup/tools/graphslice.py --crossgrpc                          # cross-module calls/references
  python3 docs/cleanup/tools/graphslice.py --callers <node-id-substring>

All output is JSON lines or a short summary. Never mutates the graph.
"""
import argparse, json, sys
from collections import Counter, defaultdict

GRAPH = "graphify-out/graph.json"


def load():
    with open(GRAPH) as f:
        return json.load(f)


def mod_of(sf):
    if not sf:
        return None
    prefixes = [
        "services/smartfarm-order", "services/smartfarm-identity",
        "services/smartfarm-inventory", "services/smartfarm-livestock",
        "services/smartfarm-health", "services/smartfarm-finance",
        "services/smartfarm-reporting", "apps/smartfarm-gateway",
        "platform/smartfarm-readiness", "platform/smartfarm-farm-simulator",
        "libs/smartfarm-common-kernel", "libs/smartfarm-security",
        "libs/smartfarm-messaging", "libs/smartfarm-proto",
    ]
    for p in prefixes:
        if sf.startswith(p):
            return p
    return "other"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--module", help="source_file prefix, e.g. services/smartfarm-order")
    ap.add_argument("--summary", action="store_true")
    ap.add_argument("--symbol", help="substring match on node id/label")
    ap.add_argument("--orphans", help="module prefix: list nodes with no inbound calls/references")
    ap.add_argument("--crossgrpc", action="store_true", help="list cross-module call/reference edges")
    ap.add_argument("--callers", help="node id substring: who calls/references it")
    ap.add_argument("--limit", type=int, default=200)
    a = ap.parse_args()
    g = load()
    nodes = g["nodes"]
    edges = g["links"]
    by_id = {n["id"]: n for n in nodes}

    if a.module:
        sel = [n for n in nodes if (n.get("source_file") or "").startswith(a.module)]
        ids = {n["id"] for n in sel}
        if a.summary:
            print(f"MODULE {a.module}: {len(sel)} nodes")
            ft = Counter(n.get("file_type") for n in sel)
            print("file_type:", dict(ft))
            # entrypoint annotations
            ep = Counter()
            for n in sel:
                lbl = n.get("label", "")
                for tag in ("RestController", "PostMapping", "GetMapping", "GrpcService",
                            "MessageMapping", "Scheduled", "EventListener", "QueryMapping",
                            "MutationMapping", "SubscriptionMapping", "Entity", "Repository",
                            "ConfigurationProperties", "Service", "Component", "Configuration"):
                    if tag in lbl:
                        ep[tag] += 1
            print("annotations:", dict(ep.most_common()))
            inbound = sum(1 for e in edges if e["target"] in ids and e["source"] not in ids)
            outbound = sum(1 for e in edges if e["source"] in ids and e["target"] not in ids)
            print(f"edges: inbound(from other modules)={inbound} outbound(to other modules)={outbound}")
        else:
            for n in sel[: a.limit]:
                print(json.dumps({"id": n["id"], "label": n.get("label"),
                                  "file": n.get("source_file"), "loc": n.get("source_location")}))
        return

    if a.symbol:
        hits = [n for n in nodes if a.symbol.lower() in (n["id"] + str(n.get("label", ""))).lower()]
        for n in hits[: a.limit]:
            print(json.dumps({"id": n["id"], "label": n.get("label"), "file": n.get("source_file")}))
        print(f"# {len(hits)} node(s)")
        return

    if a.callers:
        tgt = [n["id"] for n in nodes if a.callers.lower() in n["id"].lower()]
        tgtset = set(tgt)
        callers = [e for e in edges if e["target"] in tgtset and e["relation"] in ("calls", "references", "method", "imports")]
        for e in callers[: a.limit]:
            src = by_id.get(e["source"], {})
            print(json.dumps({"rel": e["relation"], "from": e["source"],
                              "from_file": src.get("source_file"), "to": e["target"]}))
        print(f"# {len(callers)} inbound edge(s) to {len(tgt)} node(s) matching '{a.callers}'")
        return

    if a.orphans:
        sel = [n for n in nodes if (n.get("source_file") or "").startswith(a.orphans)]
        ids = {n["id"] for n in sel}
        inbound = defaultdict(int)
        for e in edges:
            if e["relation"] in ("calls", "references", "method", "implements") and e["target"] in ids:
                inbound[e["target"]] += 1
        orphans = [n for n in sel if inbound.get(n["id"], 0) == 0
                   and n.get("file_type") == "python" or True]  # keep all, report count
        orphans = [n for n in sel if inbound.get(n["id"], 0) == 0]
        for n in orphans[: a.limit]:
            print(json.dumps({"id": n["id"], "label": n.get("label"), "file": n.get("source_file")}))
        print(f"# {len(orphans)} node(s) with ZERO inbound calls/references/method/implements in {a.orphans}")
        return

    if a.crossgrpc:
        xs = []
        for e in edges:
            if e["relation"] not in ("calls", "references", "implements"):
                continue
            s = by_id.get(e["source"], {})
            t = by_id.get(e["target"], {})
            ms, mt = mod_of(s.get("source_file")), mod_of(t.get("source_file"))
            if ms and mt and ms != mt and ms != "other" and mt != "other":
                xs.append((ms, mt, e["relation"]))
        c = Counter((a_, b_, r_) for a_, b_, r_ in xs)
        for (ms, mt, r), cnt in c.most_common(a.limit):
            print(f"{ms} --{r}--> {mt}: {cnt}")
        return

    ap.print_help()


if __name__ == "__main__":
    main()
