"""Agenda du président (France) -> assets/data/countries/FRA/agenda.json.

Jours de déplacement disponibles par semaine glissante, et temps pris par chaque activité.
"""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")


def c(days, abroad=False, place=""):
    d = {"days": days}
    if abroad: d["abroad"] = True
    if place: d["place"] = place
    return d


ACTIONS = {
    "eu_tour": c(3, True, "Berlin, Rome, Madrid, Varsovie"),
    "washington_visit": c(3, True, "Washington"),
    "beijing_visit": c(3, True, "Pékin"),
    "africa_summit": c(2, True, "Afrique"),
    "peace_conference": c(2),
    "tour_of_france": c(4),
    "great_debate": c(3),
    "business_summit": c(1),
    "press_conference": c(0.5),
    "tv_address": c(0.5),
    "unity_address": c(0.5),
    "youth_media": c(0.5),
    "joint_exercise": c(1),
    "citizens_convention": c(1),
    "refugee_reception": c(1),
}
EVENT_OPTIONS = {
    "x_visit": c(1),
    "x_rescuers": c(0),
    "visit": c(1),
    "reception": c(0.5),
    "x_dinner": c(0.5),
    "x_tv": c(0.5),
    "x_address": c(0.5),
    "x_press": c(0.5),
    "x_mayors": c(0.5),
    "x_defense_council": c(0.5),
}
SUMMITS = {e: c(2, True, "Bruxelles") for e in ["eu_budget", "eu_migration", "eu_defense", "eu_sanctions_war", "eu_common_debt"]}
SUMMITS.update({"g7_tax": c(2, True, "sommet du G7"), "g7_africa": c(2, True, "sommet du G7"),
                "nato_spending": c(2, True, "sommet de l'OTAN"), "nato_war_support": c(2, True, "sommet de l'OTAN"),
                "un_resolution": c(2, True, "New York"), "un_nuclear": c(2, True, "New York"), "cop_climate": c(2, True, "COP")})

actions = {a["id"] for a in json.load(open(os.path.join(ROOT, "countries", "FRA", "national_actions.json")))["actions"]}
assert set(ACTIONS) <= actions, set(ACTIONS) - actions
events = set()
for f in os.listdir(os.path.join(ROOT, "events")):
    events |= {e["id"] for e in json.load(open(os.path.join(ROOT, "events", f))).get("events", [])}
assert set(SUMMITS) <= events, set(SUMMITS) - events

out = {"_doc": "Agenda du président. Généré par tools/datagen/agenda_fr.py.", "weeklyDays": 5.0,
       "actions": ACTIONS, "eventOptions": {k: v for k, v in EVENT_OPTIONS.items() if v["days"] > 0}, "summits": SUMMITS,
       "foreignTalkDays": 0.25, "localTalkDays": 0.25, "localVisitDays": 1.0}
json.dump(out, open(os.path.join(ROOT, "countries", "FRA", "agenda.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"{len(ACTIONS)} décisions, {len(out['eventOptions'])} options, {len(SUMMITS)} sommets")
