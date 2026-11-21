# OmniArena logo: brief, concepts, critique

Process follows the logo-design skill (discovery, concepts, critique, refine, deliver). Palette is fixed by the owner:
red `#D6352B`, blue `#1E4FA3`, yellow `#F2C230`, black `#111111`, paper `#F4EFE6` (matches the page graphics).

## Discovery brief

- **What:** OmniArena, a PvP arena plugin for Minecraft servers (Paper, Purpur, Spigot, Bukkit). Arenas, 16 game modes,
  ranked ELO queue, tournaments, betting, automatic rebuild. Promise: "everything in one", set up in five minutes.
- **Audience:** server owners and admins first (they install it), then players who see its menus and signs.
- **Adjectives:** bold, clear, playful-but-competitive, Bauhaus, dependable, versus.
- **Where it must work:** Modrinth project icon (square, shown small and rounded), README header, hero image,
  dashboard, 16 px favicon, one colour, on dark.
- **Category clichés to avoid:** crossed swords, shields, crowns, pixel swords, flaming skulls, a literal Minecraft cube.
- **Word map:** Omni = all, every direction, complete. Arena = a ring or disc seen from above, a place of contest.
  Versus = two sides, an X, red against blue. Ranked/tournament = ordering, brackets.
  Intersection: a round arena that holds *everything*, divided by a "versus" cross.

## Concepts (all built on a 256 grid, see `concepts/*.svg` and `concepts-sheet.png`)

| | Concept | Type | Idea |
|---|---|---|---|
| A | Quartered | abstract | A disc split by an X into four colour fields: one arena, all modes; red against blue. |
| B | Face-off | abstract/pictorial | Two spawn triangles aim at each other inside a black disc. |
| C | Arena A | letterform | The letter A as a peak inside the arena disc. |
| D | Trinity | geometric | Bauhaus square, circle, triangle: everything in one. |
| E | X marks | abstract | Crossed blades abstracted to an X inside a ring. |

## Critique (testing checklist: 16/32/64 px, one colour, silhouette, distinctiveness)

| | 16 px | One colour | Silhouette | Distinct | Verdict |
|---|---|---|---|---|---|
| A | Four colour fields still separate, X gap survives (gap is 1.75 px at 16) | Works: black fields with paper X | A disc cut by an X: simple, round | Good; no sword or shield, uses all four colours | **Pick** |
| B | Reads as a dark dot; the triangles are lost | Fails: becomes a black blob | Plain disc | Medium (looks like a media/rewind control) | Drop |
| C | Reads as "A in a circle" | Works | Plain disc | Weak: generic monogram badge, says nothing about PvP | Drop |
| D | Three loose shapes turn to mush | Works but loses overlap meaning | Not a single shape | Weak: a generic Bauhaus cliché, no arena | Drop |
| E | The thin X and ring clog | Works | Ring | Medium: close to a "close" or "no" icon | Drop |

Familiarity check: the X-in-circle idea can look like a "cancel" button when drawn thin; A avoids it by using
thick colour fields and a wide cut, not strokes. Rotation: turned 90 degrees the mark reads the same (X cut is symmetric),
no unintended letters. Mirror test: red and blue swap sides, still balanced.

## Chosen: A, Quartered

One sentence: *an arena disc divided by a versus cross into four fields, one for every kind of fight.*

- Left red and right blue are the two teams, yellow on top is the spotlight/ring, black at the bottom grounds it.
- Disc r=120 on a 256 grid (centre 128,128, 8 padding). The X gap is 28 wide (half-width 14) on exact 45 degree lines.
  Each field is a single path (two straight edges and one arc), no strokes, no masks, no text, 4 flat colours.
- Refinements: the gap is widened to 34 and the disc to r=124 in the small-size cut (`omniarena-logo-small.svg`, used for
  16 and 32 px). The icon tile (`omniarena-icon.svg`) puts the mark (r=96) on paper inside the black Bauhaus frame used by
  all the page graphics. The white (reversed) version is the same geometry, because the gaps are real gaps.
- Wordmark: custom constructed capitals (cap height 120, stem 28, no font involved): O is a ring, M, N, A, R, E from bars,
  diagonals and one arc, spacing set by eye per pair. The letters share the mark's flat, heavy, geometric logic.
- Risks: the black field vanishes on a black background in the colour version (use the white version or put the mark on
  paper); the wordmark is wide (1448 x 256 lockup), so use the mark alone below about 200 px width.

## Files

| File | Use |
|---|---|
| `omniarena-logo.svg` | colour mark (master) |
| `omniarena-logo-black.svg`, `omniarena-logo-white.svg` | one-colour versions |
| `omniarena-logo-small.svg` | small-size cut for 16 and 32 px |
| `omniarena-lockup.svg` (+ `-black`, `-white`) | mark + wordmark, horizontal |
| `omniarena-icon.svg` | square tile: Modrinth icon source (copied to `../images/00-icon.svg`) |
| `png/` | exports 512, 256, 128, 64, 32, 16 for the mark and the icon, lockup 1200 wide |
| `concepts/*.svg`, `concepts-sheet.png`, `final-sheet.png` | exploration and presentation sheets |
| `concepts.py`, `concept-sheet.py`, `build-logo.py`, `final-sheet.py`, `render-svg.js` | generators (build-logo.py writes everything above) |

Not done: trademark or reverse-image search (do one before relying on the mark), CMYK/Pantone proofing, a PDF/EPS master.
