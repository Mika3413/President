"""Noms des dirigeants et des figures politiques, inspirés des personnalités réelles mais
volontairement différents (« Varkozi » plutôt que « Sarkozy ») : on les reconnaît sans les nommer.
Met à jour leader.figure de chaque pays et families[].figure des élections françaises.
"""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")


def f(first, last, female=False, born=None):
    d = {"firstName": first, "lastName": last}
    if female: d["female"] = True
    if born: d["birthYear"] = born
    return d


LEADERS = {
    "DEU": f("Friedrich", "Mertz", born=1955),
    "ESP": f("Pedro", "Sanchiz", born=1972),
    "ITA": f("Giorgia", "Meleni", True, 1977),
    "GBR": f("Keir", "Starmor", born=1962),
    "BEL": f("Bart", "De Wevre", born=1970),
    "NLD": f("Rob", "Jettan", born=1987),
    "CHE": f("Guy", "Parmelan", born=1959),
    "PRT": f("Luís", "Montenegri", born=1973),
    "AUT": f("Christian", "Stockler", born=1960),
    "POL": f("Donald", "Tosk", born=1957),
    "SWE": f("Ulf", "Kristerson", born=1963),
    "NOR": f("Jonas", "Gahr Støle", born=1960),
    "GRC": f("Kyriakos", "Mitsotakos", born=1968),
    "ROU": f("Ilie", "Bolojean", born=1969),
    "UKR": f("Yulia", "Svyrydenka", True, 1985),
    "RUS": f("Vladimir", "Poutane", born=1952),
    "BLR": f("Alexandre", "Loukachenka", born=1954),
    "TUR": f("Recep Tayyip", "Erdoğal", born=1954),
    "DZA": f("Abdelmadjid", "Tebboumi", born=1945),
    "MAR": f("Aziz", "Akhanouche", born=1961),
    "TUN": f("Kaïs", "Saïad", born=1958),
    "EGY": f("Abdel Fattah", "al-Sassi", born=1954),
    "SAU": f("Mohammed", "ben Salmine", born=1985),
    "USA": f("Donald", "Tromp", born=1946),
    "CAN": f("Mark", "Carnay", born=1965),
    "CHN": f("Xi", "Jinpang", born=1953),
    "JPN": f("Sanae", "Takaishi", True, 1961),
    "IND": f("Narendra", "Mody", born=1950),
    "BRA": f("Luiz Inácio", "Lulla", born=1945),
    "FIN": f("Petteri", "Orpa", born=1969),
    "DNK": f("Mette", "Frederiksson", True, 1977),
    "IRL": f("Micheál", "Martyn", born=1960),
    "CZE": f("Andrej", "Babich", born=1954),
    "HUN": f("Viktor", "Orbanyi", born=1963),
    "SRB": f("Aleksandar", "Vucik", born=1970),
    "ISR": f("Benjamin", "Netanyaho", born=1949),
    "IRN": f("Masoud", "Pezeshkiani", born=1954),
    "ARE": f("Mohammed", "ben Zayid", born=1961),
    "LBY": f("Mohamed", "al-Menfa", born=1976),
    "KOR": f("Lee", "Jae-myun", born=1963),
    "PRK": f("Kim", "Jong-oun", born=1984),
    "AUS": f("Anthony", "Albanesi", born=1963),
    "MEX": f("Claudia", "Sheinbaun", True, 1962),
    "ARG": f("Javier", "Mileï", born=1970),
    "ZAF": f("Cyril", "Ramaphosu", born=1952),
    "NGA": f("Bola", "Tinoubou", born=1952),
    "IDN": f("Prabowo", "Subianta", born=1951),
    "PAK": f("Shehbaz", "Sharef", born=1951),
}

FAMILIES = {
    "radical_left": f("Jean-Luc", "Mélenchin", born=1951),
    "left": f("Raphaël", "Glucksmond", born=1979),
    "greens": f("Marine", "Tondelin", True, 1986),
    "centre": f("Gabriel", "Attar", born=1989),
    "right": f("Bruno", "Retaillot", born=1960),
    "nationalist": f("Jordan", "Bardelli", born=1995),
}

for code, fig in LEADERS.items():
    p = os.path.join(ROOT, "countries", code, "country.json")
    d = json.load(open(p))
    d["leader"]["figure"] = fig
    json.dump(d, open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    open(p, "a").write("\n")
p = os.path.join(ROOT, "countries", "FRA", "elections.json")
d = json.load(open(p))
for fam in d["families"]:
    if fam["id"] in FAMILIES:
        fam["figure"] = FAMILIES[fam["id"]]
json.dump(d, open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
missing = [c for c in json.load(open(os.path.join(ROOT, "world_snapshots", "WORLD_SNAPSHOT_2026_10.json")))["countries"]
           if c.split("/")[1] not in LEADERS and c.split("/")[1] != "FRA"]
assert not missing, missing
print(f"{len(LEADERS)} dirigeants, {len(FAMILIES)} figures")
