package at.nimmdas.app.ui.screens.search

data class CatFilter(val key: String, val label: String, val type: String, val options: List<String>? = null, val unit: String? = null, val placeholder: String? = null)

val CATEGORY_FILTERS: Map<String, List<CatFilter>> = mapOf(
    "Autos" to listOf(
        CatFilter("brand", "Marke", "text", placeholder = "z.B. BMW"),
        CatFilter("model", "Modell", "text", placeholder = "z.B. 320d"),
        CatFilter("yearMin", "Baujahr ab", "text", placeholder = "2015"),
        CatFilter("yearMax", "Baujahr bis", "text", placeholder = "2026"),
        CatFilter("mileageMax", "Km bis", "text", unit = "km", placeholder = "150000"),
        CatFilter("fuelType", "Kraftstoff", "select", listOf("Benzin","Diesel","Elektro","Hybrid","Gas (LPG/CNG)")),
        CatFilter("transmission", "Getriebe", "select", listOf("Manuell","Automatik")),
        CatFilter("powerMin", "PS ab", "text", unit = "PS", placeholder = "100"),
        CatFilter("powerMax", "PS bis", "text", unit = "PS", placeholder = "300"),
        CatFilter("color", "Farbe", "text", placeholder = "z.B. Schwarz"),
        CatFilter("accidentFree", "Unfallfrei", "boolean"),
    ),
    "Immobilien" to listOf(
        CatFilter("propertyType", "Art", "select", listOf("rent","buy")),
        CatFilter("roomsMin", "Zimmer ab", "text", placeholder = "2"),
        CatFilter("roomsMax", "Zimmer bis", "text", placeholder = "5"),
        CatFilter("sqmMin", "Fläche ab", "text", unit = "m²", placeholder = "50"),
        CatFilter("sqmMax", "Fläche bis", "text", unit = "m²", placeholder = "200"),
        CatFilter("furnished", "Möbliert", "boolean"),
        CatFilter("balcony", "Balkon/Terrasse", "boolean"),
        CatFilter("elevator", "Aufzug", "boolean"),
        CatFilter("parking", "Parkplatz", "boolean"),
        CatFilter("garden", "Garten", "boolean"),
        CatFilter("cellar", "Keller", "boolean"),
    ),
    "Jobs" to listOf(
        CatFilter("jobType", "Beschäftigung", "select", listOf("Vollzeit","Teilzeit","Minijob","Praktikum","Freelancer","Ausbildung")),
        CatFilter("jobBranche", "Branche", "select", listOf("IT & Software","Handwerk","Gastro & Tourismus","Handel & Verkauf","Büro & Verwaltung","Gesundheit & Pflege","Technik & Produktion","Transport & Logistik","Bildung & Soziales","Marketing & Medien","Sonstiges")),
        CatFilter("salaryMin", "Gehalt ab", "text", unit = "€/Monat", placeholder = "1500"),
        CatFilter("salaryMax", "Gehalt bis", "text", unit = "€/Monat", placeholder = "5000"),
        CatFilter("experienceLevel", "Erfahrung", "select", listOf("Keine Erfahrung","Berufseinsteiger","1-3 Jahre","3-5 Jahre","5+ Jahre")),
        CatFilter("homeOffice", "Home Office", "boolean"),
    ),
    "Dienstleistungen" to listOf(
        CatFilter("priceUnit", "Preisart", "select", listOf("Stunde","Pauschal","Projekt","Monat")),
        CatFilter("serviceArea", "Einsatzgebiet", "text", placeholder = "z.B. Wien"),
        CatFilter("experience", "Erfahrung", "select", listOf("1-3 Jahre","3-5 Jahre","5-10 Jahre","10+ Jahre")),
        CatFilter("availability", "Verfügbarkeit", "select", listOf("Sofort","Diese Woche","Nächste Woche","Flexibel")),
    ),
    "Elektronik" to listOf(
        CatFilter("brand", "Marke", "text", placeholder = "z.B. Apple"),
        CatFilter("model", "Modell", "text", placeholder = "z.B. iPhone 15"),
        CatFilter("ram", "RAM", "text", placeholder = "8 GB"),
        CatFilter("storage", "Speicher", "text", placeholder = "256 GB"),
        CatFilter("color", "Farbe", "text", placeholder = "z.B. Schwarz"),
    ),
    "Mode" to listOf(
        CatFilter("brand", "Marke", "text", placeholder = "z.B. Nike"),
        CatFilter("gender", "Geschlecht", "select", listOf("Herren","Damen","Unisex","Kinder")),
        CatFilter("clothingSize", "Größe", "select", listOf("XXS","XS","S","M","L","XL","XXL","3XL")),
        CatFilter("shoeSize", "Schuhgröße", "select", listOf("36","37","38","39","40","41","42","43","44","45","46")),
        CatFilter("material", "Material", "select", listOf("Baumwolle","Leder","Synthetik","Wolle","Seide","Leinen","Denim","Polyester")),
        CatFilter("color", "Farbe", "text", placeholder = "z.B. Schwarz"),
    ),
    "Möbel" to listOf(
        CatFilter("brand", "Marke", "text", placeholder = "z.B. IKEA"),
        CatFilter("material", "Material", "select", listOf("Holz","Metall","Glas","Kunststoff","Stoff","Leder","Rattan","Marmor")),
        CatFilter("color", "Farbe", "text", placeholder = "z.B. Weiß"),
        CatFilter("widthMax", "Breite bis", "text", unit = "cm", placeholder = "200"),
        CatFilter("heightMax", "Höhe bis", "text", unit = "cm", placeholder = "100"),
    ),
    "Sport" to listOf(
        CatFilter("sportType", "Sportart", "select", listOf("Fahrrad","E-Bike","Fitness","Wintersport","Ballsport","Wassersport","Klettern","Camping","Reitsport","Laufen","Kampfsport")),
        CatFilter("brand", "Marke", "text", placeholder = "z.B. Specialized"),
        CatFilter("gender", "Geschlecht", "select", listOf("Herren","Damen","Unisex","Kinder")),
        CatFilter("frameSize", "Rahmengröße", "text", placeholder = "z.B. 56 cm"),
    ),
    "Garten" to listOf(
        CatFilter("gartenType", "Typ", "select", listOf("Pflanzen","Werkzeug & Geräte","Gartenmöbel","Griller & BBQ","Pool & Teich","Dekoration","Bewässerung","Zaun & Sichtschutz")),
        CatFilter("material", "Material", "select", listOf("Holz","Metall","Kunststoff","Stein","Rattan")),
    ),
    "Haustiere" to listOf(
        CatFilter("animalType", "Tierart", "select", listOf("Hunde","Katzen","Kleintiere","Vögel","Aquaristik","Pferde","Reptilien")),
        CatFilter("breed", "Rasse", "text", placeholder = "z.B. Labrador"),
        CatFilter("animalAge", "Alter", "select", listOf("Welpe/Kitten","Jung (< 1 Jahr)","1-3 Jahre","3-5 Jahre","5+ Jahre")),
        CatFilter("animalGender", "Geschlecht", "select", listOf("Männlich","Weiblich")),
        CatFilter("vaccinated", "Geimpft", "boolean"),
        CatFilter("neutered", "Kastriert", "boolean"),
    ),
    "Baby & Kind" to listOf(
        CatFilter("ageGroup", "Alter", "select", listOf("0-6 Monate","6-12 Monate","1-2 Jahre","2-4 Jahre","4-6 Jahre","6-10 Jahre","10+ Jahre")),
        CatFilter("gender", "Geschlecht", "select", listOf("Mädchen","Junge","Unisex")),
        CatFilter("clothingSize", "Kleidergröße", "select", listOf("50","56","62","68","74","80","86","92","98","104","110","116","122","128","134","140","146","152","158","164")),
    ),
    "Musik" to listOf(
        CatFilter("instrumentType", "Instrumentenart", "select", listOf("Gitarren","Keyboards & Pianos","Schlagzeug","Blasinstrumente","Streichinstrumente","DJ-Equipment","Mikrofone","Verstärker","Vinyl & CDs","Zubehör")),
        CatFilter("brand", "Marke", "text", placeholder = "z.B. Fender"),
    ),
    "Sammeln" to listOf(
        CatFilter("collectType", "Sammelgebiet", "select", listOf("Münzen","Briefmarken","Antiquitäten","Trading Cards","Modellautos","Comics","Figuren","Militaria","Kunst","Sonstiges")),
        CatFilter("rarity", "Seltenheit", "select", listOf("Häufig","Selten","Sehr selten","Rarität")),
        CatFilter("era", "Epoche", "select", listOf("Antik (vor 1900)","1900-1950","1950-1980","1980-2000","Modern (ab 2000)")),
    ),
    "Flohmarkt" to listOf(
        CatFilter("material", "Material", "select", listOf("Holz","Metall","Glas","Keramik","Textil","Kunststoff","Papier")),
    ),
)

val HIDE_CONDITION_CATEGORIES = setOf("Jobs", "Immobilien", "Dienstleistungen")
