package at.nimmdas.app.data

object Suggestions {
    val CAR_BRANDS = listOf(
        "Abarth", "Alfa Romeo", "Alpine", "Aston Martin", "Audi", "Bentley", "BMW", 
        "Bugatti", "Cadillac", "Chevrolet", "Chrysler", "Citroen", "Cupra", "Dacia", 
        "Dodge", "DS Automobiles", "Ferrari", "Fiat", "Ford", "Honda", "Hyundai", 
        "Infiniti", "Jaguar", "Jeep", "Kia", "Lada", "Lamborghini", "Lancia", 
        "Land Rover", "Lexus", "Lotus", "Maserati", "Mazda", "McLaren", "Mercedes-Benz", 
        "MG", "MINI", "Mitsubishi", "Nissan", "Opel", "Peugeot", "Polestar", "Porsche", 
        "Renault", "Rolls-Royce", "Seat", "Skoda", "Smart", "SsangYong", "Subaru", 
        "Suzuki", "Tesla", "Toyota", "VW", "Volvo"
    )

    val CAR_MODELS = mapOf(
        "Abarth" to listOf("500", "595", "695", "124 Spider"),
        "Alfa Romeo" to listOf("Giulia", "Stelvio", "Tonale", "Giulietta", "MiTo", "4C"),
        "Alpine" to listOf("A110"),
        "Aston Martin" to listOf("DB11", "DBX", "Vantage", "DBS"),
        "Audi" to listOf("A1", "A3", "A4", "A5", "A6", "A7", "A8", "Q2", "Q3", "Q4 e-tron", "Q5", "Q7", "Q8", "e-tron", "e-tron GT", "TT", "R8"),
        "Bentley" to listOf("Bentayga", "Continental GT", "Flying Spur"),
        "BMW" to listOf("1er", "2er", "3er", "4er", "5er", "6er", "7er", "8er", "X1", "X2", "X3", "X4", "X5", "X6", "X7", "Z4", "i3", "i4", "iX", "iX3"),
        "Cadillac" to listOf("XT4", "XT5", "XT6", "Escalade"),
        "Chevrolet" to listOf("Camaro", "Corvette", "Silverado", "Tahoe"),
        "Chrysler" to listOf("300C", "Pacifica", "Voyager"),
        "Citroen" to listOf("C1", "C3", "C3 Aircross", "C4", "C4 Cactus", "C5 Aircross", "C5 X", "Berlingo", "Spacetourer"),
        "Cupra" to listOf("Born", "Formentor", "Leon", "Ateca"),
        "Dacia" to listOf("Spring", "Sandero", "Logan", "Jogger", "Duster"),
        "Dodge" to listOf("Challenger", "Charger", "Durango", "RAM"),
        "DS Automobiles" to listOf("DS 3", "DS 4", "DS 7", "DS 9"),
        "Ferrari" to listOf("296", "812", "F8", "Roma", "SF90", "Purosangue"),
        "Fiat" to listOf("500", "500X", "500L", "Panda", "Tipo", "Ducato"),
        "Ford" to listOf("Fiesta", "Focus", "Mondeo", "Mustang", "Mustang Mach-E", "Puma", "Kuga", "Ranger", "Tourneo", "Transit"),
        "Honda" to listOf("e", "Jazz", "Civic", "HR-V", "CR-V"),
        "Hyundai" to listOf("i10", "i20", "i30", "Kona", "Tucson", "Santa Fe", "IONIQ 5", "IONIQ 6", "Staria"),
        "Jaguar" to listOf("E-Pace", "F-Pace", "I-Pace", "XE", "XF", "F-Type"),
        "Jeep" to listOf("Renegade", "Compass", "Cherokee", "Grand Cherokee", "Wrangler", "Gladiator"),
        "Kia" to listOf("Picanto", "Rio", "Ceed", "Xceed", "Stonic", "Niro", "Sportage", "Sorento", "EV6"),
        "Lamborghini" to listOf("Huracan", "Aventador", "Urus"),
        "Lancia" to listOf("Ypsilon"),
        "Land Rover" to listOf("Defender", "Discovery", "Discovery Sport", "Range Rover", "Range Rover Sport", "Range Rover Velar", "Range Rover Evoque"),
        "Lexus" to listOf("CT", "ES", "IS", "LS", "NX", "RX", "UX"),
        "Lotus" to listOf("Emira", "Eletre"),
        "Maserati" to listOf("Ghibli", "Quattroporte", "Levante", "Grecale", "MC20"),
        "Mazda" to listOf("Mazda2", "Mazda3", "Mazda6", "CX-3", "CX-30", "CX-5", "CX-60", "MX-5"),
        "McLaren" to listOf("Artura", "720S", "GT"),
        "Mercedes-Benz" to listOf("A-Klasse", "B-Klasse", "C-Klasse", "E-Klasse", "G-Klasse", "S-Klasse", "CLA", "CLS", "GLA", "GLB", "GLC", "GLE", "GLS", "EQA", "EQB", "EQC", "EQE", "EQS", "V-Klasse"),
        "MG" to listOf("MG3", "MG4", "MG5", "ZS", "HS", "Marvel R"),
        "MINI" to listOf("One", "Cooper", "Clubman", "Countryman"),
        "Mitsubishi" to listOf("Space Star", "Colt", "ASX", "Eclipse Cross", "Outlander", "L200"),
        "Nissan" to listOf("Micra", "Leaf", "Juke", "Qashqai", "X-Trail", "Ariya", "GT-R"),
        "Opel" to listOf("Corsa", "Astra", "Insignia", "Mokka", "Crossland", "Grandland", "Zafira"),
        "Peugeot" to listOf("208", "308", "408", "508", "2008", "3008", "5008", "Rifter"),
        "Polestar" to listOf("Polestar 2", "Polestar 3"),
        "Porsche" to listOf("911", "718 Boxster", "718 Cayman", "Panamera", "Macan", "Cayenne", "Taycan"),
        "Renault" to listOf("Twingo", "Clio", "Megane", "KCaptur", "Arkana", "Austral", "Espace", "Zoe", "Kangoo"),
        "Rolls-Royce" to listOf("Ghost", "Phantom", "Cullinan", "Wraith", "Dawn"),
        "Seat" to listOf("Ibiza", "Leon", "Arona", "Ateca", "Tarraco"),
        "Skoda" to listOf("Fabia", "Scala", "Octavia", "Superb", "Kamiq", "Karoq", "Kodiaq", "Enyaq"),
        "Smart" to listOf("fortwo", "forfour", "#1"),
        "SsangYong" to listOf("Tivoli", "Korando", "Rexton", "Musso"),
        "Subaru" to listOf("Impreza", "XV", "Forester", "Outback", "BRZ", "Solterra"),
        "Suzuki" to listOf("Ignis", "Swift", "Vitara", "S-Cross", "Jimny", "Swace", "Across"),
        "Tesla" to listOf("Model 3", "Model Y", "Model S", "Model X"),
        "Toyota" to listOf("Aygo X", "Yaris", "Corolla", "Camry", "C-HR", "RAV4", "Highlander", "Land Cruiser", "Hilux", "Proace", "Supra", "GR86", "bZ4X"),
        "VW" to listOf("up!", "Polo", "Golf", "Passat", "Arteon", "T-Cross", "Taigo", "T-Roc", "Tiguan", "Touareg", "Touran", "Sharan", "Caddy", "Multivan", "ID.3", "ID.4", "ID.5", "ID.Buzz", "Amarok"),
        "Volvo" to listOf("XC40", "C40", "XC60", "XC90", "S60", "S90", "V60", "V90")
    )

