"""Dernière étape de génération des dialogues (à lancer après tous les autres générateurs) :
 - complète la section « request » de chaque courrier d'événement avec des formulations tirées
   des options réellement proposées au joueur ;
 - écrit les fichiers de dialogue en JSON compact (ils sont volumineux).
Idempotent : peut être relancé sans dupliquer les variantes."""
import glob, json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dialogue_fr_lib import option_requests

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")
options_by_template = {}
for f in glob.glob(os.path.join(ROOT, "events", "*.json")):
    for e in json.load(open(f))["events"]:
        m = e.get("message")
        if m and len(m.get("options", [])) >= 2:
            options_by_template.setdefault(m["template"], m["options"])

added = 0
for f in glob.glob(os.path.join(ROOT, "dialogue", "fr", "*.json")):
    data = json.load(open(f))
    if "templates" in data:
        for t in data["templates"]:
            opts = options_by_template.get(t["id"])
            if not opts: continue
            for s in t["sections"]:
                if s["id"] != "request": continue
                known = {v["text"] for v in s["variants"]}
                for v in option_requests(opts):
                    if v["text"] not in known:
                        s["variants"].append(v); added += 1
    json.dump(data, open(f, "w"), ensure_ascii=False, separators=(",", ":"))
print("dialogues finalisés :", added, "formulations ajoutées")
