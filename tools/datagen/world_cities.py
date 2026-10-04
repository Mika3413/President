"""Villes des pays étrangers simulés -> assets/data/geo/world_cities.json.

Capitales et grandes villes (population de l'agglomération en millions, coordonnées
approximatives). Rang 1 : capitale ; 2 : grande ville ; 3 : ville régionale ou port stratégique.
La France a ses propres villes, plus détaillées, dans countries/FRA/territory.json.
"""
import json, os, unicodedata

C = {
 "DEU": [("Berlin", 52.52, 13.40, 3.7), ("Hambourg", 53.55, 9.99, 1.9), ("Munich", 48.14, 11.58, 1.5), ("Cologne", 50.94, 6.96, 1.1),
         ("Francfort", 50.11, 8.68, 0.8), ("Stuttgart", 48.78, 9.18, 0.6), ("Düsseldorf", 51.23, 6.78, 0.6), ("Leipzig", 51.34, 12.37, 0.6),
         ("Dresde", 51.05, 13.74, 0.56), ("Hanovre", 52.37, 9.73, 0.54), ("Brême", 53.08, 8.80, 0.57), ("Nuremberg", 49.45, 11.08, 0.52), ("Kiel", 54.32, 10.14, 0.25)],
 "ESP": [("Madrid", 40.42, -3.70, 6.7), ("Barcelone", 41.39, 2.17, 5.6), ("Valence", 39.47, -0.38, 1.6), ("Séville", 37.39, -5.98, 1.5),
         ("Bilbao", 43.26, -2.93, 1.0), ("Malaga", 36.72, -4.42, 1.0), ("Saragosse", 41.65, -0.89, 0.7), ("Palma", 39.57, 2.65, 0.4), ("Cadix", 36.53, -6.29, 0.12)],
 "ITA": [("Rome", 41.90, 12.50, 4.3), ("Milan", 45.46, 9.19, 3.2), ("Naples", 40.85, 14.27, 3.0), ("Turin", 45.07, 7.69, 1.7),
         ("Palerme", 38.12, 13.36, 1.2), ("Gênes", 44.41, 8.93, 0.8), ("Bologne", 44.49, 11.34, 0.6), ("Florence", 43.77, 11.26, 0.7),
         ("Venise", 45.44, 12.32, 0.6), ("Bari", 41.12, 16.87, 0.6), ("Tarente", 40.47, 17.24, 0.2)],
 "GBR": [("Londres", 51.51, -0.13, 9.5), ("Birmingham", 52.49, -1.89, 2.9), ("Manchester", 53.48, -2.24, 2.8), ("Glasgow", 55.86, -4.25, 1.7),
         ("Leeds", 53.80, -1.55, 1.9), ("Liverpool", 53.41, -2.98, 0.9), ("Édimbourg", 55.95, -3.19, 0.55), ("Bristol", 51.45, -2.59, 0.7),
         ("Belfast", 54.60, -5.93, 0.6), ("Cardiff", 51.48, -3.18, 0.5), ("Portsmouth", 50.82, -1.09, 0.25), ("Plymouth", 50.38, -4.14, 0.26)],
 "BEL": [("Bruxelles", 50.85, 4.35, 2.1), ("Anvers", 51.22, 4.40, 1.1), ("Gand", 51.05, 3.72, 0.6), ("Liège", 50.63, 5.57, 0.75), ("Charleroi", 50.41, 4.44, 0.5), ("Bruges", 51.21, 3.22, 0.12)],
 "NLD": [("Amsterdam", 52.37, 4.90, 2.5), ("Rotterdam", 51.92, 4.48, 1.8), ("La Haye", 52.08, 4.30, 1.1), ("Utrecht", 52.09, 5.12, 0.7), ("Eindhoven", 51.44, 5.48, 0.75), ("Groningue", 53.22, 6.57, 0.35)],
 "CHE": [("Berne", 46.95, 7.45, 0.43), ("Zurich", 47.38, 8.54, 1.4), ("Genève", 46.20, 6.14, 0.6), ("Bâle", 47.56, 7.59, 0.55), ("Lausanne", 46.52, 6.63, 0.42), ("Lugano", 46.00, 8.95, 0.15)],
 "PRT": [("Lisbonne", 38.72, -9.14, 2.9), ("Porto", 41.15, -8.61, 1.7), ("Braga", 41.55, -8.42, 0.2), ("Coimbra", 40.21, -8.43, 0.15), ("Faro", 37.02, -7.93, 0.12)],
 "AUT": [("Vienne", 48.21, 16.37, 2.0), ("Graz", 47.07, 15.44, 0.33), ("Linz", 48.31, 14.29, 0.21), ("Salzbourg", 47.81, 13.06, 0.16), ("Innsbruck", 47.27, 11.40, 0.13)],
 "POL": [("Varsovie", 52.23, 21.01, 3.1), ("Cracovie", 50.06, 19.94, 1.4), ("Łódź", 51.76, 19.46, 1.1), ("Wrocław", 51.11, 17.03, 1.0),
         ("Poznań", 52.41, 16.93, 1.0), ("Gdańsk", 54.35, 18.65, 1.1), ("Szczecin", 53.43, 14.55, 0.7), ("Lublin", 51.25, 22.57, 0.65), ("Rzeszów", 50.04, 22.00, 0.2)],
 "SWE": [("Stockholm", 59.33, 18.07, 2.4), ("Göteborg", 57.71, 11.97, 1.0), ("Malmö", 55.60, 13.00, 0.7), ("Uppsala", 59.86, 17.64, 0.23), ("Luleå", 65.58, 22.15, 0.08), ("Gotland (Visby)", 57.64, 18.30, 0.06)],
 "NOR": [("Oslo", 59.91, 10.75, 1.6), ("Bergen", 60.39, 5.32, 0.42), ("Trondheim", 63.43, 10.40, 0.2), ("Stavanger", 58.97, 5.73, 0.24), ("Tromsø", 69.65, 18.96, 0.08), ("Bodø", 67.28, 14.40, 0.05)],
 "GRC": [("Athènes", 37.98, 23.73, 3.6), ("Thessalonique", 40.64, 22.94, 1.1), ("Patras", 38.25, 21.73, 0.2), ("Héraklion", 35.34, 25.13, 0.2), ("Rhodes", 36.43, 28.22, 0.12), ("Alexandroúpoli", 40.85, 25.87, 0.07)],
 "ROU": [("Bucarest", 44.43, 26.10, 2.2), ("Cluj-Napoca", 46.77, 23.60, 0.4), ("Timișoara", 45.75, 21.23, 0.32), ("Iași", 47.16, 27.59, 0.36), ("Constanța", 44.18, 28.63, 0.3), ("Brașov", 45.65, 25.61, 0.25)],
 "UKR": [("Kyiv", 50.45, 30.52, 3.0), ("Kharkiv", 49.99, 36.23, 1.4), ("Odessa", 46.48, 30.73, 1.0), ("Dnipro", 48.46, 35.05, 1.0), ("Lviv", 49.84, 24.03, 0.72),
         ("Zaporijjia", 47.84, 35.14, 0.7), ("Kryvyï Rih", 47.91, 33.39, 0.6), ("Mykolaïv", 46.97, 31.99, 0.47), ("Tchernihiv", 51.49, 31.29, 0.28), ("Soumy", 50.91, 34.80, 0.26)],
 "BLR": [("Minsk", 53.90, 27.56, 2.0), ("Homiel", 52.44, 30.98, 0.5), ("Moguilev", 53.90, 30.33, 0.36), ("Vitebsk", 55.19, 30.20, 0.36), ("Hrodna", 53.68, 23.83, 0.36), ("Brest", 52.10, 23.69, 0.34)],
 "RUS": [("Moscou", 55.76, 37.62, 12.6), ("Saint-Pétersbourg", 59.94, 30.31, 5.4), ("Kaliningrad", 54.71, 20.51, 0.49), ("Mourmansk", 68.97, 33.08, 0.27),
         ("Smolensk", 54.78, 32.05, 0.32), ("Briansk", 53.24, 34.36, 0.4), ("Koursk", 51.73, 36.19, 0.44), ("Belgorod", 50.60, 36.59, 0.34),
         ("Voronej", 51.66, 39.20, 1.05), ("Rostov-sur-le-Don", 47.24, 39.71, 1.14), ("Volgograd", 48.71, 44.51, 1.0), ("Sébastopol", 44.62, 33.52, 0.5),
         ("Novorossiisk", 44.72, 37.77, 0.27), ("Kazan", 55.79, 49.12, 1.3), ("Nijni Novgorod", 56.33, 44.00, 1.2), ("Samara", 53.20, 50.15, 1.15),
         ("Iekaterinbourg", 56.84, 60.61, 1.5), ("Novossibirsk", 55.03, 82.92, 1.6), ("Vladivostok", 43.12, 131.89, 0.6)],
 "TUR": [("Ankara", 39.93, 32.86, 5.7), ("Istanbul", 41.01, 28.98, 15.6), ("Izmir", 38.42, 27.14, 4.4), ("Bursa", 40.19, 29.06, 3.1), ("Antalya", 36.90, 30.70, 2.6),
         ("Adana", 37.00, 35.32, 2.3), ("Gaziantep", 37.07, 37.38, 2.1), ("Konya", 37.87, 32.48, 2.3), ("Trabzon", 41.00, 39.72, 0.8), ("Diyarbakır", 37.91, 40.24, 1.8)],
 "DZA": [("Alger", 36.75, 3.06, 3.9), ("Oran", 35.70, -0.63, 1.6), ("Constantine", 36.37, 6.61, 0.95), ("Annaba", 36.90, 7.77, 0.6), ("Sétif", 36.19, 5.41, 0.3), ("Tamanrasset", 22.79, 5.52, 0.1)],
 "MAR": [("Rabat", 34.02, -6.84, 1.9), ("Casablanca", 33.57, -7.59, 3.8), ("Marrakech", 31.63, -7.99, 1.0), ("Fès", 34.03, -5.00, 1.2), ("Tanger", 35.76, -5.83, 1.3), ("Agadir", 30.43, -9.60, 0.9)],
 "TUN": [("Tunis", 36.81, 10.18, 2.7), ("Sfax", 34.74, 10.76, 0.95), ("Sousse", 35.83, 10.64, 0.7), ("Bizerte", 37.27, 9.87, 0.15), ("Gabès", 33.88, 10.10, 0.15)],
 "EGY": [("Le Caire", 30.04, 31.24, 21.7), ("Alexandrie", 31.20, 29.92, 5.4), ("Gizeh", 30.01, 31.21, 4.4), ("Port-Saïd", 31.27, 32.30, 0.75), ("Suez", 29.97, 32.53, 0.75), ("Assouan", 24.09, 32.90, 0.3), ("Louxor", 25.69, 32.64, 0.5)],
 "SAU": [("Riyad", 24.71, 46.68, 7.7), ("Djeddah", 21.49, 39.19, 4.7), ("La Mecque", 21.39, 39.86, 2.0), ("Médine", 24.47, 39.61, 1.5), ("Dammam", 26.43, 50.10, 1.3), ("Tabouk", 28.38, 36.57, 0.6)],
 "USA": [("Washington", 38.91, -77.04, 6.3), ("New York", 40.71, -74.01, 19.5), ("Los Angeles", 34.05, -118.24, 13.0), ("Chicago", 41.88, -87.63, 9.4),
         ("Houston", 29.76, -95.37, 7.3), ("Dallas", 32.78, -96.80, 7.9), ("Philadelphie", 39.95, -75.17, 6.2), ("Miami", 25.76, -80.19, 6.1),
         ("Atlanta", 33.75, -84.39, 6.2), ("Boston", 42.36, -71.06, 4.9), ("San Francisco", 37.77, -122.42, 4.6), ("Seattle", 47.61, -122.33, 4.0),
         ("San Diego", 32.72, -117.16, 3.3), ("Norfolk", 36.85, -76.29, 1.8), ("Honolulu", 21.31, -157.86, 1.0), ("Anchorage", 61.22, -149.90, 0.4)],
 "CAN": [("Ottawa", 45.42, -75.70, 1.5), ("Toronto", 43.65, -79.38, 6.4), ("Montréal", 45.50, -73.57, 4.3), ("Vancouver", 49.28, -123.12, 2.6),
         ("Calgary", 51.05, -114.07, 1.5), ("Edmonton", 53.55, -113.49, 1.4), ("Québec", 46.81, -71.21, 0.84), ("Halifax", 44.65, -63.58, 0.45)],
 "BRA": [("Brasília", -15.79, -47.88, 4.8), ("São Paulo", -23.55, -46.63, 22.4), ("Rio de Janeiro", -22.91, -43.17, 13.6), ("Belo Horizonte", -19.92, -43.94, 6.1),
         ("Salvador", -12.97, -38.50, 3.9), ("Fortaleza", -3.73, -38.53, 4.1), ("Recife", -8.05, -34.88, 4.1), ("Manaus", -3.12, -60.02, 2.3), ("Porto Alegre", -30.03, -51.23, 4.3)],
 "CHN": [("Pékin", 39.90, 116.41, 21.9), ("Shanghai", 31.23, 121.47, 29.2), ("Canton", 23.13, 113.26, 19.0), ("Shenzhen", 22.54, 114.06, 17.6),
         ("Chongqing", 29.56, 106.55, 17.0), ("Tianjin", 39.34, 117.36, 14.0), ("Chengdu", 30.66, 104.07, 16.0), ("Wuhan", 30.59, 114.31, 8.9),
         ("Xi'an", 34.34, 108.94, 8.9), ("Hangzhou", 30.27, 120.16, 9.0), ("Nankin", 32.06, 118.80, 9.0), ("Hong Kong", 22.32, 114.17, 7.5),
         ("Qingdao", 36.07, 120.38, 6.2), ("Dalian", 38.91, 121.60, 4.5), ("Xiamen", 24.48, 118.09, 4.0), ("Harbin", 45.80, 126.53, 5.0), ("Ürümqi", 43.83, 87.62, 4.0)],
 "JPN": [("Tokyo", 35.68, 139.69, 37.2), ("Osaka", 34.69, 135.50, 19.0), ("Nagoya", 35.18, 136.91, 9.5), ("Fukuoka", 33.59, 130.40, 5.5),
         ("Sapporo", 43.06, 141.35, 2.7), ("Sendai", 38.27, 140.87, 2.3), ("Hiroshima", 34.39, 132.46, 1.4), ("Yokosuka", 35.28, 139.67, 0.4), ("Naha (Okinawa)", 26.21, 127.68, 0.8)],
 "IND": [("New Delhi", 28.61, 77.21, 32.9), ("Bombay", 19.08, 72.88, 21.3), ("Calcutta", 22.57, 88.36, 15.3), ("Bangalore", 12.97, 77.59, 13.6),
         ("Madras", 13.08, 80.27, 11.8), ("Hyderabad", 17.39, 78.49, 10.8), ("Ahmedabad", 23.02, 72.57, 8.6), ("Pune", 18.52, 73.86, 7.2),
         ("Jaipur", 26.91, 75.79, 4.1), ("Lucknow", 26.85, 80.95, 3.9), ("Visakhapatnam", 17.69, 83.22, 2.3), ("Srinagar", 34.08, 74.80, 1.5)],
}