    val AUSTRIAN_DISTRICTS = listOf(
        "Wien - 1. Innere Stadt", "Wien - 2. Leopoldstadt", "Wien - 3. Landstraße", "Wien - 4. Wieden", "Wien - 5. Margareten",
        "Wien - 6. Mariahilf", "Wien - 7. Neubau", "Wien - 8. Josefstadt", "Wien - 9. Alsergrund", "Wien - 10. Favoriten",
        "Wien - 11. Simmering", "Wien - 12. Meidling", "Wien - 13. Hietzing", "Wien - 14. Penzing", "Wien - 15. Rudolfsheim-Fünfhaus",
        "Wien - 16. Ottakring", "Wien - 17. Hernals", "Wien - 18. Währing", "Wien - 19. Döbling", "Wien - 20. Brigittenau",
        "Wien - 21. Floridsdorf", "Wien - 22. Donaustadt", "Wien - 23. Liesing",
        "Graz - I. Innere Stadt", "Graz - II. St. Leonhard", "Graz - III. Geidorf", "Graz - IV. Lend", "Graz - V. Gries",
        "Graz - VI. Jakomini", "Graz - VII. Liebenau", "Graz - VIII. St. Peter", "Graz - IX. Waltendorf", "Graz - X. Ries",
        "Graz - XI. Mariatrost", "Graz - XII. Andritz", "Graz - XIII. Gösting", "Graz - XIV. Eggenberg", "Graz - XV. Wetzelsdorf",
        "Graz - XVI. Straßgang", "Graz - XVII. Puntigam",
        "Linz - Innere Stadt", "Linz - Urfahr", "Linz - Pöstlingberg", "Linz - St. Magdalena", "Linz - Dornach-Auhof",
        "Linz - Kapuzinerberg", "Linz - Waldegg", "Linz - Bindermichl-Keferfeld", "Linz - Spallerhof", "Linz - Bulgariplatz",
        "Linz - Industriegebiet-Hafen", "Linz - Kleinmünchen-Auwiesen", "Linz - Ebelsberg", "Linz - Pichling",
        "Salzburg", "Innsbruck", "Klagenfurt", "Villach", "Wels", "St. Pölten", "Dornbirn", "Wiener Neustadt",
        "Steyr", "Feldkirch", "Bregenz", "Leonding", "Klosterneuburg", "Baden", "Wolfsberg", "Leoben", "Krems"
    )

