#!/usr/bin/env python3
"""Réserves de noms des langues ajoutées (finnois, danois, tchèque, hongrois, serbe, hébreu,
persan, coréen, indonésien, ourdou) -> assets/data/names/<langue>.json, déclarées dans game_config."""
import json, os

DATA = os.path.join(os.path.dirname(__file__), "..", "..", "assets", "data")
P = {
    "fi": (["Antti", "Matti", "Juha", "Mikko", "Jari", "Timo", "Pekka", "Ville", "Sami", "Janne", "Petri", "Markku", "Heikki", "Lauri", "Olli", "Tuomas", "Eero", "Aleksi", "Kalle", "Risto"],
           ["Anna", "Maria", "Liisa", "Sanna", "Hanna", "Laura", "Johanna", "Kaisa", "Elina", "Riikka", "Tiina", "Minna", "Satu", "Paula", "Aino", "Emma", "Sari", "Päivi", "Katja", "Noora"],
           ["Korhonen", "Virtanen", "Mäkinen", "Nieminen", "Mäkelä", "Hämäläinen", "Laine", "Heikkinen", "Koskinen", "Järvinen", "Lehtonen", "Lehtinen", "Saarinen", "Salminen", "Heinonen", "Niemi", "Heikkilä", "Kinnunen", "Salonen", "Turunen", "Salo", "Laitinen", "Tuominen", "Rantanen", "Karjalainen", "Jokinen"]),
    "da": (["Lars", "Jens", "Peter", "Søren", "Niels", "Henrik", "Anders", "Morten", "Rasmus", "Mads", "Thomas", "Jesper", "Christian", "Martin", "Mikkel", "Kasper", "Jakob", "Frederik", "Mathias", "Jonas"],
           ["Anne", "Mette", "Kirsten", "Hanne", "Susanne", "Lene", "Camilla", "Louise", "Charlotte", "Sofie", "Ida", "Maria", "Pernille", "Line", "Julie", "Emma", "Karen", "Signe", "Trine", "Helle"],
           ["Nielsen", "Jensen", "Hansen", "Pedersen", "Andersen", "Christensen", "Larsen", "Sørensen", "Rasmussen", "Jørgensen", "Petersen", "Madsen", "Kristensen", "Olsen", "Thomsen", "Christiansen", "Poulsen", "Johansen", "Møller", "Mortensen", "Knudsen", "Jakobsen", "Mikkelsen", "Lund", "Holm", "Frederiksen"]),
    "cs": (["Jan", "Petr", "Jiří", "Josef", "Pavel", "Martin", "Tomáš", "Jaroslav", "Miroslav", "Zdeněk", "Václav", "Michal", "František", "Karel", "Lukáš", "Jakub", "David", "Ondřej", "Vojtěch", "Radek"],
           ["Jana", "Marie", "Eva", "Hana", "Anna", "Lenka", "Kateřina", "Lucie", "Věra", "Alena", "Petra", "Veronika", "Martina", "Jaroslava", "Tereza", "Michaela", "Zuzana", "Markéta", "Barbora", "Klára"],
           ["Novák", "Svoboda", "Novotný", "Dvořák", "Černý", "Procházka", "Kučera", "Veselý", "Horák", "Němec", "Marek", "Pospíšil", "Pokorný", "Hájek", "Král", "Jelínek", "Růžička", "Beneš", "Fiala", "Sedláček", "Doležal", "Zeman", "Kolář", "Navrátil", "Čermák", "Vaněk"]),
    "hu": (["László", "István", "József", "János", "Zoltán", "Sándor", "Gábor", "Ferenc", "Attila", "Péter", "Tamás", "Zsolt", "Tibor", "András", "Csaba", "Imre", "Balázs", "Gergely", "Dániel", "Ádám"],
           ["Mária", "Erzsébet", "Katalin", "Éva", "Ilona", "Anna", "Zsuzsanna", "Margit", "Judit", "Ágnes", "Andrea", "Erika", "Krisztina", "Eszter", "Szilvia", "Anita", "Orsolya", "Réka", "Dóra", "Nóra"],
           ["Nagy", "Kovács", "Tóth", "Szabó", "Horváth", "Varga", "Kiss", "Molnár", "Németh", "Farkas", "Balogh", "Papp", "Takács", "Juhász", "Lakatos", "Mészáros", "Oláh", "Simon", "Rácz", "Fekete", "Szilágyi", "Török", "Fehér", "Balázs", "Gál", "Kis"]),
    "sr": (["Aleksandar", "Nikola", "Marko", "Stefan", "Milan", "Nenad", "Dragan", "Zoran", "Goran", "Miloš", "Dejan", "Vladimir", "Ivan", "Bojan", "Dušan", "Lazar", "Luka", "Filip", "Uroš", "Vuk"],
           ["Milica", "Jelena", "Ana", "Marija", "Jovana", "Ivana", "Dragana", "Snežana", "Tamara", "Sanja", "Maja", "Katarina", "Teodora", "Nevena", "Mila", "Sara", "Biljana", "Gordana", "Vesna", "Ljiljana"],
           ["Jovanović", "Petrović", "Nikolić", "Marković", "Đorđević", "Stojanović", "Ilić", "Stanković", "Pavlović", "Milošević", "Popović", "Kostić", "Mitrović", "Živković", "Todorović", "Ristić", "Savić", "Lazić", "Đukić", "Marić", "Kovačević", "Vasić", "Simić", "Obradović", "Radović", "Tomić"]),
    "he": (["David", "Moshe", "Yosef", "Avraham", "Yaakov", "Daniel", "Itzhak", "Eitan", "Noam", "Ariel", "Uri", "Amir", "Yonatan", "Omer", "Gil", "Ron", "Avi", "Shlomo", "Elad", "Yair"],
           ["Sarah", "Rachel", "Miriam", "Tamar", "Noa", "Yael", "Michal", "Shira", "Maya", "Avital", "Orly", "Dana", "Hila", "Limor", "Efrat", "Ayelet", "Galit", "Liat", "Merav", "Roni"],
           ["Cohen", "Levi", "Mizrahi", "Peretz", "Biton", "Dahan", "Avraham", "Friedman", "Malka", "Azoulay", "Katz", "Yosef", "David", "Amar", "Ohana", "Hadad", "Gabay", "Ben-David", "Shapira", "Klein", "Ashkenazi", "Rosen", "Golan", "Barak", "Segal", "Weiss"]),
    "fa": (["Mohammad", "Ali", "Hossein", "Reza", "Mehdi", "Hassan", "Ahmad", "Amir", "Saeed", "Javad", "Majid", "Mostafa", "Hamid", "Kaveh", "Babak", "Dariush", "Farhad", "Kamran", "Masoud", "Navid"],
           ["Fatemeh", "Zahra", "Maryam", "Sara", "Narges", "Leila", "Shirin", "Mahsa", "Parisa", "Neda", "Roya", "Azadeh", "Mina", "Samira", "Elham", "Niloufar", "Shabnam", "Golnaz", "Ladan", "Yasmin"],
           ["Mohammadi", "Hosseini", "Ahmadi", "Rezaei", "Moradi", "Karimi", "Jafari", "Rahimi", "Hashemi", "Mousavi", "Ghasemi", "Sadeghi", "Kazemi", "Ebrahimi", "Rostami", "Shirazi", "Tehrani", "Akbari", "Salehi", "Nouri", "Bagheri", "Amini", "Farahani", "Esfahani", "Najafi", "Zamani"]),
    "ko": (["Min-jun", "Seo-jun", "Ji-ho", "Do-yun", "Joon-ho", "Hyun-woo", "Sung-min", "Jae-hyun", "Dong-hyun", "Young-ho", "Sang-woo", "Jin-woo", "Tae-yang", "Woo-jin", "Kyung-soo", "Chul-soo", "Seung-ho", "Ji-hoon", "Hyun-jun", "Byung-hun"],
           ["Seo-yeon", "Ji-woo", "Ha-eun", "Min-seo", "Soo-ah", "Ji-yeon", "Yoon-seo", "Eun-ji", "Hye-jin", "Mi-kyung", "Su-jin", "Young-hee", "Ji-hye", "Na-young", "Bo-ra", "Da-eun", "Hyun-jung", "So-yeon", "Ye-jin", "Jung-ah"],
           ["Kim", "Lee", "Park", "Choi", "Jung", "Kang", "Cho", "Yoon", "Jang", "Lim", "Han", "Oh", "Seo", "Shin", "Kwon", "Hwang", "Ahn", "Song", "Yoo", "Hong", "Jeon", "Ko", "Moon", "Yang", "Son", "Bae"]),
    "id": (["Budi", "Agus", "Joko", "Rudi", "Hendra", "Dedi", "Eko", "Bambang", "Andi", "Wahyu", "Arief", "Dimas", "Rizky", "Fajar", "Teguh", "Bayu", "Yusuf", "Hadi", "Irwan", "Gilang"],
           ["Siti", "Sri", "Dewi", "Rina", "Ani", "Wati", "Lestari", "Putri", "Ayu", "Indah", "Fitri", "Nur", "Ratna", "Yuni", "Dian", "Kartika", "Sari", "Mega", "Wulan", "Intan"],
           ["Santoso", "Wijaya", "Saputra", "Hidayat", "Setiawan", "Pratama", "Gunawan", "Kurniawan", "Susanto", "Hartono", "Nugroho", "Wibowo", "Halim", "Siregar", "Nasution", "Lubis", "Harahap", "Sitompul", "Purnomo", "Utomo", "Rahman", "Sihombing", "Tanjung", "Simanjuntak", "Hasibuan", "Daulay"]),
    "ur": (["Muhammad", "Ahmed", "Ali", "Hassan", "Usman", "Bilal", "Imran", "Faisal", "Kamran", "Asad", "Tariq", "Zubair", "Shahid", "Waqar", "Junaid", "Adnan", "Fahad", "Hamza", "Nadeem", "Saad"],
           ["Ayesha", "Fatima", "Zainab", "Maryam", "Sana", "Hina", "Sadia", "Amna", "Nida", "Rabia", "Saima", "Uzma", "Mehwish", "Kiran", "Bushra", "Farah", "Iqra", "Samina", "Nazia", "Huma"],
           ["Khan", "Ahmed", "Ali", "Malik", "Hussain", "Shah", "Butt", "Chaudhry", "Qureshi", "Sheikh", "Mirza", "Siddiqui", "Akhtar", "Iqbal", "Raza", "Abbasi", "Baig", "Rana", "Javed", "Aslam", "Bhatti", "Gill", "Awan", "Niazi", "Sharif", "Zaidi"]),
}
for lang, (m, f, last) in P.items():
    json.dump({"maleFirstNames": m, "femaleFirstNames": f, "lastNames": last}, open(os.path.join(DATA, "names", f"{lang}.json"), "w"), ensure_ascii=False, indent=1)
cfg_path = os.path.join(DATA, "config", "game_config.json")
cfg = json.load(open(cfg_path))
for lang in P: cfg["files"]["names"][lang] = f"names/{lang}.json"
json.dump(cfg, open(cfg_path, "w"), ensure_ascii=False, indent=2)
print(len(P), "réserves de noms")
