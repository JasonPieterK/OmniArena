# Upload steps: OmniArena Modrinth page

Do these once. You need the project owner account on Modrinth. About 10 minutes. This folder holds only the page and
the pictures. **The jars are not here.** Build them with GitHub Actions or locally (see "Step 5").

## Quick way (recommended)

`DESCRIPTION.md` loads its pictures from `raw.githubusercontent.com/JasonPieterK/OmniArena/main/docs/modrinth/images/...`.
This works only when the repository is public and the files are pushed to `main`. If you rename or move the files,
the links break. (Alternative: the Gallery way below.)

1. Create the project on Modrinth (type: **Plugin**). Name: **OmniArena**. Set the slug, for example `omniarena`.
2. **Settings -> General:** upload `images/00-icon.png` (512 x 512) as the project icon. Paste the text from
   `SUMMARY.txt` into **Summary**. Set categories, environment (server) and the licence (see `SUMMARY.txt`).
3. **Settings -> Description:** delete the old text, paste the full content of `DESCRIPTION.md`, click **Preview**
   and check that every picture shows. Save.
4. **Settings -> Links:** add source, issues and wiki (`SUMMARY.txt`).
5. **Gallery:** upload the pictures from the table below, with their titles.
6. **Versions:** upload the jars (Step 5).
7. Submit the project for review.

## Icon

| File | Use |
|---|---|
| `images/00-icon.png` | 512 x 512 project icon (upload this one) |
| `images/00-icon-256.png`, `images/00-icon-64.png` | smaller copies, for the README and other places |
| `images/00-icon.svg` | the source drawing |

## Gallery list (title and caption)

Mark `01-hero.png` as **Featured** so it is the gallery's main picture.

| Order | File | Title | Caption |
|---|---|---|---|
| 1 | `01-hero.png` | OmniArena | PvP arenas, zero hassle. Ranked, tournaments, four server types. |
| 2 | `02-recap.png` | Product recap | The twelve main features at a glance. |
| 3 | `03-setup.png` | Setup in 3 steps | Create the arena, set the spawns, finish. About five minutes. |
| 4 | `04-match-flow.png` | How a match runs | Join, start, play, end and automatic rebuild. |
| 5 | `05-modes.png` | 16 game modes | From Deathmatch to Spleef, each with its own goal and rules. |
| 6 | `06-ranked.png` | Ranked and tournaments | ELO matchmaking with parties, and single-elimination brackets. |
| 7 | `07-menus.png` | Menu design | Gray glass, green and red concrete, one footer. Every button has a command twin. |
| 8 | `08-integrations.png` | Integrations | Optional hooks, each isolated and switchable. |
| 9 | `09-matrix.png` | Platforms and versions | 24 jars: Paper, Purpur, Spigot, Bukkit times six Minecraft versions. |

`10-divider.png` is a thin strip used inside the description only. Do not put it in the Gallery.

## Other way: pictures from the Modrinth Gallery

Use this if you do not want the page to load pictures from GitHub.

1. Upload all pictures to the Gallery (Settings -> Gallery -> Add an image).
2. Right-click each picture in the list, copy the image address (`https://cdn.modrinth.com/data/<id>/images/<hash>.png`).
3. In a copy of `DESCRIPTION.md`, replace each `raw.githubusercontent.com/...` link with the Modrinth link of the
   same file. `10-divider.png` appears three times: replace all three.
4. Search the copy for `raw.githubusercontent.com`. It must find nothing. Paste the copy into Modrinth.

## Step 5: Versions and jars

The 24 jars come from your own build: GitHub Actions or local (`scripts/build-all.sh`, with `JAVA25_HOME` and
`JAVA21_HOME` set). Never build them from this documentation change. Name each file exactly:

`OmniArena-<platform>-<mcversion>.jar`, for example `OmniArena-paper-26.2.jar`.

| Platform in the name | Modrinth loader |
|---|---|
| `paper` | Paper |
| `purpur` | Purpur |
| `spigot` | Spigot |
| `bukkit` | Bukkit |

Minecraft version in the name: `26.3`, `26.2`, `26.1`, `1.21.11`, `1.21.10`, `1.21.9`.

Create one Modrinth version per jar (or group the jars of one Minecraft version in one release). For each:

- Version number: `1.0.0`. Version title: `OmniArena 1.0.0 for <platform> <mcversion>`.
- Loader and game version as in the file name. Channel: **Release**, or **Beta** while it is untested on a live server.
- Changelog: "First public release. See the honest status on the project page."
- Java: 25 for the 26.x jars, 21 for the 1.21.x jars. Say so in the version notes.

Check before upload: the six `purpur` jars were not verified when the project was last built (repository unreachable).
Build them again before you publish them, or leave them out until they compile.

## Checks before you publish

- The page says the plugin has not been run on a live server. Change this only after you test the jars on a copy of your server.
- The repository has no `LICENSE` file. Pick the licence first (see `SUMMARY.txt`).
- Open the public page in a private window and look at it in light and dark theme. The pictures have a black frame and read well in both.
- The pictures contain no Minecraft textures or logos.

## If you change a picture later

Edit `graphics-src/gen.py` (or `icon.svg` / `common.css`), then:

```
python3 graphics-src/gen.py
NODE_PATH=<your global node_modules> node graphics-src/render.js
pip install markdown
python3 graphics-src/build-preview.py
```

Node with Playwright and Chromium must be installed. Upload the new picture, then check the description preview.
