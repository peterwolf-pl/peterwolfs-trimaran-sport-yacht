# Peterwolf's Trimaran Sport Yacht

Mod **Fabric 26.3 / Java 25**, zależny od **Wind & Sails**. Osobny identyfikator: `peterwolfs_trimaran_sport_yacht`. Katamaran nie jest wymagany.

## Model

Regatowy trimaran inspirowany dostarczonymi zdjęciami, z własnym malowaniem PW 03:

- Kadłub centralny **16 bloków**, pływaki **15 bloków**, szerokość **11,25 bloku**.
- Maszt **28 bloków** nad punktem odniesienia kadłuba. Dla porównania katamaran ma około 8 × 5 bloków.
- Trzy smukłe, stopniowane kadłuby; łukowe ramiona karbonowe; dwie siatki z przezroczystymi oczkami.
- Niska żółta kabina, ciemne szyby, otwarty kokpit, kabestany, koło sterowe, knagi i platforma wejściowa.
- Grot square-top z 48 pasów i ciągłą grafiką konturową. Fok obraca się wokół pochylonego sztagu.
- Foile, miecz i trzy niezależnie obracane stery T. Śruba napędu pomocniczego animowana tylko przy włączonym silniku.
- Atlas **2048 × 2048**. Model encji jest Java `LayerDefinition`, nie JSON.

Foile są stałymi elementami modelu, **nie symulacją lotu hydrodynamicznego**. Kabina jest zamkniętą osłoną regatową: bez wnętrza mieszkalnego i funkcji spania. Kolizja bloków obejmuje kadłuby; podwodne płetwy są wizualne.

## Instalacja i użycie

Do folderu `mods` gry 26.3 dodaj:

1. Fabric API dla 26.3.
2. `pw_wind_n_sails` dla 26.3, co najmniej 1.0.3.
3. `build/libs/peterwolfs-trimaran-sport-yacht-1.0.0+26.3.jar` — nie plik `-sources.jar`.

Przedmiot jest dostępny w osobnej zakładce kreatywnej i zakładce Wind & Sails.

```mcfunction
/give @s peterwolfs_trimaran_sport_yacht:trimaran_sport_yacht
```

PPM na wodę woduje trimaran. Wybierz rozległy akwen, minimum dwa bloki wody w miejscu wodowania. PPM na koło przejmuje ster.

| Klawisz | Funkcja |
|---|---|
| A / D | Ster |
| W / S | Szoty pod żaglami; gaz / wsteczny pod silnikiem |
| X | Postaw / zwiń żagle |
| Z | Włącz / wyłącz silnik pomocniczy |
| Spacja | Przejdź na nawietrzną / wróć za koło |
| Shift | Zejdź ze steru do kokpitu |

Kotwicę z Wind & Sails najpierw wyrzuć, następnie kliknij wybraną knagę. Smycz służy do cumowania do płotu. Zasięg knag: 14 bloków.

## Budowanie

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
./gradlew compileJava compileClientJava test build
```

Domyślna zależność kompilacji: `../pw_wind_n_sails/build/libs/pw_wind_n_sails-1.0.3+26.3.jar`.
Inną lokalizację podaj przez `-Pwind_sails_jar=/pełna/ścieżka/do/mod.jar`.

## Edycja modelu i eksport

- `src/main/java/com/piotrek/trimaranyacht/geometry/TrimaranGeometry.java` — geometria, hierarchia, pivoty, UV.
- `src/main/java/com/piotrek/trimaranyacht/geometry/TrimaranLayout.java` — powierzchnie pokładu i SAT trzech kadłubów.
- `src/client/java/com/piotrek/trimaranyacht/client/TrimaranSportYachtModel.java` — warstwy i animacje.

Po zmianie geometrii uruchom (Pillow jest wymagany):

```sh
python3 generate_assets.py
python3 tools/render_preview.py
./gradlew compileJava compileClientJava test build
```

Eksport w `build/model/`:
- `trimaran.obj` + `trimaran.mtl` + `trimaran_sport_yacht.png` — ten sam model do importu np. w Blenderze; jednostki w metrach, oś Y w górę.
- `geometry-preview.png` — pogląd geometrii offline, z płaskimi kolorami, bez kompletnego malowania.
- `geometry.json` — dane pośrednie eksportera, **nie model encji Minecraft**.

## Weryfikacja

14 testów: proporcje, UV, styki sekcji, maski wody, knagi, żagle, pivoty, pokład, SAT, bake warstw i reset animacji.

Nie uruchamiano klienta Minecraft ani Client GameTest. Podgląd offline nie potwierdza wyglądu, braku migotania ani zachowania fizyki w grze. Weryfikacja w kliencie pozostaje osobnym krokiem na żądanie.