CAPITALS = {"DEU": "Berlin", "ESP": "Madrid", "ITA": "Rome", "GBR": "Londres", "BEL": "Bruxelles", "NLD": "Amsterdam", "CHE": "Berne", "PRT": "Lisbonne",
            "AUT": "Vienne", "POL": "Varsovie", "SWE": "Stockholm", "NOR": "Oslo", "GRC": "Athènes", "ROU": "Bucarest", "UKR": "Kyiv", "BLR": "Minsk",
            "RUS": "Moscou", "TUR": "Ankara", "DZA": "Alger", "MAR": "Rabat", "TUN": "Tunis", "EGY": "Le Caire", "SAU": "Riyad", "USA": "Washington",
            "CAN": "Ottawa", "BRA": "Brasília", "CHN": "Pékin", "JPN": "Tokyo", "IND": "New Delhi"}


def slug(country, name):
    s = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode().lower()
    return country.lower() + "-" + "".join(ch if ch.isalnum() else "-" for ch in s).strip("-")


cities = []
for country, lst in C.items():
    for name, lat, lon, pop in lst:
        capital = CAPITALS[country] == name
        rank = 1 if capital else (2 if pop >= 1.0 else 3)
        cities.append({"id": slug(country, name), "name": name, "country": country, "lat": lat, "lon": lon,
                       "populationMillions": pop, "capital": capital, "rank": rank})

path = os.path.join(os.path.dirname(__file__), "..", "..", "assets", "data", "geo", "world_cities.json")
with open(path, "w", encoding="utf-8") as f:
    json.dump({"_doc": "Villes des pays étrangers simulés. Généré par tools/datagen/world_cities.py.", "cities": cities}, f, ensure_ascii=False, indent=1)
    f.write("\n")
print(len(cities), "villes écrites")