    // ── Create-form suggestions, mirroring the website's lists ──
    val COMMON_COLORS = listOf(
        "Schwarz", "Weiß", "Grau", "Silber", "Blau", "Rot", "Grün", "Braun", "Beige", "Gelb", "Orange", "Gold",
    )

    val REG_DATES = listOf(
        "01/2026", "06/2025", "01/2025", "06/2024", "01/2024", "06/2023", "01/2023",
        "06/2022", "01/2022", "06/2021", "01/2021", "2020", "2019", "2018",
    )

    val TUEV_DATES = listOf("06/2026", "12/2026", "06/2027", "12/2027", "06/2028", "12/2028")

    val FLOOR_OPTIONS = listOf(
        "Erdgeschoss", "1. Stock", "2. Stock", "3. Stock", "4. Stock", "5. Stock",
        "Dachgeschoss", "Penthouse", "Keller / Souterrain",
    )

    val HEATING_TYPES = listOf(
        "Fernwärme", "Wärmepumpe", "Gasheizung", "Pelletsheizung", "Holzheizung",
        "Ölheizung", "Infrarotheizung", "Zentralheizung",
    )

    val REALESTATE_AVAIL = listOf(
        "Ab sofort", "Ab nächstem Monat", "Nach Vereinbarung",
        "Ab dem ersten des Folgemonats", "Erstbezug nach Fertigstellung",
    )

    val SALARIES = listOf(
        "€ 1.500 - € 2.000 / Monat", "€ 2.000 - € 2.500 / Monat", "€ 2.500 - € 3.000 / Monat",
        "€ 3.000 - € 3.500 / Monat", "€ 3.500 - € 4.500 / Monat", "€ 4.500 - € 6.000 / Monat",
        "€ 6.000+ / Monat", "Auf Stundenbasis (z.B. € 15 - € 25 / Std.)",
        "Verhandlungsbasis (Vollzeit/Teilzeit KV)",
    )

    val START_DATES = listOf(
        "Ab sofort", "Zum nächstmöglichen Zeitpunkt", "Ab nächstem Monat",
        "Nach Absprache (Kündigungsfrist)", "1. des Folgemonats",
    )

    val SERVICE_AREAS = listOf(
        "Ganz Österreich", "Wien & Umgebung", "Linz & Umgebung", "Graz & Umgebung",
        "Salzburg & Umgebung", "Klagenfurt & Umgebung", "Innsbruck & Umgebung",
        "Bregenz & Umgebung", "St. Pölten & Umgebung", "Eisenstadt & Umgebung", "Heimarbeit / Remote",
    )

    val AVAILABILITIES = listOf(
        "Ab sofort", "Flexible Termine nach Vereinbarung", "Nur Wochentags (Mo-Fr)",
        "Nur am Wochenende (Sa-So)", "Abendstunden (ab 17:00)", "Vormittags (08:00 - 12:00)",
    )

    val EXPERIENCES = listOf(
        "Keine Erfahrung erforderlich (Quereinsteiger)", "1 - 2 Jahre Berufserfahrung",
        "3 - 5 Jahre Berufserfahrung", "Über 5 Jahre Berufserfahrung",
        "Meister / Staatlich geprüft", "Akademischer Abschluss / Fachexperte",
    )

    val EVENT_TIMES = listOf(
        "08:00 - 14:00 Uhr", "09:00 - 17:00 Uhr", "10:00 - 18:00 Uhr",
        "18:00 - 22:00 Uhr", "Ganztägig (nach Absprache)",
    )

    val EVENT_ADDRESSES = listOf(
        "Hauptplatz, 4020 Linz", "Mariahilfer Straße, 1060 Wien", "Herrengasse, 8010 Graz",
        "Getreidegasse, 5020 Salzburg", "Messeplatz 1, 1020 Wien", "Kärntner Straße, 1010 Wien",
        "Rathausplatz, 1010 Wien", "Landstraße, 4020 Linz", "Jakominiplatz, 8010 Graz",
    )

    val WARRANTIES = listOf(
        "2 Jahre", "5 Jahre", "10 Jahre", "15 Jahre", "20 Jahre", "25 Jahre", "30 Jahre", "Auf Anfrage",
    )
}
