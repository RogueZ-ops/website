# AstralixMC kliens – HUD mod 2.15.14

Alap: launcher 1.2.44 → `resources/hud-mods/hud-bundle.zip` (HUD mod 2.15.13). Ez a bundle mind a 12 verziócsoportot (g1182 … g262) tartalmazza.

## Változások

1. **Az AstralixMC felirat eltűnik, amíg a TAB-ot nyomod.**
   Csak játékban számít, és csak akkor, ha nincs nyitva képernyő (chat, inventory stb.). A HUD-szerkesztőben a felirat továbbra is látszik. Ha a feliratot kikapcsolod, ez sem csinál semmit.
   A gomb fixen a TAB. Ha valaki átállítja a „Játékoslista” billentyűt, ez nem követi.

2. **Áttűnés a főmenük között.**
   - Az *Eredeti Minecraft menü* gombra a saját menü kb. 0,3 mp alatt elsötétül. Ezután az eredeti menü a Minecraft saját indulós animációjával jelenik meg (`TitleScreen(true)`): előbb a panoráma úszik be, aztán a logó és a gombok, összesen kb. 2 mp alatt. Az elsötétülés közben a gombok nem kattinthatók.
   - A *« AstralixMC menü* gombbal visszalépve a saját menü kb. 0,45 mp alatt tűnik elő a sötétből.

3. A verzió 2.15.13-ról 2.15.14-re nőtt, ezért belépéskor feljön a „Frissült a kliens: v2.15.14” értesítés. A beállítások nem állnak vissza alaphelyzetbe.

## Beépítés

- **Új launcher-kiadáshoz:** a launcher projektedben cseréld le a `hud-mods/hud-bundle.zip` fájlt erre a `hud-bundle.zip`-re, emeld a verziót (pl. 1.2.45), buildelj, és töltsd fel a szokásos módon (`latest.yml` + exe + blockmap).
- **Gyors teszthez a saját gépeden:** zárd be a launchert, írd felül ezt a fájlt: `C:\Program Files\AstralixMC Launcher\resources\hud-mods\hud-bundle.zip` (vagy ahova telepítetted). Indításkor a launcher magától kicseréli a modot a példányokban.

## Hogyan készült (`forras/`)

A mod forrása nem volt meg, ezért az eredeti lefordított osztályokat módosítottam:

- `forras/Transitions.java`: új osztály (`hu.astralixmc.hud.ui.Transitions`). Benne van a teljes új logika: TAB-figyelés, elsötétítő overlay, időzítés.
- `forras/patcher/Patch.java`: ASM-mel minimális bekötéseket tesz a meglévő osztályokba:
  - `BrandModule.layout()` elején: ha `Transitions.hideBrand(c)` igaz, `return false`
  - `TitleUi`: az „Eredeti Minecraft menü” gomb `Hud::openVanillaMenu` helyett `Transitions::leave`-et hív, a `draw()` végén pedig `Transitions.overlay(this)` fut
  - `Hud.tick()` elején `Transitions.tick()`, a `Hud.leaveVanillaMenu()` elején `Transitions.returning()` fut
  - `ExtraImpl.openVanillaTitle()` először `TitleScreen(true)`-t próbál, ha az nem megy, a paraméter nélküli konstruktort
  - minden `2.15.13` szövegkonstans `2.15.14` lesz
- `forras/patcher/mat.py` és `bundle.py`: a bundle kicsomagolása verziócsoportokra, majd visszacsomagolása ugyanabban a formátumban (`manifest.json` + `b/<sha1>`).

Ha megvan a mod eredeti forrása, érdemesebb ezt a pár változtatást ott átvezetni, a `Transitions.java`-t pedig egy az egyben átvinni.

## Ellenőrzés

- Mind a 12 build bytecode-ja átment az ASM-verifikáción, és a JVM is gond nélkül betöltötte.
- TAB-logika teszt: TAB nélkül látszik; TAB-bal rejtve; szerkesztőben, nyitott képernyőn vagy menüben látszik.
- Overlay-teszt: a fade-ki 0→255 alfa ~0,32 mp alatt, a fade-be 255→0 ~0,45 mp alatt.
- A launcher saját `hudInstaller.js`-ével 1.21.8, 1.20.1 és 26.1 verzióra telepítve a jar-ok rendben települnek, és benne van az új osztály.
- Játékban, Minecraft alatt nem teszteltem, ezt érdemes egyszer kipróbálni.
